# BodyWise

**خطوة صغيرة كل يوم.**

BodyWise is an offline-first personal Android tracker with three sections:

1. **Food & Water:** Saturday–Friday weekly view, breakfast/lunch/dinner entries, and daily water tracking.
2. **Weight & Measurements:** weekly weight and body measurements, plus a private progress-photo timeline.
3. **Settings & Data Safety:** dark mode, optional biometric lock, in-app APK updates with SHA-256 verification, dark mode, optional biometric lock, and complete backup/restore.

## Data backup

- Create a single ZIP backup containing food logs, water records, measurements, photo metadata, and the actual progress-photo files.
- Restore a BodyWise ZIP backup from Android's document picker.
- Restoring replaces the current app records with the selected backup.
- Keep backup ZIP files somewhere safe; they may contain private body-progress photos.

## Tech

- Kotlin + Jetpack Compose
- Offline-first local storage using Android app preferences and private files
- GitHub Actions builds a debug APK and publishes releases

## Build

Open the repository in Android Studio, sync Gradle, then run the `app` configuration.

Or with Gradle 8.11.1 and JDK 17:

```bash
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Updates

The app checks the latest GitHub Release, downloads the APK with progress, verifies its SHA-256 digest, checks that its signing certificate matches the installed app, then opens Android’s installer for user approval. Updates signed with a different certificate are blocked to protect app data. A stable release signing key is required for seamless upgrades across builds. Configure the four GitHub Actions secrets `BODYWISE_KEYSTORE_BASE64`, `BODYWISE_STORE_PASSWORD`, `BODYWISE_KEY_ALIAS`, and `BODYWISE_KEY_PASSWORD` before publishing a release. The workflow intentionally skips public releases until signing is configured.

## Privacy

No AI, AI APIs, accounts, or required backend. Food logs, measurements, and progress photos are stored locally on the device. Internet is used only for checking the latest app release.

## Current version

v0.2.5 — in-app APK download and safe installation flow, plus complete ZIP backup and restore. The signed public release is generated only when the stable signing secrets are configured.
