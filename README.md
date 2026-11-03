# BodyWise AI

**Understand Your Body. Improve Your Life.**

BodyWise AI is a personal, offline-first Android app for nutrition, weight, body measurements, progress photos, hydration and AI-assisted food logging.

## Project status
Initial Android foundation. This is an actively developed starter release, not a medical product.

## Tech
- Kotlin + Jetpack Compose
- Material 3
- GitHub Actions builds a debug APK on every push to `main`
- Tag a version (for example `v0.1.0`) to build and publish a GitHub Release

## Build
Open the repository in Android Studio (Hedgehog or newer), allow Gradle sync, then run the `app` configuration.

Or with Gradle 8.11.1 and JDK 17:
```bash
gradle assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Privacy
Health and body data stays on-device by default. Cloud AI, backup and photo upload are opt-in future integrations. Never commit API keys, personal health records or progress photos.

See `docs/` for product and technical plans.
