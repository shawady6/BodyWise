# BodyWise AI — Product Specification

## Purpose
A private, single-user Android companion for nutrition, body measurements, sustainable progress, and proactive weekly guidance.

## Confirmed product decisions
- Android only; personal use; single user.
- Offline-first with Arabic RTL and English support.
- Egyptian food first, with international foods too.
- Hybrid AI: local/offline baseline and optional cloud AI.
- Light and dark themes.
- Local backup plus optional cloud backup.
- Metric units: kg, cm, kcal.
- **Weekly Planner planning mode: AI-generated weekly plan.** The app proposes a complete week; the user reviews, edits, and explicitly approves it before activation.
- **Weekly Planner notifications: ALL categories enabled by default** — meals, water, walking/activity, and sleep. Each category can be configured independently in settings.

## Core modules
1. Dashboard: daily calories, macros, water, weight and helpful insight.
2. Food diary: breakfast, snacks, lunch and dinner; manual entry, search, text/photo AI draft and saved meals.
3. Food catalog: per-100g calories, protein, carbs, fat and fiber; raw/cooked state and source.
4. Weight: dated entries, weekly averages and trend charts.
5. Measurements: neck, shoulders, chest, waist, abdomen, hips, arms, thighs and calves.
6. Progress photos: private app storage and dated comparisons; never treat photos as precise body-fat measurements.
7. AI coach: reviewed food estimates, weekly summaries and trend explanations.
8. Water and activity logs.
9. Reports and encrypted backup/restore.
10. **Weekly Planner & Smart Coach** (priority feature; detailed requirements below).

## Weekly Planner & Smart Coach

### Planning experience
- The default planning flow is AI-first: generate a proposed plan for the upcoming seven days (Saturday–Friday, localized to the user's selected week start).
- Collect the profile and preference inputs needed to create a safe, realistic plan: waking/sleep times, meal schedule, food preferences and exclusions, available foods, activity preferences, water preferences, and user-approved nutrition goals.
- Propose each day's meal schedule, meal suggestions and portions, hydration reminders/targets, walking or activity sessions, and sleep/wind-down reminders where configured.
- Support different schedules on different days; never assume every day is identical.
- Show the entire week for review. The user can edit meals, portions and times, regenerate a day or the whole week, and approve the plan.
- Never activate a generated plan or schedule notifications before explicit user approval.
- Keep manual editing available even though AI generation is the selected default mode.

### Notification categories
All categories are enabled by default, with individual toggles and per-day times:
- Meals: breakfast, snacks, lunch and dinner as configured in the approved plan.
- Water: user-configured hydration reminders, with sensible spacing and quiet hours.
- Walking/activity: planned activity reminders.
- Sleep: bedtime/wind-down reminders.
- Daily wrap-up and weekly review may be offered as optional reminders.

### Notification actions
Where supported, notifications should offer useful actions such as:
- Mark meal/activity as done.
- Snooze (for example, 15 minutes).
- Skip.
- Open the relevant planner/log screen to edit or replace the item.
A meal marked done should open or create a diary entry with the planned food and serving size prefilled; the user confirms or edits before saving.

### Smart Coach behavior
- Explain why the weekly plan was suggested and show estimates/uncertainty.
- If a meal or activity is missed, offer a practical adjustment to the remaining day; do not shame the user or encourage compensatory restriction.
- Suggest realistic food alternatives, prioritizing Egyptian foods and the user's preferences.
- Provide a weekly review based on logged data and clearly distinguish actual logs from planned items.
- AI suggestions are advisory; the user remains in control.

### Offline and Android behavior
- The planner, approved schedule, daily checklist, local data, and basic notifications must work offline without an AI subscription or network connection.
- AI generation may use an optional cloud provider only with explicit opt-in; provide a local/basic fallback or let the user edit a plan manually when offline.
- Persist plans and reminders locally (Room); use Android notification channels and scheduled background work, with rescheduling after reboot/time-zone changes.
- Request notification permission at the appropriate time on supported Android versions.
- Exact-time alarms may require additional Android permission and remain subject to OS battery/Doze restrictions. Explain this clearly; do not promise guaranteed delivery to the minute.
- Respect quiet hours, notification toggles, time zone, and user edits. Avoid duplicate reminders when a plan is regenerated or rescheduled.

## Product principles
- Never save AI food estimates without user confirmation.
- Show uncertainty and allow editing serving size and ingredients.
- No diagnosis, treatment advice, shame language or unsafe restriction.
- Health data remains local unless the user explicitly opts in to a cloud feature.
- Do not place provider API keys or secrets in the APK.
