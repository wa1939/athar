<p align="center">
  <img src="docs/branding/athar-mark.png" alt="Athar" width="200" />
</p>

<h1 align="center">أثر · Athar</h1>

<p align="center">
  <strong>The free, local-first replacement for "The Measure of a Plan" (TMOAP) budget Excel.</strong><br/>
  <em>Trace every riyal — or dollar, dirham, rupee, pound — you spend. Quietly. On your device. Without spreadsheets.</em><br/>
  <em>تتبع أموالك. بوضوح.</em>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-blue.svg" alt="License"></a>
  <img src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/min%20SDK-28-3DDC84" alt="Min SDK 28">
  <img src="https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.1.0">
  <img src="https://img.shields.io/badge/Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose Material 3">
  <a href=".github/workflows/build.yml"><img src="https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white" alt="CI"></a>
  <img src="https://img.shields.io/badge/status-beta-orange" alt="status">
</p>

<p align="center">
  <a href="#how-it-works">How it works</a> ·
  <a href="#features">Features</a> ·
  <a href="#screenshots">Screenshots</a> ·
  <a href="#building">Building</a> ·
  <a href="#roadmap">Roadmap</a> ·
  <a href="#privacy">Privacy</a>
</p>

---

> **أثر · Athar** is a local-first Android app — an open-source, privacy-first replacement for the popular **"The Measure of a Plan" (TMOAP) personal finance Excel workbook**. It reads your bank SMS, parses every transaction, auto-categorizes against a 195-entry merchant dictionary, and replaces a complex Excel budget workbook with three calm screens: **Today** · **Trends** · **Plan**. Arabic + English. Multi-currency (18 codes). Encrypted on-device. No cloud. No ads. No telemetry. Ever.

## Why · Replacing TMOAP

**The Measure of a Plan (TMOAP)** is a beloved personal-finance Excel workbook used by thousands worldwide to track monthly cash flow, savings rate, wishlists, and historical comparisons. It is rigorous, beautiful, and entirely manual — every transaction is typed in by hand.

Athar carries TMOAP's discipline (monthly cash flow → savings capacity → wishlist gating, historical period-vs-prior comparison, dashboard charts) into a phone that lives in your pocket and reads your bank SMS automatically. You get TMOAP-grade analysis without TMOAP's manual data-entry tax.

**Search keywords:** TMOAP replacement · The Measure of a Plan Excel alternative · open-source budget tracking app · personal finance spreadsheet replacement · monthly cash flow tracker · savings rate calculator · expense tracker without cloud · Excel budget Android · SMS budget app · privacy-first finance · multi-currency budget app · zero-knowledge expense tracker · Arabic budget app · Saudi budget tracker.

Other tools force a trade-off:
- A spreadsheet (TMOAP, YNAB-on-sheet, custom Google Sheets) that knows your categories but requires manual entry for every transaction
- A SaaS app (Mint, YNAB, Monarch, Copilot, Money Lover) that ingests transactions but sends your financial graph to a third-party server, runs ads, doesn't speak Saudi/Gulf banking, or doesn't work without a cloud account

**Athar bridges that gap**, locally and quietly. Your data never leaves the device unless you export it yourself.

## Features

#### Today (اليوم)
- ✅ One landmark net-flow number with **count-up animation**
- ✅ **Income + Expense pills** (olive / ember) — see your monthly inflow and outflow at a glance
- ✅ **Savings-rate caption** (e.g., "معدّل الادخار · 18٪") — the TMOAP discipline, on the home screen
- ✅ Pending tray for SMS-captured transactions awaiting your confirmation
- ✅ Swipe right to confirm · swipe left to dismiss · **bulk actions** for 5+ pending entries
- ✅ Auto-confirm transactions when category is already known; auto-dismiss low-confidence noise
- ✅ FAB to add a manual transaction in seconds

#### Trends (النمط) — TMOAP-depth analysis
- ✅ Period selector: **month · 3 months · year · period-vs-prior · custom range**
- ✅ **Headline numbers card** with delta-vs-prior % and expense-as-%-of-income
- ✅ **Income · Expenses · Savings** breakdown with savings-rate %
- ✅ **Monthly Dashboard** — 3 panels (income / expense / savings) over the last 12 months, each with **budget-target line + computed average line** overlays (TMOAP "Income by Month / Expenses by Month / Savings by Month" parity)
- ✅ **Period-vs-prior comparison bars** for income/expense/savings (TMOAP "Historical Comparison" sheet parity)
- ✅ **Category comparison table** with delta in money + delta % per category
- ✅ **Proportional category split** horizontal stacked bar (pie-chart replacement — pies banned per design brief)
- ✅ **Tap a bar → 12-month drilldown** for that category, with monthly target overlay
- ✅ Top-8 category bar chart
- ✅ Hijri date display optional

#### Plan (الخطة)
- ✅ **Budget targets** per category with variance pills (over/under)
- ✅ **Editable monthly targets** — tap any row, type the number, save
- ✅ **Wishlist** with savings-capacity math (NOW / WAIT until YYYY-MM / INFEASIBLE)
- ✅ **Family investments pool** with **percentage-based return entry**, proportional share %, delete pool/contributor
- ✅ **Recurring transactions** — define rent / salary / Netflix / utilities once; rules materialize into PENDING transactions on their due date

#### Ingestion + categorization (Saudi-first, universal-ready)
- ✅ **Al Rajhi SMS** parser (Arabic + English) — purchase, transfer, deposit, profit deposit, loan instalment, credit card payment, declined transactions, transfers between own accounts
- ✅ **STC Bank · Alinma · D360 · Barq** templates derived from real corpus, with date extraction so historical SMS sit in their correct months
- ✅ **User-defined templates** — paste a sample SMS from your bank, mark the anchor strings around the amount/merchant, save (W-4)
- ✅ **Spam-resistant pipeline** — `-AD` suffix block (CITC convention), sender allow-list, ~40 ignore patterns for OTP/promo/marketing (Tasaheal, "Buy X Get Y", "Earn 10,000")
- ✅ **Notification listener** path for Play-Store-safe distribution
- ✅ **Multi-currency capture** — foreign-card spend keeps both the original amount and the SAR equivalent
- ✅ **Self-transfer detection** — moves to your own savings account flagged as "Own account move" (savings), not expense
- ✅ **Auto-detected recurring patterns** — scans your confirmed history, surfaces "Same merchant, same amount, 3+ distinct months at similar day-of-month" candidates as suggestions you confirm with one tap
- ✅ **90-day historical SMS backfill** on first install
- ✅ Rule engine with **195+ merchant seed rules** (Starbucks · Panda · STC · Saudia · McDonald's · Albaik · Herfy · Othaim · Nahdi · Amazon · Netflix · …)
- ✅ "Always categorize X as Y?" **learn-from-correction** loop
- ✅ Source of every categorization is **explainable** (rule id, confidence)

#### Settings + ops
- ✅ **Multi-currency display** — pick from 18 ISO-4217 codes (USD · EUR · GBP · AED · EGP · INR · PKR · TRY · SAR · KWD · QAR · BHD · OMR · JOD · CAD · AUD · CHF · JPY) with Arabic + English currency labels
- ✅ **AES-256-GCM encrypted backup** (Argon2-equivalent KDF, passphrase-protected)
- ✅ **CSV import + export** for Excel interop — drop in your TMOAP transaction log to import; export annual data for your accountant
- ✅ **SQLCipher** database encryption at rest, key wrapped via Android Keystore
- ✅ **SMS audit log** — every parsed/failed/ignored SMS retained, never deleted
- ✅ **Activity log** — every transaction edit, with timestamp
- ✅ **Rescan + clean** button — wipe pending tray, re-run backfill with latest templates
- ✅ **Own-account list** — register the last-4 of your accounts so internal transfers are flagged as savings moves
- ✅ **Hijri date toggle**
- ✅ **Recurring rules management** with "Run now" trigger + auto-detected suggestions
- ✅ Categories management (rename, archive, reorder, custom adds)

## Screenshots

Captured from the `v0.1.0-beta.1` signed APK running on a Pixel 6 / Android 14 emulator.

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/01_onboarding.png" width="220"/><br/><sub>Onboarding · welcome</sub></td>
    <td align="center"><img src="docs/screenshots/02_onboarding2.png" width="220"/><br/><sub>Onboarding · privacy</sub></td>
    <td align="center"><img src="docs/screenshots/03_onboarding3.png" width="220"/><br/><sub>Onboarding · SMS perm</sub></td>
    <td align="center"><img src="docs/screenshots/04_today.png" width="220"/><br/><sub>Today · empty state</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/05_trends.png" width="220"/><br/><sub>Trends · empty</sub></td>
    <td align="center"><img src="docs/screenshots/06_plan_budget.png" width="220"/><br/><sub>Plan · budget targets</sub></td>
    <td align="center"><img src="docs/screenshots/07_settings.png" width="220"/><br/><sub>Settings</sub></td>
    <td align="center"><img src="docs/screenshots/09_settings_about2.png" width="220"/><br/><sub>About · credits</sub></td>
  </tr>
</table>

## How it works

```
┌────────────────────────────────────────────────────────────────────┐
│  SMS broadcast      Notification listener      Manual entry        │
│       │                       │                      │              │
│       └─────────┬─────────────┴──────────┬──────────┘              │
│                 ▼                        ▼                          │
│           RawIngestEvent           Transaction (CONFIRMED)          │
│                 │                        │                          │
│                 ▼                        │                          │
│    SmsIngestionPipeline                  │                          │
│      ┌─ audit log (PARSED/FAILED/IGNORED)│                          │
│      ├─ parser (Al Rajhi templates)      │                          │
│      ├─ categorizer (rules → classifier) │                          │
│      └─ persist (PENDING)                │                          │
│                 ▼                        ▼                          │
│         ╔══════════════════════════════════════╗                    │
│         ║  Encrypted Room DB (SQLCipher)       ║                    │
│         ║  key wrapped via AndroidKeyStore     ║                    │
│         ╚══════════════════════════════════════╝                    │
│                 │                                                   │
│        ┌────────┼────────┬──────────┐                              │
│        ▼        ▼        ▼          ▼                              │
│      Today    Trends   Plan       Settings                          │
└────────────────────────────────────────────────────────────────────┘
```

The architecture intentionally separates each ingestion source from the parse/categorize/persist pipeline so the distribution flavor (sideload vs Play-Store-eligible) is a config change, not a rewrite.

See [`docs/adr/`](docs/adr/) for the decision records:
- **ADR-001** — Stack: Kotlin 2.1 + Compose Material 3 + Hilt + Room + multi-module
- **ADR-002** — Local-first: no backend, no telemetry, ever
- **ADR-003** — Encryption at rest: SQLCipher + Keystore-wrapped DB key
- **ADR-004** — MVP status after 24 build waves

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.1.0 |
| UI | Jetpack Compose · Material 3 |
| DI | Hilt 2.54 |
| Persistence | Room 2.6 · **SQLCipher 4.6** (AES-256-GCM, AndroidKeyStore-wrapped key) |
| Async | Coroutines + Flow |
| Navigation | Compose Navigation 2.8 (single-activity, type-safe routes) |
| Charts | Custom Compose primitives (horizontal bar + monthly vertical bar) |
| Date/time | kotlinx-datetime · `java.time.chrono.HijrahDate` for Hijri |
| Money | `java.math.BigDecimal` — never `Float`/`Double` |
| Build | Gradle 8.10 · convention plugins via `build-logic/` included build · version catalog |
| Tests | JUnit 5 · Truth · Turbine · Paparazzi (scaffolded) · Maestro (10 flows) |
| Typography | [Thmanyah typeface](https://github.com/thmanyah/Thmanyah-Type) — sans + serif-display + serif-text |
| Logging | Timber (debug); no Crashlytics (privacy commitment) |

## Building

```bash
# 1. JDK 17 + Android SDK
#    Either install Android Studio (bundles both), or:
#      JDK:  https://adoptium.net/  (Temurin 17)
#      SDK:  Android command-line tools, then `sdkmanager "platforms;android-35" "build-tools;35.0.1"`

# 2. Point the build at your SDK
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 3. Bootstrap the Gradle wrapper (only first time)
gradle wrapper --gradle-version 8.10.2

# 4. Build
./gradlew :app:assemblePersonalFullSmsDebug

# 5. Install on a connected device
./gradlew :app:installPersonalFullSmsDebug
# or:
adb install app/build/outputs/apk/personalFullSms/debug/app-personalFullSms-debug.apk
```

Two flavors:

| Flavor | Permissions | Distribution path |
|---|---|---|
| **personalFullSms** | RECEIVE_SMS, READ_SMS | Sideload only — your own daily-driver build |
| **storeSafe** | No SMS perms | Play-Store-eligible — uses NotificationListenerService, manual entry, CSV import |

## Tests

```bash
./gradlew test                                   # all JVM unit tests (53 tests across 10 classes)
./gradlew :ml:categorizer:test                   # one module
./gradlew connectedAndroidTest                   # instrumented (needs emulator/device)
maestro test .maestro/flows/                     # 10 E2E flows
```

## Roadmap

### Shipped in v0.1.0-beta.1 → beta.8 (current)

**Foundations (beta.1):**
- ✅ Phase 0 foundations · 15 Gradle modules · build-logic convention plugins
- ✅ Excel parity for manual entry (M-01..M-13)
- ✅ Pending tray with swipe gestures (S-17..S-19)
- ✅ Hijri date toggle (P-07)
- ✅ SQLCipher encryption-at-rest with Keystore-wrapped key (ADR-003)
- ✅ AES-256-GCM JSON backup + restore · CSV import + export
- ✅ Wishlist + Family Investments (Phase 4)
- ✅ Trends drilldown chart (M-07)
- ✅ SMS audit + Activity log (S-21, S-23, P-08)
- ✅ Al Rajhi SMS parsing + categorization (S-01..S-09)

**Bank coverage + sustainability (beta.2 → beta.4):**
- ✅ Al Rajhi real-corpus templates (12 message types: PoS, online, transfers, deposits, profit, loan)
- ✅ STC Bank · Alinma · D360 · Barq templates
- ✅ User-defined SMS templates (W-4) — onboard your bank without code
- ✅ Spam-resistant ingestion pipeline (ADR-006): sender allow-list + `-AD` block + 40 ignore patterns
- ✅ Date extraction from SMS body (7 formats) so historical SMS sit in their correct months
- ✅ Auto-confirm by category · auto-dismiss low confidence · bulk pending actions
- ✅ Self-transfer / own-account detection
- ✅ Investment percentage-based returns + delete pool/contributor
- ✅ TMOAP-seeded private build (gitignored) for the developer's own data — public APK stays clean

**TMOAP-depth analysis (beta.5):**
- ✅ G-9 — Monthly Income/Expense/Savings bar panels with budget-target + average overlay lines
- ✅ Period-vs-prior comparison bars (income/expense/savings)
- ✅ Headline numbers card with savings rate + expense-as-%-of-income
- ✅ Category comparison table with delta money + delta %
- ✅ Proportional category split (horizontal stacked bar)
- ✅ Custom date-range selector on Trends

**Global sprint Tier 1 (beta.6 → beta.8):**
- ✅ **G-1** — Multi-currency display (18 ISO-4217 codes with Arabic + English labels, currency picker in Settings)
- ✅ **G-2** — Income visibility (income + expense pills + savings-rate on Today header)
- ✅ **G-3** — Recurring transactions (rent / salary / Netflix / utilities — manual rules + auto-detected suggestions from history)
- ✅ **G-9** — TMOAP-depth Trends

**Global sprint Tier 1 continued (beta.9):**
- ✅ **G-4** — Multi-account + net worth view: Room v4→v5 migration adds 6 columns to the account table (openingBalance, sortOrder, archivedAt, notes, updatedAt); legacy `DEBIT/CREDIT` enum remapped to `CHECKING/SAVINGS/CREDIT_CARD/CASH/INVESTMENT/OTHER`. New `AccountRepositoryImpl.observeNetWorth()` aggregates `openingBalance + Σ(CONFIRMED transactions)` per account, returns the live net-worth flow used by the Today header pill and the new Accounts screen (CRUD + archive + edit + per-currency breakdown). Manual seed account is now a first-class CASH account, non-deletable but renameable/archivable.
- ✅ **G-6** — Custom date-range selector on Plan: Plan now has the same period picker as Trends (month / 3-month / year / custom). Monthly targets scale by average month-length of the selected range, with a caption (×N multiplier) so budget-vs-actual stays apples-to-apples.
- ⚠️ **G-5** — Localization (partial): the locale picker UI, DataStore persistence, English `values-en/strings.xml` baseline, and `MainActivity.attachBaseContext` Configuration override are all in place. User can pick System / Arabic / English from Settings and the choice persists across launches. **Caveat:** ~99% of user-facing copy is still hardcoded Arabic in Compose `AtharText(text = "…")` calls — switching to English persists correctly and the activity recreates, but the visible UI keeps Arabic until those strings migrate to `stringResource()`. The migration is mechanical (no architecture change needed); G-5 follow-up will batch-extract feature-by-feature.

### In flight / remaining

| ID | Feature | Status | Effort | Notes |
|---|---|---|---|---|
| G-5 | Localization — full string extraction | ⏳ | 2 days | Infrastructure shipped; remaining work is mechanical extraction of ~166 hardcoded Arabic strings → `stringResource()` + matching `values-en/` entries, feature module by feature module |
| G-7 | Bills calendar | ⏳ | 2 days | "Upcoming bills" view + push notifications 2 days before each bill |
| G-8 | Manual transaction UX upgrades | ⏳ | 2 days | Recent-merchant autocomplete · quick-add chips · voice entry · receipt photo |
| G-10 | Savings-rate goals + emergency fund | ⏳ | 2 days | Plan → Goals tab with target progress |
| G-11 | Broader notification handlers | ⏳ | 3 days | Apple Wallet, Google Pay, Revolut, Wise, Chase, Capital One, Mercury (Play-Store flavor) |
| G-12 | Bank statement / CSV / OFX / QFX import wizard | ⏳ | 3 days | Auto-detect format, column mapping, multi-currency statements |
| G-13 | Zero-knowledge sync to companion devices | ⏳ | 5 days | E2E-encrypted via Dropbox / Drive / iCloud / WebDAV / S3 — user holds the key |
| G-14 | Tax-export PDF for accountants | ⏳ | 2 days | Annual category totals + transaction list in user's locale |

Full gap analysis: [`docs/ROADMAP_GLOBAL.md`](docs/ROADMAP_GLOBAL.md).

### Deferred (technical-debt only)

- ⏳ TFLite merchant classifier (rule engine handles ~90% of cases)
- ⏳ Paparazzi snapshot baselines (need to record on a real machine)
- ⏳ Macrobenchmarks (need a device)
- ⏳ Per-bank account routing (S-20)
- ⏳ WorkManager auto-trigger for recurring rules (manual "Run now" works today)
- ⏳ XLSX direct import (CSV path covers the migration today)

## Privacy

Athar makes three commitments (Master Brief §2.2, enforced in ADR-002 + ADR-003):

1. **No cloud transit of financial data without your explicit action.** The only data that leaves the device is the AES-256-GCM backup file, which you save where you want.
2. **No ads, partnerships, affiliate links, telemetry, or third-party crash reporting.**
3. **No friction that makes you feel bad.** No streaks, no shame notifications, no scream red bars.

The DB is encrypted at rest with a 256-bit key resident in `AndroidKeyStore`. Without the device's secure hardware, the key is undecryptable. See [`SECURITY.md`](SECURITY.md).

## Contributing

PRs welcome. See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the workflow and the spec-first loop the codebase was built with. The single source of truth is [`Athar_Master_Brief.md`](Athar_Master_Brief.md) — please read it before opening a PR that adds a feature.

## License

[Apache License 2.0](LICENSE). Bank-template strings in `ingestion:sms-parser` are derived from observed SMS bodies (anonymized) and are released under the same terms.

## Credits

Developed by **Waleed Alhamed** — [walhamed.com](https://walhamed.com)

Inspired by [**The Measure of a Plan (TMOAP)**](https://themeasureofaplan.com) budget tracking workbook by Tudor Mihailescu, whose monthly cash-flow + historical-comparison + savings-capacity discipline shaped Athar's Trends and Plan screens. Athar carries that spreadsheet's clarity into a phone that lives in your pocket — and removes the manual data-entry tax that TMOAP, by virtue of being a spreadsheet, can never eliminate.

If you were searching for "TMOAP for mobile", "The Measure of a Plan Android app", "open-source TMOAP", "TMOAP without Excel", "automated budget tracker that replaces my spreadsheet", "privacy-first alternative to YNAB / Mint / Monarch / Copilot", or "Saudi / Gulf / Middle East budget app with SMS reading" — Athar is the project you're looking for.

---

<p align="center">
  <sub>Built one disciplined wave at a time. The Excel sheet retires when Athar ships its v1.0.</sub>
</p>
