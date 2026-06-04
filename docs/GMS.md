# User-Supplied Google Mobile Services

OpenPhone does not redistribute Google Mobile Services, Google Play Store, or
Google apps. Those packages are proprietary and require appropriate rights or
licenses to distribute.

Users may choose to install a compatible Google apps package on their own
device after flashing OpenPhone. OpenPhone can provide a host-side helper for
downloading and sideloading a public package into local ignored worktree state,
but the project must not mirror, bundle, redistribute, or ship Google packages
as OpenPhone release artifacts.

## Supported Project Position

OpenPhone release artifacts:

- do not include Google Play Store,
- do not include Google Play Services,
- do not include Gmail, Maps, YouTube, Chrome, or other Google apps,
- should remain useful without Google services installed.

User/developer devices:

- may sideload a compatible package after installing the OpenPhone OTA,
- are responsible for package source, license, compatibility, and trust,
- may still hit Play Integrity, device certification, unlocked bootloader,
  account, DRM, or app-specific compatibility failures.

## When to Install

Install the user-supplied Google apps package immediately after the OpenPhone
OTA, before the first normal boot, unless the package's own instructions say
otherwise.

Typical recovery sequence:

```text
1. Sideload OpenPhone OTA.
2. Recovery asks: "Install additional packages?"
3. Choose Yes if the package requires recovery to reboot first.
4. Return to Apply update -> Apply from ADB.
5. Sideload the user-supplied Google apps ZIP.
6. Reboot system.
```

If the phone already booted without Google services, a clean data wipe and
fresh OpenPhone OTA install may be required before adding the package.

## Helper Script

For the Pixel 9a (`tegu`) OpenPhone target, the locally validated path is
MindTheGapps for Android 16 / arm64. Download the latest release from the public
MindTheGapps GitHub release and verify its release-provided SHA-256:

```bash
scripts/download-mindthegapps.sh
```

By default, this saves the ZIP under:

```text
.worktree/downloads/gms/
```

The helper downloads from the public `MindTheGapps/16.0.0-arm64` GitHub release
repository and verifies the matching `.zip.sha256sum` asset. It does not commit,
mirror, or redistribute the ZIP.

To use a package you already downloaded:

```bash
scripts/sideload-user-gms.sh --package /path/to/user-supplied-gapps.zip
```

If Android is booted and ADB is authorized, the helper can reboot to recovery:

```bash
scripts/sideload-user-gms.sh \
  --reboot-recovery \
  --package /path/to/user-supplied-gapps.zip
```

The script:

- verifies that the package exists and is a ZIP,
- prints the ZIP SHA-256,
- waits until the device is in ADB sideload mode,
- runs `adb sideload`,
- leaves signature prompts and trust decisions to the user on the phone.

The script does not validate that a package is safe, licensed, or compatible.

## Validated Pixel 9a Flow

This flow was validated on a Pixel 9a OpenPhone/LineageOS 23.2 Android 16
bringup device.

Download and verify MindTheGapps:

```bash
scripts/download-mindthegapps.sh
```

Install OpenPhone OTA from recovery, then when recovery asks about additional
packages:

```text
Install additional packages? -> Yes
To install additional packages, reboot recovery first? -> Yes
Apply update -> Apply from ADB
```

From the host:

```bash
scripts/sideload-user-gms.sh \
  --package .worktree/downloads/gms/MindTheGapps-16.0.0-arm64-*.zip
```

When host-side sideload finishes with `Total xfer: 1.00x`, read the recovery log
on the phone. If recovery reports success, choose `Reboot system now`.

If the phone had already booted OpenPhone before Google services were added,
Google apps may still fail. The clean supported flow is to reinstall the
OpenPhone OTA, sideload MindTheGapps immediately as an additional package, then
boot normally.

## Compatibility Notes

The package must match the installed ROM's Android generation and architecture.
For the Pixel 9a OpenPhone target, use an `arm64` package for the same Android
generation as the installed OpenPhone/Lineage base.

Many mainstream apps work better with Google services installed, but installing
Google services does not make OpenPhone a certified Google device. Apps may
still fail or degrade when they require:

- Play Integrity / device certification,
- locked bootloader state,
- DRM attestation,
- Google account features,
- proprietary push or location APIs,
- app-specific anti-tamper checks.

## Product Strategy

OpenPhone should support three paths:

- no-Google mode with a curated OpenPhone app catalog,
- user-supplied Google services for developer/power-user devices,
- official Google Mobile Services only if OpenPhone obtains the required
  commercial/OEM licensing.
