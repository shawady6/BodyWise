# BodyWise

**خطوة صغيرة كل يوم.**

BodyWise is a simple, personal Android tracker with three sections:
1. **Food & Water:** a Saturday–Friday weekly view, breakfast/lunch/dinner entries, and daily water cups.
2. **Weight & Measurements:** weekly weight and body measurements plus a private progress-photo timeline.
3. **Settings & Updates:** dark mode, local data export, and a GitHub release update checker.

## Project status
The first functional Android implementation is being built. It is a personal tracking tool, not a medical product.

## Tech
- Kotlin + Jetpack Compose
- Offline-first local storage using Android app preferences and private files
- GitHub Actions builds a debug APK on pushes to `main`

## Build
Open the repository in Android Studio, sync Gradle, then run the `app` configuration.

Or with Gradle 8.11.1 and JDK 17:
```bash
gradle assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Updates
The app checks the latest GitHub Release. Android requires the user to approve installation of downloaded app updates; the app does not silently install APKs.

## Privacy
No AI, AI APIs, accounts, or required backend. Food logs, measurements, and progress photos are stored locally on the device. Internet is used only for checking the latest app release.

## Version 0.2.0

Camera capture for weekly progress photos, a redesigned BodyWise launcher icon, and optional biometric app lock.
