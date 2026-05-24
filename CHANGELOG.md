# Changelog

All notable changes to Athar will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

- Nothing yet.

## [0.1.0-beta.2] — 2026-05-25

### Added

- **User-defined bank templates** — Settings → "قوالب البنوك" lets the user teach Athar new SMS formats from inside the app. Paste a sample, name the bank, point to the words that surround the amount/merchant/recipient, save. Stored locally in the encrypted Room DB (new `user_template` table, migration `2 → 3`). The parser observes the template list as a Flow and rebuilds itself live — no app restart. Each saved template's anchor strings, sender, and transaction type appear at the top of the screen with a delete button.
- **Multi-bank parser expansion** — `PoS purchase / Amount:X SAR / Card:Y / At: MERCHANT` Al-Rajhi format, internal-transfer Al-Rajhi format, STC Pay (outgoing/incoming/ignore), Alinma, D360, Barq, Riyad Bank, SNB, ANB. Plus a universal multi-currency / multi-language fallback (SAR/AED/USD/EUR/GBP/INR/PKR/TRY/EGP/KWD/QAR/BHD/OMR/JOD; Arabic/English/Spanish/French/Turkish/Urdu/Hindi). Messages from the screenshot's "552 failed" pile now parse cleanly.
- **Approach-limit warnings on Plan** — each budget row computes a `LimitState` from `actual ÷ target` (70% Watch / 90% Tight / >100% Over). A new strip near the top of Plan reads "X, Y, Z · اقتربت من الحد" calling out categories nearing their cap. The variance pill switches between olive/dust/ember accordingly.
- **`-Pathar.seed=true` build flag** — wires a `seeded/` source set + BuildConfig boolean for a private personal build seeded with the user's TMOAP workbook data (Categories / Expenses / Income / Budget Targets / Wishlist / Family Investments). Extracted via `scripts/extract_tmoap.py`. The seeded source set is gitignored — never ships to a public build.
- **ADR-005** — three-layer parser architecture (bank-specific templates → user-defined templates → universal heuristic). Documents the future on-device ML option (MobileBERT-NER INT8) without committing to it.

### Fixed

- **Number regex truncating long amounts** — the parser's number regex was matching `135` from `1350` because the first alternation didn't require a thousands separator. Fixed to `\d{1,3}(?:[ ,]\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?` — first alternation now requires at least one separator, so plain digit-runs fall to the second alternation and stay whole.
- **Plan → Wishlist crash** — `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints` because `LazyColumn` was nested inside a `Column.verticalScroll`. Replaced with `Column { state.items.forEach }` since the count is small and the outer scroll already handles overflow.

## [0.1.0-beta.1] — 2026-05-24

### Fixed

- **Launch crash on personalFullSms release build** — release APKs default to `extractNativeLibs="false"`, which prevented SQLCipher's `libsqlcipher.so` from loading and killed the process during DI graph construction. Forced `extractNativeLibs="true"` in the manifest, wrapped Room/SQLCipher initialization in a `runCatching { … }.onFailure { write crash.log }` block, and planted Timber + a global `Thread.UncaughtExceptionHandler` in **all** builds (not just debug) so any future on-device crash is recoverable via `adb shell run-as com.athar.personal cat files/crash.log` or shared from the file picker.

### Added

- **Settings → About card** — credits Waleed Alhamed (walhamed.com) as developer, names TMOAP (The Measure of a Plan) as the spreadsheet that inspired Athar's Plan screen, and surfaces the build's `versionName`.
- **README credits section** — same attribution made public.

## [0.1.0-beta] — 2026-05-24

The first build that's actually usable as a daily driver. Built across 24 disciplined waves of spec → code → test → verify.

### Added

- **Foundations** — 15 Gradle modules with convention plugins, version catalog, included `build-logic` build. Multi-flavor: `personalFullSms` (sideload) + `storeSafe` (Play-Store-eligible).
- **Today screen** — landmark net-flow number with count-up animation, pending tray, FAB add manual transaction, recent feed with list animations.
- **Trends screen** — period selector (month/3m/year), comparison-to-previous arrow + delta %, top-8 category bar chart, **tap-to-drilldown 12-month sheet** for any category.
- **Plan screen** — segmented sub-tabs: Budget (target/actual/variance pills), Wishlist (savings-capacity math + NOW/WAIT/INFEASIBLE status), Family Investments (proportional share % + return).
- **Onboarding** — 3-page flow (welcome → privacy → SMS perm) with dot-indicator nav.
- **SMS ingestion** — BroadcastReceiver + NotificationListenerService, source-agnostic `RawIngestEvent` pipeline, 90-day historical backfill, idempotent re-delivery.
- **Al Rajhi parser** — Arabic + English templates for purchase / transfer-out / deposit / balance-alert, Arabic-Indic digit normalization, confidence scoring.
- **Categorizer** — 5-tier rule engine (exact → substring → regex → classifier → unknown), 45 Saudi merchant seed rules, learn-from-correction loop with priority-200 user rules.
- **Encryption at rest** — SQLCipher 4.6.1 with 256-bit DB key wrapped by an `AndroidKeyStore`-resident AES-256-GCM master key (ADR-003).
- **Backup + restore** — AES-256-GCM file format, PBKDF2-HMAC-SHA256 600k-iteration KDF, JSON snapshot of all tables, gzip-compressed.
- **CSV import + export** — RFC-4180-quoting parser, category matching by Arabic or English name, multi-format date parsing.
- **Settings** — SMS permission card, backfill trigger, encrypted backup buttons, CSV exchange, Hijri date toggle, categories CRUD (rename / archive / reorder), SMS audit log, Activity log.
- **SMS audit log** — every parsed/failed/ignored SMS retained forever, filterable, never deleted (§4.6 Master Brief promise).
- **Activity log** — every transaction CREATE/UPDATE/DELETE/CONFIRM/DISMISS auto-recorded with timestamp.
- **Hijri date toggle** — landmark caption gains "· 1447/11 هـ" alongside Gregorian when enabled.
- **Brand** — Athar wordmark adaptive icon, Thmanyah typeface (sans + serif-display + serif-text), brand gold accent (#B8893C) replacing the placeholder ember.
- **Tests** — 53 pure-JVM unit tests across `MoneyTest`, `PeriodShiftTest`, `HijriDateTest`, `AtharCryptoTest`, `NormalizeTest`, `AlRajhiTemplatesTest`, `RuleEngineTest`, `WishlistCalcTest`, `InvestmentsCalcTest`, `BudgetCalcTest`.
- **E2E** — 10 Maestro flows covering onboarding, add-tx, edit-with-learn, set-target, add-wishlist, toggle-Hijri, sms-to-pending, backfill, export, smoke-nav.
- **Documentation** — `README.md`, `GETTING_STARTED.md`, `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, four ADRs.

### Known limitations

See [ADR-004](docs/adr/ADR-004-mvp-status.md). Notably:

- XLSX direct import deferred (CSV path is the migration route today).
- TFLite merchant classifier deferred (rule engine covers ~90% of cases).
- Locale toggle deferred (Arabic-first per brief).
- Paparazzi snapshot baselines need a first record run.
- Macrobenchmarks need a real device.

[Unreleased]: https://github.com/wa1939/athar/compare/v0.1.0-beta.1...HEAD
[0.1.0-beta.1]: https://github.com/wa1939/athar/releases/tag/v0.1.0-beta.1
[0.1.0-beta]: https://github.com/wa1939/athar/releases/tag/v0.1.0-beta
