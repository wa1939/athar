# Changelog

All notable changes to Athar will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

- Nothing yet.

## [0.1.0-beta.4] — 2026-05-25

The "no more 618-pending-entries" release. Six tightly-coupled fixes addressing the headaches a user with hundreds of historical SMS hits on day one.

### Added

- **Auto-confirm when the category is known.** The ingestion pipeline no longer puts every parsed transaction in the pending tray. If the categorizer assigned a category (i.e. Athar already knows McDonald's is a restaurant), the transaction goes straight to CONFIRMED. Low-confidence parses (< 0.50) are auto-DISMISSED. Self-transfers always go to PENDING so the user labels them as savings vs regular. No more triaging 600 obvious entries.
- **Bulk actions in the pending tray.** When the tray has ≥ 5 items, three buttons appear on Today: «تأكيد المؤكدة» (confirm all with confidence ≥ 0.85), «تجاهل المشكوك فيه» (dismiss everything < 0.70), «تجاهل الكل» (nuke the tray). Backed by new repository methods `confirmAllConfident`, `dismissAllLowConfidence`, `dismissAllPending`.
- **Real dates from SMS bodies.** Every bank's `Date:` / `On:` / `at:` line is now parsed (`DateExtraction.parseSmsDate`) — supports all seven shapes in the corpus (`YYYY-MM-DD HH:MM`, `DD-MM-YYYY HH:MM`, `YY-MM-DD HH:MM`, `DD/MM/YYYY HH:MM`, `DD/MM/YY HH:MM`, `YY-M-D`, `D\M\YY HH:MM`). Historical SMS now get historical dates instead of all landing as "today" — Today screen totals are real again.
- **Self-transfer detection (savings moves).** Settings → "حساباتك الخاصة" lets the user list their own account-number tails (`0930, 4268`). Any parsed transfer touching one of those numbers is tagged "تحويل داخلي · ادخار محتمل" with a confirm-this-is-savings prompt in the pending tray, instead of being filed as a generic outgoing payment that skews the budget.
- **Editable budget targets on Plan.** Tap any budget row → bottom sheet → enter monthly target → save. The infrastructure was already wired; the row click now opens the editor.
- **Investment delete + percentage-based return.** Each pool has a «حذف المجموعة» button with confirmation; each contributor row has a `✕` to remove. Tapping the total-return line opens a bottom sheet to enter return as a percentage of corpus instead of an absolute SAR amount (Athar computes `corpus × pct/100`).
- **Trends — TMOAP-equivalent analysis.** New `IncomeExpenseSavingsCard` shows income/expenses/savings side-by-side with a savings-rate %; new `CategoryComparisonTable` (visible when the user selects «مقارنة») lists every category with this-period vs last-period totals plus the SAR delta and % change, mirroring TMOAP's Historical Comparison sheet. Added a 4th period segment «مقارنة».
- **Settings → "إعادة فحص الرسائل" / "Re-scan messages"** — one-tap recovery from polluted pending trays. Wipes every PENDING transaction and re-runs the SMS backfill with the latest templates. Confirmed transactions are untouched.

## [0.1.0-beta.3] — 2026-05-25

### Fixed

- **Promotional SMS from unknown senders were being ingested as transactions.** The earlier `UniversalAmountTemplate` matched ANY sender via `Regex(".+")`, so a marketing shortcode shouting "Earn 10,000 SAR cashback!" became a pending transaction. The universal template is now restricted to `KnownBankSenders.builtIn`. Any sender ending in the Saudi-CITC `-AD` suffix (`AlRajhiB-AD`, `eXtra-AD`, `JARIR-AD`, …) is hard-blocked because that suffix is reserved for advertising channels by the regulator. Loyalty programs (`mokafaa`), OTP-only senders (`FoodicsOTP`), and prize-contest senders (`stcplay-AD`) are explicitly in `hardBlocked`.

### Added

- **Real-format bank templates from a 1,000-message corpus.** Rewrote the Al Rajhi / STC Bank / D360 / Barq parsers against actual SMS exports (`docs/sms-corpus-analysis.md`, `docs/all-senders-analysis.md`). New templates: `Online Purchase`, `PoS purchase`, `Reverse Transaction` (refund), `Debit Internal Transfer`, `Debit Transfer Local` (SARIE), `Credit Transfer Local` (salary inbound), `Bill Payment`, `Deposit: Saving Account Monthly Profit`, `Credit Card:Payment`, `Loan Instalment`, `Transfer Between Your Accounts` for Al Rajhi; `Internal incoming/outward transfer`, `Outward transfer (SARIE)`, `Online Purchase Transaction`, `Pay qattah` for STC Bank; `Online Purchase`, `International Purchase`, local `Purchase`, `Account Funding`, `Incoming Transfer`, `International transfer` for D360; `Online Purchases`, `POS International Purchase`, `ATM Withdrawal`, `Debit Transfer Internal`, `Credit transfer Local` for Barq. Multi-currency handled correctly — the SAR-in-parens value wins.
- **GlobalBankIgnoreTemplate.** Cross-bank content filter for OTP codes, beneficiary additions/activations, scheduled maintenance, card-activation notices, marketing language (`Tasaheal`, `Buy X Get Y`, `Shukrans`, `Earn X cashback`, prize draws, Arabic `جوائز`/`موافقة فورية`/`نقاط مكافأة`/`تطبق الشروط`/`تقسيط`). Anything that matches a pattern returns `Ignored` *before* template parsing runs, so wasted regex work is skipped and the audit log stays clean.
- **Default merchant catalog (`seed_merchant_catalog.json`).** ~200 substring→category mappings covering McDonald's / KFC / Albaik / Herfy / Kudu / Shawarmer / Pizza Hut / Starbucks / Dunkin / Tim Hortons / Roasting House / Barn's / %Arabica / Carrefour / Lulu / Othaim / Tamimi / Panda / Bindawood / Danube / Sarawat / Nahdi / Aldrees / Saso / Petromin / STC / Mobily / Zain / Uber / Careem / DiDi / TfL / Jarir / Extra / Saco / IKEA / Amazon / Noon / Shein / Zara / Uniqlo / H&M / Netflix / Spotify / Apple / iCloud / OpenAI / Anthropic / GitHub / Shahid / Starzplay / Airbnb / Booking.com / Agoda / Flynas / Saudia / Emirates / Saudi Electric / National Water / Tesco / Conad / Penny Market / and ~140 more. Each entry has a confidence so the user sees which auto-classifications are guesses vs certain.
- **ADR-006 (`docs/adr/ADR-006-spam-resistant-ingestion.md`).** Documents the layered filter pipeline (sender allow-list → `-AD` suffix block → content patterns → bank templates → user templates → universal fallback) and the sustainability guarantees for future users so this class of bug doesn't return.
- **Test suite expansion.** 39 new corpus-pinned tests in `SmsCorpusTest.kt` plus 6 sustainability tests (`-AD` suffix blocking, `mokafaa` blocked, OTP-only sender blocked, Tasaheal pitch ignored, Arabic financing offer ignored, prize contest ignored). Total parser tests: 59.

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
