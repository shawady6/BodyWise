# BodyWise AI — Product Specification

## Purpose
A private, single-user Android companion for nutrition, body measurements, sustainable progress, and proactive weekly guidance.

## Confirmed product decisions
- Android only; personal use; single user.
- Offline-first with Arabic RTL and English support.
- Egyptian food first, with international foods too.
- **Local AI only. No cloud AI provider, cloud inference, or remote AI fallback.**
- Light and dark themes.
- Local backup plus optional cloud backup for user-controlled backup only; this does not permit cloud AI processing.
- Metric units: kg, cm, kcal.
- **Weekly Planner planning mode: AI-generated weekly plan.** The app proposes a complete week; the user reviews, edits, and explicitly approves it before activation.
- **Weekly Planner notifications: ALL categories enabled by default** — meals, water, walking/activity, and sleep. Each category can be configured independently in settings.

## Local AI architecture

### Non-negotiable privacy and connectivity rules
- All AI inference runs on the Android device. User profile, food logs, measurements, photos, prompts and AI conversations must never be sent to a remote AI service.
- No Gemini, OpenAI, hosted inference API, server-side LLM, or cloud fallback.
- No AI API keys, provider secrets, or inference credentials in the APK.
- AI features must explain when a compatible model is not installed or the device cannot run it. Do not silently switch to cloud processing.
- A model may be downloaded by the user once (preferably on Wi-Fi) from a trusted, documented model distribution source. After installation, inference works offline. Make download size, model license, storage needs and device requirements visible.
- Verify model file integrity (e.g. SHA-256) and validate supported format before loading. Store model files in app-private storage and allow the user to delete or replace them.
- The app must remain useful without an installed LLM: deterministic nutrition calculations, local food database, manual planner, approved plans, checklist and notifications all continue to work.

### Runtime and model strategy
- Preferred Android runtime: **Google AI Edge LiteRT-LM Kotlin API** for on-device LLM inference. Do not start new implementation with MediaPipe LLM Inference; Google's current documentation marks it maintenance-only and recommends LiteRT-LM.
- Keep runtime behind an internal LocalAiEngine interface so model/runtime changes do not leak into planner, nutrition or UI code.
- Candidate models must be open-weight, locally runnable, quantized for mobile, license-reviewed and benchmarked on real Android hardware. Evaluate Arabic and Egyptian colloquial Arabic quality before selecting a default. Do not hard-code a model choice until size, RAM, latency, license and Arabic quality have been tested.
- Use a compact model first for text-based planning and coaching. Support CPU execution as baseline and hardware acceleration only when compatible; gracefully fall back to CPU or deterministic non-AI behavior if unsupported.
- Model download is an initial setup step and requires internet; after download, model inference itself must not require internet.
- First release scope for local AI: text prompts and structured JSON output for weekly planning/coach. Food-photo understanding requires a separate on-device vision model and is a later, separately benchmarked feature. Never pretend a text-only LLM can accurately identify food from an image.

### AI safety and structured output
- Ask the local model for schema-constrained/validated structured suggestions (days, meal slots, food suggestions, portions, reminder times, activity and sleep items).
- Validate all model output in deterministic app code before display or persistence. Reject malformed, out-of-range, unsafe or incomplete plans; never execute model-provided code or arbitrary actions.
- Nutrition totals, unit conversions, schedule calculations, reminder scheduling and goal bounds must be calculated by deterministic application code, not trusted to LLM arithmetic.
- Generated plans are drafts. The user must review, edit and explicitly approve before activation or scheduling reminders.
- Display uncertainty; AI suggestions are advisory and not medical advice. No diagnosis, treatment advice, shame language or unsafe restriction.

## Core modules
1. Dashboard: daily calories, macros, water, weight and helpful insight.
2. Food diary: breakfast, snacks, lunch and dinner; manual entry, search, text/local-AI draft and saved meals. Photo AI is a later on-device vision milestone.
3. Food catalog: per-100g calories, protein, carbs, fat and fiber; raw/cooked state and source.
4. Weight: dated entries, weekly averages and trend charts.
5. Measurements: neck, shoulders, chest, waist, abdomen, hips, arms, thighs and calves.
6. Progress photos: private app storage and dated comparisons; never treat photos as precise body-fat measurements.
7. Local AI coach: reviewed food estimates, weekly summaries and trend explanations.
8. Water and activity logs.
9. Reports and encrypted backup/restore.
10. **Weekly Planner & Smart Coach** (priority feature; detailed requirements below).

## Weekly Planner & Smart Coach

### Planning experience
- The default planning flow is AI-first: generate a proposed plan for the upcoming seven days (Saturday–Friday by default, with configurable week start).
- Collect the profile and preference inputs needed to create a safe, realistic plan: waking/sleep times, meal schedule, food preferences and exclusions, available foods, activity preferences, water preferences, and user-approved nutrition goals.
- Propose each day's meal schedule, meal suggestions and portions, hydration reminders/targets, walking or activity sessions, and sleep/wind-down reminders where configured.
- Support different schedules on different days; never assume every day is identical.
- Show the entire week for review. The user can edit meals, portions and times, regenerate a day or the whole week, and approve the plan.
- Never activate a generated plan or schedule notifications before explicit user approval.
- Keep manual editing available even though AI generation is the selected default mode.
- If no local model is installed or the device is unsupported, explain this and offer manual planning; never call a remote AI.

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
- Planner, approved schedule, daily checklist, local data, notifications and local AI inference work without network access after the optional model download.
- Persist plans and reminders locally (Room); use Android notification channels and scheduled background work, with rescheduling after reboot/time-zone changes.
- Request notification permission at the appropriate time on supported Android versions.
- Exact-time alarms may require additional Android permission and remain subject to OS battery/Doze restrictions. Explain this clearly; do not promise guaranteed delivery to the minute.
- Respect quiet hours, notification toggles, time zone, and user edits. Avoid duplicate reminders when a plan is regenerated or rescheduled.

## Product principles
- Never save AI food estimates without user confirmation.
- Show uncertainty and allow editing serving size and ingredients.
- No diagnosis, treatment advice, shame language or unsafe restriction.
- Health data and AI processing remain on-device. Optional cloud backup is a separate user-controlled feature and must not include AI inference.
