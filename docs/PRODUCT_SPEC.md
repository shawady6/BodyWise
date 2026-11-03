# BodyWise — Simple Product Specification

## Purpose
A simple, single-user Android app for tracking meals, water, weight, body measurements, and weekly progress photos.

## Confirmed scope
- Android app; one personal user.
- Arabic RTL interface.
- Exactly three main sections: Food & Water, Weight & Measurements, Settings & Updates.
- No AI of any kind: no model, no API, no AI provider, and no AI/cloud fallback.
- Offline-first. User data and progress photos remain on the device.
- Metric units: kg and cm.

## 1. Food & Water
- Weekly view, Saturday through Friday.
- Three meal slots per day only: breakfast, lunch, dinner.
- Add and remove food entries; each entry has a name and optional quantity/notes.
- Record daily water intake in cups.
- Previously entered days remain available when switching dates.

## 2. Weight & Measurements
- Add dated weight entries in kg.
- Optional measurements: abdomen, waist, chest, arm, thigh in cm.
- Keep a chronological measurement history.
- Add a progress photo each week, date it, and show the photo timeline.
- Photos are stored in app-private local storage and are not uploaded.

## 3. Settings & Updates
- Light/dark theme.
- Export a basic data backup.
- Check the latest GitHub Release.
- Android installation/update must remain user-approved; do not attempt silent APK installation.

## Privacy and technical principles
- No sign-in and no required server.
- No AI, telemetry, analytics, or advertising SDKs.
- Network access is only for checking the public GitHub release.
- Validate inputs and handle empty states and storage errors.
- Do not present the app as medical advice or a diagnostic tool.

## Explicitly out of scope
AI features, calorie/macronutrient calculations, automatic meal generation, sleep/activity plans, social features, subscriptions, cloud sync, and food-photo recognition.
