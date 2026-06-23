# OpenPhone Assistant — Jetpack Compose Migration Plan

This document is self-contained. A new contributor or new AI chat session
should be able to read only this file and understand:

1. What product this UI is part of.
2. Why we want to migrate to Compose.
3. What constraints make this harder than a normal Android app.
4. The exact step-by-step plan.
5. What success looks like.

---

## 1. Product Background — What is OpenPhone?

OpenPhone is an **Android-based operating system** where an AI agent is a
**first-class system capability**, not an app. The canonical spec lives in
[`SPEC.md`](../SPEC.md), the active plan in [`docs/PLAN.md`](PLAN.md), and the
implementation evidence ledger in
[`docs/IMPLEMENTATION_STATUS.md`](IMPLEMENTATION_STATUS.md).

Key facts:

- OpenPhone is a custom **Android/LineageOS ROM**, not a normal Android app.
- Upstream is LineageOS `lineage-23.2` / Android 16 / `bp4a`.
- The first verified device is the **Google Pixel 9a (`tegu`, SKU `GTF7P`)**.
- The OS ships a privileged system app called **`OpenPhoneAssistant`** that is
  the user-facing assistant surface.
- The assistant talks to a privileged framework service
  (`OpenPhoneAgentManagerService`) registered in `system_server`, which
  mediates screen context, action execution, policy/consent, and audit.
- Models (OpenAI vision/realtime, broker, local heuristic) are pluggable
  adapters inside the assistant package.

The assistant is the **only thing the user actually sees**. It must feel like
a clean consumer AI assistant — not a developer tool — while still keeping
all the privileged-system capabilities (task lifecycle, voice, model
adapters, OTA preview client, audit/trace export, accessibility-driven UI
tree, framework Binder calls) working.

The agent loop is what gives OpenPhone product value. The assistant UI is
what makes that value usable. Right now the UI is the bottleneck on
"feels like a real product."

---

## 2. Current State of the Assistant App

Source location:

```
overlay/packages/apps/OpenPhoneAssistant/
  Android.bp
  AndroidManifest.xml
  res/
    drawable/                — only ic_openphone_tile.xml
    drawable-nodpi/          — openphone_sky.jpg, openphone_sky_blur.jpg (consumer wallpaper)
    values/                  — colors.xml, strings.xml, styles.xml (Theme.Material.NoActionBar)
    xml/                     — accessibility service config
  src/org/openphone/assistant/
    MainActivity.java         — ~2400 lines, the entire UI
    GlassPanel.java           — custom View that draws blurred wallpaper slice
    OpenPhoneAssistantService.java
    OpenPhoneAccessibilityService.java
    OpenPhoneBootReceiver.java
    OpenPhoneNotificationController.java
    OpenPhoneQuickSettingsTileService.java
    OpenPhoneTriggerReceiver.java
    PointerOverlayController.java
    IOpenPhoneAssistant.aidl
    agent/                    — TaskRegistry, AgentOrchestrator, FrameworkToolExecutor, ScreenUnderstanding, ActionExecution, TrajectoryRecorder, AuditEvidenceExporter, AgentTask
    model/                    — ModelAdapter, ModelEndpointConfig, OpenAiRealtimeAdapter, OpenAiSpeechTranscriber, LocalHeuristicModelAdapter
    ota/                      — OtaUpdateClient
    policy/                   — PolicyEngine, PolicyDecision, AppCapabilityPolicy, AuditLog, CapabilityRisk
```

What is implemented in the UI today:

- Programmatic Java Views — no XML layouts. Every `LinearLayout`,
  `FrameLayout`, `TextView`, `EditText`, `ScrollView` is built in
  `MainActivity.buildView()`.
- Custom drawables hand-rolled with `GradientDrawable` and `Canvas`.
- Two-surface architecture: chat surface and "Advanced" surface live in the
  same activity, swapped via `View.setVisibility`. Back button returns chat.
- A chat-style home with empty state, three suggestion chips, composer pill
  with a custom `AssistantIconButton` that paints mic/send/stop/profile/back
  glyphs on a `Canvas`.
- Profile-icon dropdown that anchors a `PopupWindow` and currently animates
  by hand using `ViewPropertyAnimator`.
- A custom `GlassPanel` that samples a pre-blurred wallpaper bitmap through
  a `BitmapShader` to mimic frosted glass.
- Advanced surface includes Model settings, OTA preview, Task Grants,
  Developer Controls (Start / Run Agent / Stop / Screen / Shot / Back /
  Export Trace / Export Audit / raw Action JSON / Approve / Deny / Refresh)
  plus monospace Screen Context and Audit Log dumps.
- Composer behavior: mic when empty, send when text, stop while listening
  or running. Keyboard-aware insets. Outside-tap dismisses keyboard.
- Voice flow with `OpenAiSpeechTranscriber`, RECORD_AUDIO permission flow,
  island voice launch.
- Per-task grants persisted in `Settings.Secure` and app-private prefs.
- Pending action confirmation card.
- Trajectory + framework audit export to `Downloads/OpenPhone`.
- OTA feed check + download.

What is wrong with this UI today:

- **Performance**: per-frame `BitmapShader` sampling on multiple capsules
  caused ANRs when the IME was open and animations ran simultaneously.
- **Smoothness**: hand-rolled `ObjectAnimator` / `ViewPropertyAnimator`
  choreography is fragile and can desync from layout passes.
- **State**: 2400 lines of mutable activity fields tracking task state,
  agent state, voice state, UI state. Whole categories of UI bugs come from
  this state going out of sync with what is rendered.
- **No design system**: every color, padding, corner radius, font size is a
  magic number chosen ad hoc. No typography scale, no spacing rhythm, no
  motion spec.
- **No widget vocabulary**: chips, cards, dropdowns, sheets, dialogs,
  snackbars, text fields, lists are all hand-rolled. Every reinvention
  introduces inconsistency.
- **Iteration is slow**: each visual change is hours of edits in
  `MainActivity.java`, then `rsync → build → push → reboot → tap into the
  screen`. There is no `@Preview`, no design tokens, no fast loop.

---

## 3. Why Compose, Why Now

The two real options that were considered:

- **Option 2 — Vendor Material Components / AppCompat**. ~2 days. Gives a
  polished Pixel-style consumer Android look. Won't deliver iOS-style
  frosted glass (that is an Android platform constraint, not a library
  one). Still ends up using `findViewById` + mutable `View` fields, still
  uses raw `Animator` underneath, still no `@Preview`.
- **Option 3 — Vendor Compose + Material 3**. ~1 week. Real declarative UI,
  real state model, real motion primitives, real `@Preview`, real
  `Modifier.blur` (content blur, same Android limitation as above), and the
  framework actually choreographs animations correctly with layout.

Long term, **Compose is the right answer**. After ~5–6 UI iterations,
Compose is already cheaper than Material Components, and the smoothness
ceiling is much higher.

**Backdrop blur honesty**: neither option fixes the "iOS frosted glass"
problem. Real per-element backdrop blur is a hardware path (HWUI / Material
You's tonal-elevation pipeline / `Window.setBackgroundBlurRadius` for
window-scope only). We are stopping the pursuit of iOS-style blur and
designing within what Android actually offers — translucent surface tints,
real elevation, real motion. Compose makes that easier, not harder.

---

## 4. Hard Constraints — Why This is Not a Normal Android Project

**This app does not use Gradle.** It is built by **Soong** (`Android.bp`)
inside an Android/LineageOS source tree. Every external library has to be
vendored as a prebuilt AAR/JAR and imported with `android_library_import`
or `java_import` blocks.

- **Build system**: Soong (`Android.bp`).
  Current `Android.bp` is at
  `overlay/packages/apps/OpenPhoneAssistant/Android.bp`. It declares
  `platform_apis: true`, `certificate: "platform"`, `privileged: true`,
  `system_ext_specific: true`. That status must be preserved — the
  privileged framework Binder service only trusts platform-signed callers.
- **Classpath**: `framework.jar` (the platform classpath) plus whatever we
  vendor. There is no Maven/Gradle resolver. There is no
  `repositories { mavenCentral() }`.
- **No AndroidX today**: the platform classpath has its own `androidx.*`
  in some Android trees, but we have not relied on it. Some AndroidX
  libraries assume access to versions that conflict with what is on the
  platform classpath; privileged apps see the platform classpath first,
  so duplicate-class errors are common.
- **Kotlin today**: not used by this app. Soong supports `kotlin_srcs` in
  `android_app` modules, but this app has only Java sources.
- **Compose plugin coupling**: Compose is not just a library — it is a
  Kotlin compiler plugin (`androidx.compose.compiler:compiler`). It is
  **strictly tied** to a specific Kotlin compiler version. If the platform
  tree's Kotlin version does not match the Compose compiler's required
  Kotlin version, nothing builds. This is the single highest-risk piece of
  the migration.
- **Privileged signing**: the APK must keep building with `platform`
  certificate. Material/AppCompat does not break this; Compose does not
  break this — but build-system mistakes (e.g. accidentally including a
  conflicting `AndroidManifest.xml` from an AAR) can.
- **Resource conflicts**: AAR resources compile into the same `R.attr`
  namespace as the platform; mismatches show up as `aapt2 link` failures
  with cryptic attribute IDs. Each one takes ~10–20 minutes to chase.
- **Dev loop**: assistant-only changes go EC2 build → SCP APK →
  `scripts/push-assistant-apk.sh` → reboot Pixel 9a → re-test. Full OTA
  builds are reserved for framework / sepolicy / Settings / SystemUI /
  boot-chain / first-install changes. Compose migration should fit inside
  the assistant-only path.
- **Behavior to preserve** (do not regress any of these):
  - Boot path: `OpenPhoneBootReceiver` starts
    `OpenPhoneAssistantService` after `LOCKED_BOOT_COMPLETED`,
    `BOOT_COMPLETED`, and package replacement.
  - Privileged Binder calls into `OpenPhoneAgentManager`
    (`startTask`, `stopTask`, `getScreen`, `executeAction`,
    `confirmAction`, `getAuditLog`, `getServiceStatus`,
    `getPointerEvents`).
  - Voice flow including `RECORD_AUDIO` permission, transcription via the
    development OpenAI path or the broker, island voice launch hand-off.
  - Composer state machine: mic-empty / send-text / stop-listening /
    stop-running, keyboard inset behavior, outside-tap dismissal.
  - Per-task grant defaults persisted to `Settings.Secure` keys
    (`openphone_task_grant_input`, `openphone_task_grant_screenshot`,
    `openphone_task_grant_clipboard`, `openphone_task_grant_share`,
    `openphone_task_grant_network`).
  - Task/agent lifecycle: `mActiveTaskId`, `mAgentThread`,
    `mAgentRunGeneration`, `mAgentRunCancelled` semantics, model adapter
    cancellation, voice run generation, in-flight model thread interrupt.
  - Pending action confirmation flow including framework
    `pending_action_id` and assistant model-tool confirmation requests.
  - Trajectory recorder, audit evidence exporter, OTA preview client.
  - Pointer overlay, dynamic-island hide-while-foreground rule, accessibility
    service auto-enable.
  - All `EXTRA_*` debug intent extras used by
    `scripts/run-assistant-task.sh` (goal, base64 goal, run, start voice,
    stop agent, dev OpenAI API key on userdebug/eng builds).
- **CI**: every change must pass `./scripts/check.sh` from the repo root.
- **Validation gate**: a full APK push + Pixel 9a reboot + repeat the
  Assistant UI Smoke Test from `docs/TESTING.md` is mandatory before
  declaring the migration done.

---

## 5. Goals and Non-Goals

### Goals

1. Migrate the entire `MainActivity` UI to Jetpack Compose with Material 3.
2. Keep every behavior listed under "Behavior to preserve" working.
3. Make the chat surface feel like a polished consumer AI app: clean
   typography, real motion, smooth IME handling, smooth navigation between
   chat ↔ Advanced.
4. Introduce a `ViewModel` (or equivalent state holder) so that task,
   agent, voice, OTA, model, and pending-confirmation state are not raw
   activity fields anymore.
5. Add `@Preview` coverage for every screen and major composable.
6. Pass `./scripts/check.sh` and the Pixel 9a Assistant UI Smoke Test.

### Non-Goals

- Do **not** redesign anything outside the assistant package
  (`overlay/packages/apps/OpenPhoneAssistant`). Settings, SystemUI,
  framework, sepolicy stay untouched.
- Do **not** chase iOS frosted glass / per-element backdrop blur. We
  accept Material 3 surface tints + elevation as the "depth" language.
- Do **not** change the framework Binder API, the privileged-app
  signing, the boot-receiver flow, the accessibility service, the
  OTA client logic, or the model adapters' transport behavior.
- Do **not** introduce Gradle. Stay on Soong/`Android.bp`.
- Do **not** remove developer/debug controls. They stay on the Advanced
  surface; only the chat surface is consumer-facing.

---

## 6. Architecture Target

### Module structure (no new Gradle modules — single Soong module stays)

```
overlay/packages/apps/OpenPhoneAssistant/
  Android.bp                       — adds Kotlin srcs + Compose imports
  AndroidManifest.xml              — unchanged (still platform-signed, privileged)
  res/                             — keep existing resources, add Compose-friendly themes
  prebuilts/                       — NEW: vendored Compose + AndroidX AARs / JARs
  src/org/openphone/assistant/
    MainActivity.kt                — replaces MainActivity.java; thin Activity entry point
    AssistantActivityBackend.java  — existing task/agent/voice/OTA behavior owner
    ui/
      OpenPhoneTheme.kt            — Material 3 theme + color/typography tokens
      AssistantApp.kt              — top-level NavHost: Chat, Advanced
      chat/
        ChatScreen.kt              — empty state, suggestion chips, composer, status pill
        ComposerBar.kt             — composer with mic/send/stop state
        SuggestionChips.kt
        ChatBubbleList.kt
        ConfirmationCard.kt
      advanced/
        AdvancedScreen.kt          — root for Model / OTA / Grants / Developer
        ModelSection.kt
        OtaSection.kt
        GrantsSection.kt
        DeveloperSection.kt
        ContextDump.kt
        AuditDump.kt
      common/
        GlassSurface.kt            — translucent Material 3 surface with stroke
        IconGlyphs.kt              — Compose-painted mic/send/stop/profile/back icons
    state/
      AssistantViewModel.kt        — holds agent, task, voice, OTA, grants, model state
      AssistantUiState.kt          — sealed UI state model
      ChatUiState.kt
      AdvancedUiState.kt
    (keep all existing non-UI classes unchanged)
    agent/
    model/
    ota/
    policy/
    OpenPhoneAssistantService.java
    OpenPhoneAccessibilityService.java
    OpenPhoneBootReceiver.java
    OpenPhoneNotificationController.java
    OpenPhoneQuickSettingsTileService.java
    OpenPhoneTriggerReceiver.java
    PointerOverlayController.java
    IOpenPhoneAssistant.aidl
```

The non-UI Java files stay Java. `MainActivity.java` is split into a thin
Kotlin `MainActivity.kt` entry point plus `AssistantActivityBackend.java`
for the existing privileged behavior while Compose owns rendering. The
custom `GlassPanel` and icon-painting code are replaced by Kotlin/Compose.

### State

- `AssistantViewModel` (extends `androidx.lifecycle.ViewModel`) owns:
  - `chat: StateFlow<ChatUiState>`
  - `advanced: StateFlow<AdvancedUiState>`
  - `pending: StateFlow<PendingConfirmation?>`
  - `agentRun: StateFlow<AgentRunStatus>`
  - `model: StateFlow<ModelConfig>`
  - `grants: StateFlow<TaskGrants>`
- ViewModel calls existing Java logic: `OpenPhoneAgentManager`,
  `FrameworkToolExecutor`, `TrajectoryRecorder`, `OtaUpdateClient`,
  `OpenAiSpeechTranscriber`, etc. The migration **does not rewrite any of
  those classes** — it puts a thin Kotlin facade around them.
- All `mAgentRunGeneration` / `mVoiceRunGeneration` / cancellation logic
  stays semantically identical, expressed as `Job` + `coroutineScope` + a
  cancellation token where it makes Kotlin sense, but with the same
  guarantees the Java code provides today.

### Theme

- Material 3 light theme with custom color seed driven by OpenPhone brand
  (the existing `R.color.openphone_*` palette becomes a `ColorScheme`).
- Typography scale based on system fonts; no custom font assets.
- Optional: keep the meadow wallpaper as a translucent backdrop behind the
  chat surface (decorative only — no per-element blur math).

---

## 7. Step-by-Step Plan

Each step is a single PR or commit. Each step ends with **`./scripts/check.sh`
passing**, and the high-risk steps end with a **Pixel 9a smoke test**.

### Step 0 — Baseline pin (≤30 min)

- Tag the current main commit before starting.
- Capture current assistant `versionCode` / `versionName` from
  `AndroidManifest.xml`.
- Capture v7 screenshots and a logcat slice to
  `.worktree/artifacts/tegu/ui-test/pre-compose/` for before/after diffs.

### Step 1 — Add Kotlin to the assistant module (≤4 hours)

- Edit `overlay/packages/apps/OpenPhoneAssistant/Android.bp` to add
  `srcs: ["src/**/*.kt"]` and confirm Soong compiles a placeholder
  `Empty.kt` alongside the existing Java sources.
- Confirm the platform tree's bundled Kotlin version with
  `find prebuilts/build-tools -name "kotlinc*"` and
  `find external/kotlinc -name "build.txt"`. Record it in this doc.
- Add a no-op `Empty.kt`, build with
  `OPENPHONE_BUILD_GOAL=OpenPhoneAssistant ./scripts/build.sh openphone_tegu`.
- Push to Pixel 9a, confirm app still launches and behaves identically.

Implementation note, 2026-06-07:

- `src/**/*.kt` is now included in `OpenPhoneAssistant`.
- The local `.worktree/android` checkout is sparse and does not currently
  include `external/kotlinc`, `prebuilts/build-tools`, or `build/soong`, so
  the platform-bundled Kotlin version could not be confirmed from this
  checkout. The Compose prebuilt set below is pinned to Kotlin `1.9.22` and
  Compose compiler `1.5.10`; a complete LineageOS tree must confirm the
  bundled Kotlin compiler before the focused APK build gate can be closed.

### Step 2 — Vendor AndroidX foundation (≤4 hours)

Add prebuilts under `overlay/packages/apps/OpenPhoneAssistant/prebuilts/`
and import each in `Android.bp`. Required closure (versions to be pinned
to a single coherent set the Compose compiler will accept):

- `androidx.core:core`
- `androidx.activity:activity` and `androidx.activity:activity-compose`
- `androidx.lifecycle:lifecycle-runtime`
- `androidx.lifecycle:lifecycle-runtime-ktx`
- `androidx.lifecycle:lifecycle-viewmodel`
- `androidx.lifecycle:lifecycle-viewmodel-compose`
- `androidx.lifecycle:lifecycle-runtime-compose`
- `androidx.savedstate:savedstate`
- `androidx.annotation:annotation`
- `org.jetbrains.kotlin:kotlin-stdlib`
- `org.jetbrains.kotlinx:kotlinx-coroutines-core`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android`

Implementation note, 2026-06-07:

- AndroidX foundation prebuilts are vendored under
  `overlay/packages/apps/OpenPhoneAssistant/prebuilts/maven/` and imported in
  `Android.bp`.
- Pinned versions: AndroidX Core `1.13.1`, Activity `1.9.0`, Lifecycle
  `2.7.0`, SavedState `1.2.1`, Annotation `1.8.0`, Kotlin stdlib `1.9.22`,
  coroutines `1.8.1`.
- `prebuilts/SHA256SUMS` records exact artifact hashes.

For each, write an `android_library_import` (AAR) or `java_import` (JAR).
Verify with a no-op composable build.

**Likely friction**: AndroidX often expects access to attributes that
clash with platform `R.attr`. Resolve case by case — usually with a
narrower theme parent or by overriding the conflicting attribute in
`res/values/themes.xml`.

### Step 3 — Vendor Compose (≤6 hours)

Add the Compose AAR closure:

- `androidx.compose.runtime:runtime`
- `androidx.compose.runtime:runtime-saveable`
- `androidx.compose.ui:ui`
- `androidx.compose.ui:ui-graphics`
- `androidx.compose.ui:ui-text`
- `androidx.compose.ui:ui-unit`
- `androidx.compose.ui:ui-util`
- `androidx.compose.foundation:foundation`
- `androidx.compose.foundation:foundation-layout`
- `androidx.compose.animation:animation`
- `androidx.compose.animation:animation-core`
- `androidx.compose.material3:material3`
- `androidx.compose.material:material-ripple`

Add the Compose compiler plugin. In Soong, attach it as a `kapt`-style
plugin or via `kotlinc_flags` — the exact wiring depends on the platform
tree's Kotlin support. Verify by adding a single `Hello world` composable
in a test screen, building, and pushing to Pixel 9a.

Implementation note, 2026-06-07:

- Compose prebuilts are vendored under
  `overlay/packages/apps/OpenPhoneAssistant/prebuilts/maven/` and imported in
  `Android.bp`.
- Pinned versions: Compose runtime/UI/foundation/animation `1.6.7`, Material
  3 `1.2.1`, Compose compiler `1.5.10`.
- `androidx.compose.ui:ui-tooling-preview:1.6.7` was added because this plan
  requires `@Preview` coverage.
- AOSP Soong only permits `kotlin_plugin` modules from allowed projects such
  as `external/kotlinc`. `Android.bp` now attaches the platform-provided
  `kotlin-compose-compiler-plugin` through the consuming module's
  `kotlin_plugins` property.

**Highest-risk step**. If the Compose compiler's required Kotlin version
does not match the platform tree's Kotlin version, this step blocks until
either the Kotlin version is upgraded in the tree (out of scope) or a
matching older Compose version is selected.

### Step 4 — Theme + entry point (≤2 hours)

- Add `OpenPhoneTheme.kt` with a Material 3 `ColorScheme` derived from the
  current `R.color.openphone_*` palette.
- Convert `MainActivity.java` → `MainActivity.kt`. Keep the activity body
  small: it should `setContent { OpenPhoneTheme { AssistantApp(...) } }`,
  hand off to the Compose tree, and forward the existing `Intent` extras
  to the ViewModel.
- Confirm the existing accessibility service, boot receiver, notification
  controller, Quick Settings tile still bind to the activity correctly.

Implementation note, 2026-06-07:

- `OpenPhoneTheme.kt` now defines the Material 3 theme.
- `AssistantComposeHost.kt` creates the `ComposeView` and renders
  `AssistantApp`.
- `MainActivity.kt` is now a thin Activity entry point. The former Java
  activity behavior lives in `AssistantActivityBackend.java`, whose
  `onCreate()` installs the Compose host as the Activity content view.
- The Java backend remains the behavior owner for task, voice, OTA, grant,
  and approval flows, exposed to Compose through explicit `onCompose*`
  bridge methods and state callbacks.

### Step 5 — ViewModel + state (≤4 hours)

- Create `AssistantViewModel` that wraps:
  - `OpenPhoneAgentManager`
  - `FrameworkToolExecutor`
  - `TrajectoryRecorder`
  - `OpenAiSpeechTranscriber`
  - `OtaUpdateClient`
  - `AppCapabilityPolicy`
- Expose `StateFlow`s for the UI shapes listed in the Architecture section.
- Move all the mutable `m*` activity fields into the ViewModel as
  appropriate `MutableStateFlow`s. **Preserve cancellation semantics
  exactly**: the existing `mAgentRunGeneration` guard becomes a
  `Job`-cancellation pattern; the existing model-adapter `cancel()` keeps
  being called the same way.
- Unit tests are out of scope for this migration. Manual physical evals
  on Pixel 9a are the validation gate.

Implementation note, 2026-06-07:

- `state/AssistantUiState.kt` defines chat, advanced, pending confirmation,
  model, OTA, grants, and developer state shapes.
- `state/AssistantViewModel.kt` owns the initial `StateFlow` UI model and
  basic chat/navigation mutations.
- `MainActivity` now has Compose-owned fallback values for composer text,
  model config, OTA feed URL, raw action JSON, and grant defaults. Backend
  paths read through helper methods instead of directly depending on
  `EditText`/`CheckBox` widgets, which allows the Compose entry surface to
  call the same privileged task/agent methods.

### Step 6 — Chat surface in Compose (≤6 hours)

Build, in this order, with `@Preview` for each:

1. `ChatScreen` shell with top app bar and bottom composer slot.
2. `EmptyStateHero` with brand mark, greeting, hint, and three
   `SuggestionChip`s. Tapping a chip fills the composer.
3. `ComposerBar` with state: empty → mic, with text → send, listening or
   running → stop. IME-aware insets via `Modifier.imePadding()` (no manual
   `SOFT_INPUT_ADJUST_RESIZE` plumbing).
4. `ChatBubbleList` for user/agent messages.
5. `StatusPill` shown only while a task is active.
6. `ConfirmationCard` shown when `pending` is non-null.
7. `ProfileMenu` — `DropdownMenu` from Material 3 with a single
   "Developer settings" item.

Wire each to the ViewModel. Verify outside-tap-keyboard-dismissal,
keyboard inset behavior, and chip-tap behavior on the device.

### Step 7 — Advanced surface in Compose (≤4 hours)

Use a `NavHost` so chat ↔ advanced gets real Material 3 motion (slide +
fade) instead of hand-rolled crossfade.

Sections, each in its own composable with `@Preview`:

- `ModelSection` — vision model toggle, broker toggle, dev API key field,
  broker URL/token, disclosure card.
- `OtaSection` — feed URL, Check, Download, status text.
- `GrantsSection` — five toggles backed by `Settings.Secure` keys.
- `DeveloperSection` — Start / Run Agent / Stop / Screen / Shot / Back /
  Export Trace / Export Audit / raw Action JSON / Action / Approve / Deny
  / Refresh.
- `ContextDump` and `AuditDump` — monospace text in `Card`.

Implementation note, 2026-06-07:

- Advanced controls are now editable in Compose and bridged into the existing
  Activity backend:
  - model toggles, dev API key, broker URL/token
  - OTA feed URL
  - task grant toggles
  - developer goal and raw action JSON
- Developer action buttons now call the corresponding existing backend
  methods for Start, Run Agent, Stop, Screen, Shot, Back, Export Trace,
  Export Audit, raw Action, Approve, Deny, and Refresh.
- Activity backend status writes now mirror into Compose state for task
  status, screen context, audit log, model disclosure, OTA status,
  runtime/listening/running status, conversation messages, and pending
  confirmations.
- Hardware Back now returns Compose Advanced to Chat by routing through the
  Compose state callback.

### Step 8 — Motion + smoothness pass (≤3 hours)

- Empty-state hero entrance: fade + 8dp slide-up.
- Chip press: `clickable` with `Modifier.scale(animatedScale)`.
- Dropdown reveal: Material 3 default (already correct).
- Chat ↔ Advanced: `NavHost` enter/exit transitions, default Material 3
  fade-through.
- Status pill: `AnimatedVisibility` with slide + fade.
- Confirmation card: `AnimatedVisibility` with slide-up from below.

No hand-rolled `ObjectAnimator` anywhere in the new code.

### Step 9 — Strip the old code (≤2 hours)

- Delete `MainActivity.java`, `GlassPanel.java`, the
  `AssistantIconButton` inner class (replaced by Compose icons), and the
  glass-related drawables that are no longer used.
- Keep `openphone_sky.jpg` if the chat surface uses it as a decorative
  backdrop. Delete `openphone_sky_blur.jpg` (no longer needed without the
  per-element shader).
- Update `res/values/styles.xml` if needed for the Material 3 theme.

Implementation note, 2026-06-07:

- `MainActivity.java` has been removed. `MainActivity.kt` now subclasses
  `AssistantActivityBackend.java`, preserving the manifest activity name,
  debug intent constants used by pointer-overlay launches, and existing
  privileged behavior.
- Legacy Java View rendering has been stripped from
  `AssistantActivityBackend.java`; UI state now flows through
  `ComposeStateCallbacks` into `AssistantViewModel`.
- `GlassPanel.java` and `openphone_sky_blur.jpg` have been deleted. The chat
  surface keeps `openphone_sky.jpg` as a decorative backdrop without
  per-element shader blur.
- The old canvas icon button has been removed; Compose icons live in
  `ui/common/IconGlyphs.kt`.

### Step 10 — Validation (≤3 hours)

- `./scripts/check.sh` passes locally.
- Build the assistant APK on EC2 with the standard flow.
- Push to Pixel 9a with `scripts/push-assistant-apk.sh`.
- Run the full Assistant UI Smoke Test from `docs/TESTING.md`:
  - app opens to the chat-style home screen
  - profile icon opens Developer settings dropdown
  - Developer settings navigates to Advanced
  - mic / send / stop icon transitions correct
  - keyboard inset behavior correct
  - outside-tap dismisses keyboard
  - logcat has no `FATAL EXCEPTION` / `AndroidRuntime` from the assistant
- Run the two required agent evals:
  - `scripts/run-assistant-task.sh --goal "Open Settings." --wait 90`
  - `scripts/run-assistant-task.sh --goal "Open Settings, open the Apps settings page, then finish when the Apps page is visible." --wait 120`
  - confirm `task.finished` and the trajectory contains expected
    `tap_element` / `finish_task` events.
- Bump the assistant `versionCode` / `versionName` in
  `AndroidManifest.xml` to mark the Compose cutover release.
- Capture before/after screenshots and update
  `docs/IMPLEMENTATION_STATUS.md`.

Implementation note, 2026-06-07:

- `./scripts/check.sh` passed.
- The final focused Assistant APK build was run on EC2 for
  `openphone_tegu-bp4a-userdebug`, not locally.
- Final APK SHA-256:
  `71836a3a86d10a959210a449b91816d74725fee904ec6135beaeb08caf6366b5`.
- The APK was pushed to the physical Pixel 9a with
  `scripts/push-assistant-apk.sh`; after reboot the installed
  `/system_ext/priv-app/OpenPhoneAssistant/OpenPhoneAssistant.apk` hash matched
  and PackageManager reported `versionCode=58`, `versionName=0.1.22-dev`.
- `service check openphone_agent` reported `found`.
- UI smoke evidence under `.worktree/reports/compose-smoke/` covers home,
  Profile dropdown, keyboard/send state, Advanced navigation, hardware Back to
  Chat, and final logcat/process checks with no assistant fatal crash or ANR
  entries.
- Required evals passed:
  - Open Settings: trajectory
    `.worktree/evals/compose-open-settings-v58-final2/20260607-183026-task-121301688290`
    validates and records `task.finished`.
  - Apps Settings page: validated report
    `.worktree/evals/compose-apps-settings-v58-final2-20260607T153300Z/agent-eval.json`
    records `task.finished` with `open_app`, `tap_element`, and `finish_task`.

---

## 8. Risk Register

| Risk | Likelihood | Mitigation |
|---|---|---|
| Compose compiler / Kotlin version mismatch with the platform tree | **High** | Pin Compose to the version whose required Kotlin matches what is in the tree. Validate with a Hello-world composable before vendoring the rest. |
| AndroidX / platform `R.attr` collisions in `aapt2 link` | Medium | Resolve case by case. Use a narrow Material 3 theme parent and override conflicting attrs. |
| Privileged-app signing breaks after AAR ingestion | Low | Each AAR ingestion is its own commit; check signing locally after every step. |
| Privileged Binder calls regress because of activity lifecycle change | Medium | The ViewModel's `viewModelScope` lifetime differs from activity. Ensure model-adapter cancel paths call into the same Java cancellation primitives the current code uses. Validate by running an agent task and pressing stop at every visible transition. |
| Voice flow regresses with `RECORD_AUDIO` permission re-prompts | Medium | Permission flow stays in the activity, not the ViewModel. The ViewModel only consumes the result. |
| OTA preview client regresses because file IO moves to the wrong scope | Low | OtaUpdateClient is unchanged. The ViewModel just calls it. |
| `EXTRA_*` debug intents stop working | Medium | Activity intent handling stays in `MainActivity.kt`; forward to ViewModel exactly the same way. |
| Visual regressions in the Advanced surface | Low | Advanced is for developers; we keep all controls, just restyled. |
| Loss of `@Preview` because of resource lookup failures | Low | Use `LocalContext.current` carefully; preview-only fakes for any framework-Binder-dependent state. |

---

## 9. Acceptance Criteria

The migration is done when **all** of the following are true:

1. `./scripts/check.sh` passes from the repo root.
2. `OPENPHONE_BUILD_GOAL=OpenPhoneAssistant ./scripts/build.sh openphone_tegu`
   produces a privileged-signed APK with the new Compose UI.
3. The APK is pushed to a real Pixel 9a, the device reboots cleanly, and
   the assistant launches.
4. `service check openphone_agent` reports `found`.
5. The Assistant UI Smoke Test from `docs/TESTING.md` passes manually.
6. Both required agent evals (`Open Settings.` and the Apps-page eval)
   pass with `task.finished` and the expected trajectory evidence.
7. Logcat after a 5-minute exercise of chat → keyboard → dropdown →
   advanced → back → voice → run agent contains **zero**
   `FATAL EXCEPTION` or `AndroidRuntime` entries from
   `org.openphone.assistant`.
8. **No ANRs** in `dumpsys activity processes` for the assistant during
   the smoke test.
9. The assistant `versionCode` / `versionName` in
   `AndroidManifest.xml` is bumped, and
   `docs/IMPLEMENTATION_STATUS.md` is updated with the new manifest
   version, the Compose cutover note, and the APK SHA-256.
10. `MainActivity.java` and `GlassPanel.java` are deleted.

---

## 10. Out of Scope (Explicit Non-Goals, Restated)

- Settings, SystemUI, framework, sepolicy.
- iOS-style backdrop blur.
- Gradle.
- Material Components / AppCompat (we are skipping option 2).
- Rewriting the agent loop, the model adapters, the OTA client, the
  trajectory recorder, the audit exporter, the accessibility service, the
  framework Binder service, or any of the privileged behavior.
- New product features. This is a UI re-platforming, not a feature pass.

---

## 11. Reference Files

If continuing in a new chat or session, read these in order before
starting work:

1. [`SPEC.md`](../SPEC.md) — what OpenPhone is.
2. [`docs/PLAN.md`](PLAN.md) — current product plan and what's proven.
3. [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) — system architecture.
4. [`docs/IMPLEMENTATION_STATUS.md`](IMPLEMENTATION_STATUS.md) — what
   has shipped, current `versionCode` / `versionName`.
5. [`docs/TESTING.md`](TESTING.md) — Assistant UI Smoke Test, eval suite.
6. [`docs/BUILD.md`](BUILD.md) — Soong build flow and EC2 path.
7. [`devices/tegu.md`](../devices/tegu.md) — Pixel 9a baseline, recovery
   notes, ADB onboarding.
8. `overlay/packages/apps/OpenPhoneAssistant/Android.bp` — the build file
   to edit in Step 1.
9. `overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/MainActivity.kt`
   — the thin Activity entry point.
10. `overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/AssistantActivityBackend.java`
   — the preserved task/agent/voice/OTA behavior backend.
11. `overlay/packages/apps/OpenPhoneAssistant/AndroidManifest.xml` — the
    manifest, including the privileged permissions and the `versionCode`
    that must be bumped at the end.

---

## 12. Working Loop for the New Session

```bash
# 1. From repo root, before any change:
./scripts/check.sh

# 2. After each Compose step, sync the overlay to EC2:
rsync -az --delete \
  -e 'ssh -i claudecode.pem -o StrictHostKeyChecking=no' \
  overlay/packages/apps/OpenPhoneAssistant/ \
  ubuntu@ec2-18-189-1-174.us-east-2.compute.amazonaws.com:/home/ubuntu/OpenPhone/.worktree/android/packages/apps/OpenPhoneAssistant/

# 3. Build the assistant APK on EC2:
ssh -i claudecode.pem -o StrictHostKeyChecking=no \
  ubuntu@ec2-18-189-1-174.us-east-2.compute.amazonaws.com \
  'cd /home/ubuntu/OpenPhone/.worktree/android && bash -c "source build/envsetup.sh && lunch openphone_tegu-bp4a-userdebug && m nothing && prebuilts/build-tools/linux-x86/bin/ninja -f out/combined-openphone_tegu.ninja out/target/product/tegu/obj/APPS/OpenPhoneAssistant_intermediates/package.apk"'

# 4. Pull the APK back:
mkdir -p .worktree/artifacts/tegu
scp -i claudecode.pem -o StrictHostKeyChecking=no \
  ubuntu@ec2-18-189-1-174.us-east-2.compute.amazonaws.com:/home/ubuntu/OpenPhone/.worktree/android/out/target/product/tegu/obj/APPS/OpenPhoneAssistant_intermediates/package.apk \
  .worktree/artifacts/tegu/OpenPhoneAssistant-compose.apk

# 5. Push to Pixel 9a:
scripts/push-assistant-apk.sh .worktree/artifacts/tegu/OpenPhoneAssistant-compose.apk

# 6. Wait for boot and launch:
adb wait-for-device
for i in $(seq 1 90); do
  state=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  [ "$state" = "1" ] && break
  sleep 2
done
adb shell input swipe 540 2150 540 900 350
adb shell am start -n org.openphone.assistant/.MainActivity

# 7. Capture screenshot for the step:
adb exec-out screencap -p > .worktree/artifacts/tegu/ui-test/compose-stepN.png

# 8. Crash check:
adb logcat -d -t 2000 | grep -E 'FATAL EXCEPTION|AndroidRuntime|org.openphone.assistant' | tail -80
```

---

## 13. Author Notes for the Next Session

The previous session shipped through ~v7 of a hand-rolled glass UI on
top of `LinearLayout` + `GradientDrawable` + a custom `BitmapShader`
backdrop sampler called `GlassPanel`. The assistant ANR'd repeatedly
under combined IME + animation + multi-panel-redraw load. We concluded
the **right fix is not more shader optimization, but a better tool**.
Compose + Material 3 is that tool.

Every iteration of the previous approach took hours and produced
diminishing returns. The first iteration of the Compose approach will
take longer (~1 week of focused work) because of the privileged-system
build wiring. After that, every UI iteration is minutes.

Stay disciplined about scope. The temptation will be to redesign things,
add features, or refactor the agent loop. **Do not.** The migration is
done when the existing UI behaves the same on Pixel 9a, written in
Compose, with the chat surface feeling smooth and the Advanced surface
unchanged in capability. New design moves come *after* the migration
ships.
