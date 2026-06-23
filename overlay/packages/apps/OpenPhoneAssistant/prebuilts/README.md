OpenPhoneAssistant Compose prebuilts
====================================

This directory pins the Maven artifacts required by the historical Compose
planning notes in `docs/archive/COMPOSE_PLAN.md`.
AndroidX and Compose artifacts come from Google's Maven repository. Kotlin and
coroutines artifacts come from Maven Central.

Pinned set:

- AndroidX Activity 1.9.0
- AndroidX Core 1.13.1
- AndroidX Lifecycle 2.7.0
- AndroidX SavedState 1.2.1
- Compose 1.6.7
- Compose Material 3 1.2.1
- Compose compiler 1.5.10
- Kotlin stdlib 1.9.22
- Kotlin coroutines 1.8.1

`SHA256SUMS` records the exact downloaded artifacts. The platform checkout in
`.worktree/android` is currently sparse and does not include `external/kotlinc`
or `build/soong`, so the platform-bundled Kotlin version still needs to be
confirmed inside a complete LineageOS tree before the device build gate can be
closed.
