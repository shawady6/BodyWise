# Roadmap

## Phase 0 — Foundation
- [x] Android Compose project and navigation shell
- [x] Light/dark theme foundation
- [x] GitHub Actions debug APK build
- [x] First CI build and v0.1.0 APK release

## Phase 1 — Personal profile and tracking
- [ ] Profile, height, age and goals
- [ ] Weight logs with dates and weekly averages
- [ ] Body measurements and history

## Phase 2 — Weekly Planner foundation (priority)
- [ ] Room entities/DAO/repository for weekly plans, plan days, scheduled items and notification preferences
- [ ] Profile/preferences intake needed for safe AI-generated weekly suggestions
- [ ] Seven-day planner UI with day-by-day meals, portions, hydration, activity and sleep schedule
- [ ] Edit, regenerate day/week, approve and activate workflow
- [ ] Daily checklist that distinguishes planned items from completed logs
- [ ] Notification channels and ALL-category defaults: meals, water, walking/activity, sleep
- [ ] Snooze, done, skip and open/edit notification actions
- [ ] Schedule/cancel/reschedule reminders without duplicates; handle reboot and time-zone changes
- [ ] Notification permission and exact-alarm capability handling with clear fallback behavior
- [ ] Offline manual plan editing and local reminder scheduling
- [ ] Tests for plan persistence, approval gating, schedule changes and notification scheduling

## Phase 3 — Local AI runtime (no cloud AI)
- [ ] Add LocalAiEngine abstraction and device capability checks
- [ ] Integrate Google AI Edge LiteRT-LM Kotlin runtime (preferred current Android LLM runtime)
- [ ] Build model manager: trusted source, model metadata/license, download progress, Wi-Fi preference, SHA-256 verification, private storage, delete/update
- [ ] Benchmark compact quantized candidate models on real target phones for RAM, latency, thermal behavior, storage and Arabic/Egyptian Arabic quality
- [ ] Select default model only after benchmark and license review
- [ ] Structured JSON generation for weekly plans with strict parsing and deterministic validation
- [ ] CPU fallback and graceful manual-only mode if model/runtime/device is unsupported
- [ ] Ensure no network calls occur during inference; add tests/inspection for cloud-free operation
- [ ] Add local AI coach for missed-item suggestions and weekly summaries
- [ ] Later: evaluate a separate on-device vision model for food-photo analysis

## Phase 4 — Nutrition
- [ ] Food database with Egyptian foods
- [ ] Meal diary and serving sizes
- [ ] Calories, macros and fiber totals
- [ ] Saved meals and recipes
- [ ] Marking a planned meal done creates an editable diary draft; save only after user confirmation
- [ ] Deterministic nutrition calculations; never trust LLM arithmetic

## Phase 5 — Progress and privacy
- [ ] Private progress photo timeline
- [ ] Water and activity tracking
- [ ] Sleep schedule and reminders
- [ ] Export reports
- [ ] Encrypted local backup and optional user-controlled cloud backup (never AI inference)

A feature is not complete until it persists data, validates input, handles empty/error states and has tests. Scheduled notifications are best-effort under Android OS/battery restrictions; exact alarms require capability checks and must not be promised unconditionally. Local model inference must never silently fall back to a remote service.
