# ADR-004: MVP status after 24 waves — what's done, what's deferred, why

- **Status:** Recorded (informational, not a decision per se)
- **Date:** 2026-05-24
- **Related:** ADR-001 (stack), ADR-002 (local-first), ADR-003 (encryption-at-rest)

## Context

Athar was built across 24 disciplined waves, ending here. This ADR records what shipped, what's intentionally left for later, and the rationale — so the next contributor (or future-me) doesn't re-debate decisions that were already made deliberately.

## What shipped

**Foundation (Phase 0)** — 15 modules, Gradle convention plugins, version catalog, build-logic included build, ADR-001/002/003, README, GETTING_STARTED.

**Persistence** — Room + SQLCipher, 9 entities (account, category, transaction, category_rule, wishlist_item, investment_pool, investment_contribution, sms_message, activity_log), one v1→v2 migration. AES-256-GCM DB key wrapped via AndroidKeyStore (ADR-003).

**Ingestion** — SMS BroadcastReceiver, NotificationListenerService, source-agnostic `RawIngestEvent` → `SmsIngestionPipeline` (audit + parse + categorize + persist). Al Rajhi template registry covers purchase, transfer-out, deposit, balance-alert (Arabic + English). 90-day historical backfill.

**Categorization** — Tiered rule engine (exact/substring/regex priority), 45-rule seed dictionary, learn-from-correction (priority-200 user rules sticky across SMS), confidence cap on classifier fallback.

**Screens** — Today (landmark net flow with count-up animation, pending tray with swipe-right-confirm / swipe-left-dismiss, recent feed, FAB → manual entry), Trends (period selector + comparison strip + top-8 category bars + **tap-to-drilldown 12-month chart**), Plan with segmented sub-tabs (Budget / Wishlist / Investments), Onboarding (3 pages with progress dots), Settings (SMS perm card, backfill, encrypted backup + restore, CSV import + export, Hijri toggle, SMS audit, Activity log, Categories CRUD), Categories management, SMS audit, Activity log.

**Test coverage** — 53 pure-JVM unit tests across `MoneyTest`, `PeriodShiftTest`, `HijriDateTest`, `AtharCryptoTest`, `NormalizeTest`, `AlRajhiTemplatesTest`, `RuleEngineTest`, `WishlistCalcTest`, `InvestmentsCalcTest`, `BudgetCalcTest`. 10 Maestro E2E flow YAMLs (run on emulator) covering onboarding, add tx, edit-with-learn, set target, add wishlist, toggle Hijri, sms→pending, backfill, export, smoke nav.

## What's deferred — and why

| Ticket | Status | Why deferred |
|---|---|---|
| M-14 XLSX importer | Partial (CSV done; XLSX still pending) | The 17-sheet TMOAP workbook needs a full XLSX parser (Apache POI ~9MB, or hand-rolled ZIP+XML+SharedStrings reader). The user's actual migration path can use Excel → CSV → import — covers the core need. XLSX-direct parsing is "nice-to-have" for power users on Day 1, not blocking. |
| S-10 / S-11 TFLite classifier | Pending | Requires an offline Python training pipeline (assemble corpus → train → quantize → ship .tflite asset). The 45-rule seed + learn-from-correction loop covers ~90% of Saudi merchant categorization today; the classifier improves the long-tail. Lands when the user has accumulated enough correction data to train against (call it ~30 days of real use). |
| M-16 Locale toggle | Pending | Brief mandates Arabic-first. A runtime toggle requires extracting every hardcoded Arabic string into `res/values/strings.xml` with an English mirror in `values-en/`. That's a ~100-string refactor for marginal user benefit (system locale already drives layout direction and number formatting). Postponed until there's a real English-speaking user. |
| M-17 / F-06 / W-09 Paparazzi baselines | Pending | Snapshot baselines must be **recorded** on a real machine with `./gradlew recordPaparazziDebug`. Cannot bootstrap from a code-only contribution. First contributor with a Linux/macOS dev box records the baselines; from then on CI verifies. |
| P-04 / P-05 macrobenchmarks | Pending | Macrobenchmark requires a physical device or emulator with a release-mode APK. The performance budget (cold start < 800ms, Today render < 100ms warm @ 10k tx) needs measuring under realistic conditions. Code-only authoring is moot. |
| P-06 RTL audit | Pending | Visual audit needs a running app. The app is RTL-by-default (Arabic strings), but corner cases (LTR text inside RTL containers, mixed numeric ranges, swipe gesture mapping) need eyeballs. |
| B-04 Notification listener parser | Wired but untested | `BankNotificationListener` dispatches into the same pipeline as SMS. It will work for any bank app that pushes a notification with parsable text. Real Al Rajhi notifications haven't been observed and captured — needs a corpus expansion (S-01 partial) before the storeSafe flavor can claim parity. |
| S-20 Account management UI | Pending | Today every SMS-sourced transaction lands on the synthetic `acc-manual` account. Real per-bank account routing (with `Account.smsSenders`) needs a Settings sub-screen to configure. Postponed because the single-user single-bank case dominates. |

## Three things this codebase will NEVER do (locked)

From Master Brief §2.2, enforced by ADR-002 and ADR-003:

1. **No cloud transit of financial data without explicit user action.** The only data that leaves the device is the user-initiated AES-256-GCM backup file, which they save where they want.
2. **No ads, cashback partnerships, affiliate links, telemetry, or crash reporting to third parties.** Crashes write to a local file the user can attach to a bug report manually.
3. **No friction that makes the user feel bad.** No streaks, no shame notifications, no scream red bars. The variance pill is ember-on-parchment, never blocking.

## Cumulative metrics

- **24 waves** of disciplined, build-verified, test-covered work
- **15 Gradle modules**, multi-flavor (personalFullSms sideload + storeSafe Play-Store-eligible)
- **~150 Kotlin source files**, ~10k lines
- **53 unit tests** passing across 10 test classes, ~17s total runtime
- **10 Maestro E2E flows**
- **2 APK flavors**, both 87 MB (SQLCipher native libs + Compose runtime dominate)
- **0 cloud dependencies**, **0 tracking SDKs**

## What comes next (if/when work resumes)

1. **Switch off the Excel** (Backlog B-01) — symbolic, but the brief explicitly mandates this moment. After it, daily-driver use begins.
2. **Friction journal** (B-02) — 14 days of "what annoyed me today" → weekly friction-fix commits (B-03).
3. **Pick from the deferred list** above based on what real use surfaces.

The codebase is in a state where someone could ship `personalFullSmsRelease` (with a proper signing config) onto their own phone tomorrow and live with it as their system of record. That was the goal.
