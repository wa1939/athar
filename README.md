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

> **أثر · Athar** is a local-first Android app — an open-source, privacy-first replacement for the popular **"The Measure of a Plan" (TMOAP) personal finance Excel workbook**. It reads your bank SMS, parses every transaction, auto-categorizes against a 706-rule merchant catalog plus local user-learned rules, and replaces a complex Excel budget workbook with three calm screens: **Today** · **Trends** · **Plan**. Arabic + English. Multi-currency (18 codes). Encrypted on-device. No cloud. No ads. No telemetry. Ever.

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
- ✅ **Goals check** — a compact savings-rate target and emergency-fund cue pulled from Plan targets
- ✅ **Today section** at the top of the list filters to transactions where `date == today` so you instantly see what you spent today (with `Today's net · X SAR` subtitle in olive/ember). Below it: "This month's recent" for context.
- ✅ Pending tray for SMS-captured transactions awaiting your confirmation
- ✅ Swipe right to confirm · swipe left to dismiss · **bulk actions** for 5+ pending entries
- ✅ **Ember attention banner** when pending > 0 — "%d transactions awaiting review · Tap to review →" — impossible to miss above the net-flow number. Dust-color secondary banner appears below it when today had any auto-dismissed transactions, so parser false-negatives never go unnoticed.
- ✅ **Fail-safe ingestion (beta.15):** SMS that parse as transactions are NEVER auto-dismissed; if the categorizer is uncertain, the transaction lands in PENDING for explicit user review. Money never silently disappears. See `docs/adr/ADR-008-ingestion-fail-safe.md` for rationale.
- ✅ Auto-confirm transactions when category is already known (CategoryRule match); user fixes are recorded as new rules so the same merchant auto-confirms next time
- ✅ FAB to add a manual transaction in seconds, with recent-merchant quick-add chips, quick phrase/voice fill, and multiple local encrypted receipt attachments that can be viewed/exported later from the edit sheet

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
- ✅ **Goals tab** — savings-rate target and emergency-fund target, computed from confirmed history and liquid accounts
- ✅ **Bills calendar** — Plan → Bills projects recurring rules 60 days ahead, highlights overdue items, and folds uncategorized pending transactions into the same scannable list
- ✅ **Bill reminders** — opt-in, quiet reminders 2 days before, on due date, and once if a recurring expense remains pending
- ✅ **Wishlist** with savings-capacity math (NOW / WAIT until YYYY-MM / INFEASIBLE)
- ✅ **Wishlist** with TMOAP-style savings-capacity math — start month, desired horizon, remaining amount, needed/month, and NOW / WAIT until YYYY-MM / INFEASIBLE status
- ✅ **Wishlist planning summary** — remaining wishlist backlog, ready-now count, next reachable month, target monthly pressure, and capacity-risk count
- ✅ **Family investments pool** with **percentage-based return entry**, proportional share %, delete pool/contributor
- ✅ **Recurring transactions** — define rent / salary / Netflix / utilities once; rules materialize into PENDING transactions on their due date

#### Ingestion + categorization (Saudi-first, universal-ready)
- ✅ **Al Rajhi SMS** parser (Arabic + English) — purchase, transfer, deposit, profit deposit, loan instalment, credit card payment, declined transactions, transfers between own accounts
- ✅ **STC Bank · Alinma · D360 · Barq** templates derived from real corpus, with date extraction so historical SMS sit in their correct months
- ✅ **User-defined templates** — paste a sample SMS from your bank, mark the anchor strings around the amount/merchant, and confirm the live parse preview before saving (W-4)
- ✅ **Spam-resistant pipeline** — `-AD` suffix block (CITC convention), sender allow-list, ~40 ignore patterns for OTP/promo/marketing (Tasaheal, "Buy X Get Y", "Earn 10,000")
- ✅ **Sender-ID tolerant parsing** — built-in bank sender matching trims edge whitespace and ignores casing drift, while known bank/wallet maintenance, fee, fraud-awareness, migration, and account-admin notices are ignored instead of becoming failed parses or pending transactions
- ✅ **Fallback parser merchant preservation** — Arabic Al Rajhi purchase, transfer, and biller fallback rows preserve explicit merchant/counterparty labels when present, keeping pending rows useful for category rules and local learning instead of bank-sender placeholders
- ✅ **Private-corpus parser cleanup** — Arabic card settlements, salary/cashback, D360 own-account transfers, public-service payments, account/card admin notices, account-tail purchase labels, ATM withdrawals, bank-fee debits, wallet refunds, and Al Rajhi header-only electronic/public-service payments are routed, ignored, or labeled correctly; a local received-message audit now reports 0 failed known-bank messages and 0 parsed expense rows with missing merchants
- ✅ **Parser registry parity** — app ingestion, corpus tests, and private-audit work now use the same built-in SMS template registry, so new bank/wallet/notification templates cannot silently diverge from production order
- ✅ **Bank-admin notice filtering** — beneficiary add/activation, mobile-login/biometric, card-product, card-service recovery, savings-promo, app-migration, brand, document, and fraud-awareness notices from known banks are ignored instead of polluting the SMS audit as failed parses
- ✅ **Notification listener** path for Play-Store-safe distribution, with generic bank-app push parsing plus wallet/card-app phrases, peer-payment `sent you`/`got paid` income, bank-specific card-charge/debit-card merchant extraction, merchant-first charge/card-transaction copy, amount-adjacent trailing merchant/biller extraction, structured `Merchant:`/`Sender:`/`Recipient:`/`Biller:`/`Payer:`/`Receiver:` labels, balance-aware transaction amount selection, balance/credit-state ignores, card/account admin ignores, spending-summary/recap ignores, reward/cashback amount guards, OTP/payment-authorization and temporary-authorization-hold ignores, broader global ISO and regional-symbol notification currencies, comma-decimal amount formats, scheduled-payment and marketing-discount ignores, Google Pay/Samsung Pay package variants, and more MENA/APAC/Australia/US finance package coverage
- ✅ **Multi-currency capture** — foreign-card spend keeps both the original amount and the SAR equivalent
- ✅ **Self-transfer detection** — moves to your own savings account flagged as "Own account move" (savings), not expense
- ✅ **Auto-detected recurring patterns** — scans your confirmed history, surfaces "Same merchant, same amount, 3+ distinct months at similar day-of-month" candidates as suggestions you confirm with one tap
- ✅ **90-day historical SMS backfill** on first install
- ✅ Rule engine with **706 merchant seed rules** across 22 categories (beta.17+R-05 — expanded from 44 curated rules with AI-categorized patterns plus reviewed priority-90 catalog/private-audit/public-common batches; rules auto-refresh on app upgrade)
- ✅ "Always categorize X as Y?" **learn-from-correction** loop — **as of beta.18 the learned rule is backfilled to every existing PENDING + DISMISSED row** matching the merchant pattern, not just future SMS. Picking "Always" for one Hemmah charge fixes every stranded Hemmah in one tap
- ✅ Today and History show a short localized feedback card after "Always categorize..." so users know how many existing matching rows were fixed, or that future rows will auto-categorize when there were no existing matches
- ✅ Transaction lists show readable localized category names instead of raw internal category IDs, including archived-category labels for old rows
- ✅ **Local category rule learning** — after three confirmed transactions for the exact same merchant all share one category, Athar creates an exact local rule so future matches stop asking. Explicit "Always" rules still win; ambiguous merchants are ignored
- ✅ Source of every categorization is **explainable** (rule id, confidence)

#### Settings + ops
- ✅ **Multi-currency display** — pick from 18 ISO-4217 codes (USD · EUR · GBP · AED · EGP · INR · PKR · TRY · SAR · KWD · QAR · BHD · OMR · JOD · CAD · AUD · CHF · JPY) with Arabic + English currency labels
- ✅ **Per-account ingestion routing** — add SMS sender aliases or card/account tails to each account so new bank messages land on the right checking, savings, or credit-card account instead of the manual seed account
- ✅ **AES-256-GCM encrypted backup** (Argon2-equivalent KDF, passphrase-protected)
- ✅ **Statement import + CSV export** for Excel and bank-statement interop — preview detected fields, currency totals, and sample rows, edit/exclude bad rows or move individual rows to another account before confirmation, skip already-imported rows, then import TMOAP/Athar CSVs, transaction-grid XLSX workbooks, common statement CSVs/TSVs with description/amount, split debit/credit, or debit-credit indicator columns, OFX/QFX, or MT940 files; export annual data for your accountant
- ✅ **Manual CSV column mapping** — when a bank's CSV/TSV headers are unfamiliar, map date, merchant, amount/debit/credit, currency, category, type, and notes before preview/confirm
- ✅ **Annual accountant/tax PDF export** — pick a year in Settings and export income/expense totals, category totals, and the confirmed transaction list
- ✅ **First-run XLSX/CSV import** — new users can bring a TMOAP / Excel transaction log into Athar during onboarding instead of hunting for the Settings exchange later
- ✅ **SQLCipher** database encryption at rest, key wrapped via Android Keystore
- ✅ **SMS audit log** — every parsed/failed/ignored SMS retained, never deleted
- ✅ **Support diagnostics export** — Settings writes a redacted JSON report with SMS parse counts, pseudonymous sender hashes, body-shape fingerprints/flags, failed-template groups, redacted parser errors, and aggregate categorization-backlog counts. No raw SMS body, sender, merchant, amount, balance, card, account number, transaction row, or note is included
- ✅ **SMS audit log** — every parsed/failed/ignored SMS retained, never deleted, with sender-health counts so failed banks and spammy senders are easy to spot after a backfill
- ✅ **Activity log** — every transaction edit, with timestamp
- ✅ **All transactions history (H-01, beta.12)** — searchable list of every transaction across all time, filter chips for status (All / Confirmed / Pending / Dismissed), type (Expenses / Income / Transfers), source (SMS / notification / manual / import / recurring / share), and category state (All / Uncategorized / Categorized), with row subtitles showing readable category names. Tap any row to edit category or delete. Reachable from Settings → "All transactions"
- ✅ **Recover dismissed (beta.15)** — one-tap action moves every DISMISSED transaction back to PENDING so users on older builds can recover what was hidden by the now-removed auto-dismiss policy
- ✅ **SMS backfill scans entire inbox** (beta.11) — no 90-day cap; pass `daysBack: Int? = null` to scan all history
- ✅ **Rescan + clean** button — shows the pending count, asks for confirmation, then wipes the pending tray and re-runs backfill with the latest templates
- ✅ **Per-app language picker (beta.11)** — Follow system / Arabic / English; flips text + layout direction (RTL/LTR) on the fly via `CompositionLocalProvider(LocalLayoutDirection)`. Language card sits at the TOP of Settings for discoverability
- ✅ **Own-account list** — register the last-4 of your accounts so internal transfers are flagged as savings moves
- ✅ **Hijri date toggle**
- ✅ **Subscriptions screen (R-04, beta.17)** — recurring rules reframed as subscriptions with Active / Paused sections, monthly-total card, one-tap Active↔Paused toggle
- ✅ **Confirm-before-create sheet (R-03, beta.17)** — tapping a recurring suggestion opens a sheet to edit cadence (Monthly/Weekly/Yearly), day-of-month, and category before saving the rule
- ✅ **Bulk categorize with AI (beta.18+)** — export non-transfer PENDING/DISMISSED/uncategorized transactions to CSV, grouped so repeated merchants appear first with a `merchant_group_count` and row-level `category_options`, run it through ChatGPT/Claude with the AI triage prompt, import the filled file back. A single unambiguous category choice can update blank peers in the same repeated-merchant group, and each unambiguous merchant→category decision records an exact local rule so future SMS for the same normalized merchant auto-categorize. No API keys, no cloud round-trip — your personal merchant library grows permanently
- ✅ **Local category rule learning** — repeated confirmed exact-merchant/category history creates private priority-150 exact rules. No cloud, no community sharing, and no learning from ambiguous merchants
- ✅ **Reconcile to bank balance (beta.19)** — per-account "تسوية / Reconcile" chip. Enter your bank's actual balance; Athar inserts one manual adjustment transaction so the running balance matches. No silent history rewrite, full audit trail
- ✅ **Share your rules with the community (beta.20)** — Settings → "Help others · share your rules" exports only your "Always categorize X" substring merchant→category mappings as JSON, then opens a pre-filled GitHub issue. Exact local rules from bulk imports or repeated-history learning stay on device. Maintainer reviews accepted substring rules into the next release's seed for every user. Zero transaction data leaves your device
- ✅ **Home-screen widgets (beta.21+)** — three Glance widgets: Month summary (net + Income/Expense/Net-worth pills), Today snapshot (today's net + pending count), and Pending list (top-3 pending transactions with Confirm / Dismiss / Categorize shortcuts). Long-press home screen → Widgets → Athar
- ✅ **Support diagnostics export** — Settings → "Support diagnostics" creates `athar-support-diagnostics.json`, a bounded redacted parser and categorization-backlog report for maintainers. It includes counts, sender hashes, body-shape fingerprints/flags, failed-template groups, redacted errors, and hashed uncategorized-merchant groups only — no raw SMS bodies, merchant names, transaction rows, notes, balances, amounts, or account/card numbers
- ✅ **Home-screen widgets (beta.21)** — three Glance widgets: Month summary (net + Income/Expense/Net-worth pills), Today snapshot (today's net + pending count), and Pending list (top-3 pending transactions). Long-press home screen → Widgets → Athar
- ✅ **Reconciliation no longer inflates expenses (beta.22)** — manual adjustment transactions (`تسوية يدوية`) still move net worth but are excluded from monthly spend/income totals, Trends bars, Plan actuals, and widget snapshots
- ✅ **Update-availability nudge via Obtainium (beta.23)** — Settings → "تابع التحديثات / Stay up to date" hands off update-tracking to Obtainium via a deep link. Athar stays fully offline (no INTERNET permission); Obtainium watches the GitHub releases page and notifies you when a new build ships
- ✅ **Recurring rules management** with auto-detected suggestions, manual "Run now", and WorkManager auto-run so due subscriptions land in Pending without visiting Settings
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
| **storeSafe** | No SMS perms | Play-Store-eligible — uses NotificationListenerService, manual entry, statement import |

## Tests

```bash
./gradlew test                                   # all JVM unit tests
./gradlew :ml:categorizer:test                   # one module
./gradlew :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
./gradlew :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug
./gradlew connectedAndroidTest                   # instrumented (needs emulator/device)
maestro test .maestro/flows/                     # 10 E2E flows
```

## Roadmap

### Shipped in v0.1.0-beta.1 → current

**Foundations (beta.1):**
- ✅ Phase 0 foundations · 15 Gradle modules · build-logic convention plugins
- ✅ Excel parity for manual entry (M-01..M-13)
- ✅ Pending tray with swipe gestures (S-17..S-19)
- ✅ Hijri date toggle (P-07)
- ✅ SQLCipher encryption-at-rest with Keystore-wrapped key (ADR-003)
- ✅ AES-256-GCM JSON backup + restore · statement import + CSV export
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
- ✅ Auto-confirm by category when confidence is high; uncertain parsed transactions stay in Pending for explicit user review
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
- ✅ **G-5** — Localization (full): locale picker, DataStore persistence, `attachBaseContext` Configuration override + Compose `LocalLayoutDirection` provider. **~300 user-facing strings extracted** to `values/` (Arabic default) + `values-en/` across app:onboarding, core:design-system, feature:today, feature:trends, feature:plan, feature:settings. ViewModels emit typed sealed states (`AccountError`, `BackupStatus.ExportSuccess` / `ExportFailure(detail)`, etc.) and the Composable layer resolves them to `stringResource()`, so VMs stay pure (no Context dependency). Visually QA'd end-to-end on emulator: switched to English → all UI English + LTR (chart legends "Average/Target", swipe-row "Confirm/Dismiss", FAB + settings-gear flip sides, category-picker search field translates); switched back to Arabic → all UI Arabic + RTL. Remaining persisted-data debt (acceptable): the auto-seeded "النقدي" Cash-account name (user-renameable), SMS self-transfer merchant/notes (stored in DB at ingest time; future work would inject ApplicationContext or use a sentinel value resolved at render).

**User-testing patches (beta.11–beta.15):**
- ✅ **H-01 / All-transactions history (beta.12)** — searchable + filterable list of every transaction; user can drill in from Settings, fix categories one by one, learn-rule propagates corrections to future SMS.
- ✅ **Today-section filter + pending banner (beta.13)** — Today list now splits today's transactions from this-month-recent; ember banner above the header shouts "%d transactions awaiting review" with tap-to-history CTA. Stale-edit-sheet regression fixed with `key(tx.id) { … }` + synchronous state reset on `EditTransactionViewModel.load()`.
- ✅ **Dismissed-today banner (beta.14)** — dust-color secondary banner surfaces parser false-negatives so the user catches transactions Athar discarded.
- ✅ **Fail-safe ingestion (beta.15, [ADR-008](docs/adr/ADR-008-ingestion-fail-safe.md))** — auto-dismiss removed entirely from the pipeline. Anything that successfully parses as a transaction lands in CONFIRMED (categorizer matched) or PENDING (user must decide). Never DISMISSED automatically. Money no longer silently disappears. One-tap **"Recover dismissed transactions"** Settings action moves every legacy DISMISSED row back to PENDING for existing users.
- ✅ **AI triage workflow (R-02, [docs/AI_SMS_TRIAGE_PROMPT.md](docs/AI_SMS_TRIAGE_PROMPT.md))** — copy-paste prompt teaches an external AI (ChatGPT/Claude/Gemini) to read a bulk SMS export and return JSON with `transactions` + new `parser_templates` + new `categorization_rules`. Developer pastes JSON back, merges into `seed_rules.json` and per-bank template files. Lets the user expand bank coverage from any device's SMS without code changes per batch.

### Current validation status

- ✅ The recovered PR stack (#11–#30) is integrated locally on `dev/integration-recovered-stack`; details are in [`docs/RECOVERY_2026-06-13.md`](docs/RECOVERY_2026-06-13.md).
- ✅ JVM tests, both debug APK builds, and both app lint variants pass with JDK 17.
- ✅ The scoped `dev/today-goals-nudge` branch also passes `:feature:today:test` plus the full JVM test/build/lint stack after adding the Today Goals check.
- ✅ The scoped `dev/bill-reminders` branch passes `:core:domain:test`, `:feature:plan:compileDebugKotlin`, `:app:compilePersonalFullSmsDebugKotlin`, and the full JVM test/build/lint stack after adding opt-in bill reminders. The full lint stack was run with `--max-workers=1` because parallel lint analysis intermittently crashed inside Android lint's Kotlin FIR resolver on existing unit tests.
- ✅ The scoped `dev/sender-matching-reliability` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after hardening sender matching and adding sanitized corpus tests. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`.
- ✅ The scoped `dev/notification-phrase-coverage` branch passes `:ingestion:sms-parser:test`, `:ingestion:notification-listener:test`, and the full JVM test/build/lint stack after adding wallet/card-app notification phrase coverage. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`.
- ✅ The scoped `dev/csv-import-preview-confirm` branch passes `:core:data:test`, `:feature:settings:test`, and the full JVM test/build/lint stack after adding CSV import preview/confirm. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`.
- ✅ The scoped `dev/notification-app-handlers` branch passes `:ingestion:sms-parser:test`, `:ingestion:notification-listener:test`, and the full JVM test/build/lint stack after adding peer-payment, global-currency, Google Pay, and Samsung Pay notification coverage. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`.
- ✅ The scoped `dev/ofx-qfx-import-preview` branch passes `:core:data:test`, `:feature:settings:test`, and the full JVM test/build/lint stack after adding OFX/QFX statement import through the preview-confirm path. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`.
- ✅ The scoped `dev/mt940-import-preview` branch passes `:core:data:test`, `:feature:settings:test`, and the full JVM test/build/lint stack after adding MT940 statement import through the preview-confirm path. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`.
- ✅ The scoped `dev/import-account-selection` branch passes `:core:data:test`, `:feature:settings:test`, and the full JVM test/build/lint stack after adding destination-account selection to statement import confirmation. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`.
- ✅ The scoped `dev/csv-delimiter-detection` branch passes `:core:data:test`, `:feature:settings:test`, and the full JVM test/build/lint stack after adding comma/semicolon/tab delimiter detection to statement CSV import. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`.
- ✅ The scoped `dev/csv-debit-credit-indicator` branch passes `:core:data:testDebugUnitTest`, `:core:data:compileDebugKotlin`, and the full JVM test/build/lint stack after adding positive-amount CSV debit/credit indicator support. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`.
- ✅ The scoped `dev/import-duplicate-safety` branch passes `:core:data:testDebugUnitTest`, `:core:data:compileDebugKotlin`, `:feature:settings:testDebugUnitTest`, `:feature:settings:compileDebugKotlin`, and the full JVM test/build/lint stack after adding account-scoped duplicate-safe statement imports. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`.
- ✅ The scoped `dev/localized-amount-parsing` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after adding localized comma-decimal amount parsing to generic notification and fallback SMS paths. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`.
- ✅ The scoped `dev/curated-seed-rules-batch-2` branch passes `:core:data:test` and the full JVM test/build/lint stack after adding 15 more reviewed priority-90 seed rules. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`.
- ✅ The scoped `dev/curated-seed-rules-batch-3` branch passes `:core:data:testDebugUnitTest` and the full JVM test/build/lint stack after adding 11 more reviewed priority-90 seed rules, bringing the active seed to 644 rules. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`; runtime validation remains blocked because `adb devices -l` returned no attached devices.
- ✅ The scoped `dev/notification-bank-specific-phrases` branch passes `:ingestion:sms-parser:test`, `:ingestion:notification-listener:test`, and the full JVM test/build/lint stack after adding bank-specific notification merchant extraction and package coverage. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`.
- ✅ The scoped `dev/global-notification-phrases` branch passes `:ingestion:sms-parser:test`, `:ingestion:notification-listener:test`, and the full JVM test/build/lint stack after adding merchant-first charge/card-transaction notification parsing, broader ISO currency preservation, scheduled-payment ignores, and more global finance package coverage. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`.
- ✅ The scoped `dev/manual-entry-voice-phrase` branch passes `:feature:today:testDebugUnitTest`, `:feature:today:compileDebugKotlin`, and the full JVM test/build/lint stack after adding typed/voice quick-entry fill to Add Transaction. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`.
- ✅ The scoped `dev/manual-entry-receipts` branch passes focused `:core:domain:test`, `:core:data:testDebugUnitTest`, `:core:data:compileDebugKotlin`, `:feature:today:testDebugUnitTest`, `:feature:today:compileDebugKotlin`, and the full JVM test/build/lint stack after adding encrypted manual receipt attachments.
- ✅ The scoped `dev/receipt-view-export` branch passes `:feature:today:test` and the full JVM test/build/lint stack after adding edit-sheet receipt view/export/remove. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`; runtime tap-through remains blocked because `adb devices -l` returned no attached devices.
- ✅ The scoped `dev/bank-admin-notice-ignores` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after adding sanitized bank-admin notice ignores. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`; runtime tap-through remains blocked because `adb devices -l` returned no attached devices.
- ✅ The scoped `dev/csv-manual-column-mapping` branch passes `:core:data:testDebugUnitTest`, `:feature:settings:testDebugUnitTest`, and the full JVM test/build/lint stack after adding manual CSV/TSV column mapping to statement import. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`; runtime Settings import tap-through remains blocked because `adb devices -l` returned no attached devices.
- ✅ The scoped `dev/import-preview-row-exclusion` branch passes `:core:data:testDebugUnitTest`, `:feature:settings:testDebugUnitTest`, and the full JVM test/build/lint stack after adding import preview row exclusion. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`; runtime Settings import tap-through remains blocked because `adb devices -l` returned no attached devices.
- ✅ The scoped `dev/import-currency-review` branch passes `:core:data:testDebugUnitTest`, `:feature:settings:testDebugUnitTest`, and the full JVM test/build/lint stack after adding per-currency import preview summaries. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`; runtime Settings import tap-through could not complete because the installed `AtharPixelQaApi35` x86_64 AVD exits without the Android Emulator hypervisor driver, leaving `adb devices -l` empty.
- ✅ The scoped `dev/import-row-field-overrides` branch passes `:core:data:testDebugUnitTest`, `:feature:settings:testDebugUnitTest`, and the full JVM test/build/lint stack after adding statement import preview row field editing. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`; runtime Settings import tap-through could not complete because the installed `AtharPixelQaApi35` x86_64 AVD exits without the Android Emulator hypervisor driver, leaving `adb devices -l` empty.
- ✅ The scoped `dev/import-row-account-overrides` branch passes `:core:data:testDebugUnitTest`, `:feature:settings:testDebugUnitTest`, and the full JVM test/build/lint stack after adding per-row account review to statement import previews. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `--no-daemon`, and `--max-workers=1`; runtime Settings import tap-through could not complete because `adb devices -l` returned no attached devices and `emulator -accel-check` reports that the Android Emulator hypervisor driver is not installed.
- ✅ The scoped `dev/private-corpus-parser-hardening` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after adding sanitized ignore coverage for the last known-bank failures in the private family-device SMS export. A temporary local audit over 8,136 exported messages reported zero failed known-bank messages after the change; the raw export and audit helper remain uncommitted. Runtime tap-through remains blocked because `adb devices -l` returned no attached devices and `emulator -accel-check` reports that the Android Emulator hypervisor driver is not installed.
- ✅ The scoped `dev/private-corpus-fallback-parser-hardening` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after preserving Arabic Al Rajhi purchase, transfer, and biller labels from the private family-device SMS export. A temporary local audit over 8,136 exported messages reduced parsed expense rows with missing merchants from 3,800 to 474 and increased categorized expenses from 79 to 945; the raw export and audit helper remain uncommitted.
- ✅ The scoped `dev/curated-seed-rules-private-audit-batch` branch passes `:core:data:testDebugUnitTest` and the full JVM test/build/lint stack after adding 13 reviewed priority-90 seed rules from aggregate-only private audit review, bringing the active seed to 657 rules. The raw export and temporary audit helper remain uncommitted; runtime tap-through remains blocked because no ADB target is attached and the local AVD still cannot boot without the Android Emulator hypervisor driver.
- ✅ The scoped `dev/private-corpus-missing-merchant-parser` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after routing Arabic card settlements, salary/cashback rows, D360 own-account transfers, public-service payment labels, and account/card admin notices correctly. A temporary app-equivalent local audit over 8,136 exported messages reduced parsed expense rows with missing merchants from 474 to 20; the raw export and temporary audit helper remain uncommitted. Runtime tap-through was attempted, but `adb devices -l` returned no attached devices and a bounded software-acceleration boot of `AtharPixelQaApi35` exited before exposing ADB.
- ✅ The scoped `dev/curated-seed-rules-post-r13-audit` branch passes parser tests, seed asset tests, and the full JVM test/build/lint stack after fixing account-tail merchant precedence, normalizing ATM withdrawal / bank-fee labels, and adding 10 reviewed priority-90 seed rules, bringing the active seed to 667 rules. A temporary app-equivalent local audit over 8,136 exported messages increased categorized parsed expenses from 1,113 to 1,238; the raw export and temporary audit helper remain uncommitted. Runtime install/tap-through was attempted, but no ADB target was attached and a bounded software-rendered boot of `AtharPixelQaApi35` exited with `-1073741819` before exposing ADB.
- ✅ The scoped `dev/xlsx-statement-import-preview` branch passes `:core:data:testDebugUnitTest` plus the full JVM test/build/lint stack after adding direct transaction-grid XLSX import through the statement preview-confirm path. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `ANDROID_HOME=C:\Users\waok\Android\Sdk`, `--no-daemon`, and `--max-workers=1`; runtime Settings/onboarding import tap-through could not complete because no device was attached, `emulator -accel-check` reports that the Android Emulator hypervisor driver is not installed, and a bounded software boot of `AtharPixelQaApi35` stayed `offline` in ADB for 3 minutes.
- ✅ The scoped `dev/curated-seed-rules-post-xlsx-audit` branch passes `:core:data:testDebugUnitTest` and the full JVM test/build/lint stack after adding 11 reviewed priority-90 seed rules from aggregate-only private audit review, bringing the active seed to 678 rules. A temporary local audit over 8,136 exported messages increased categorized parsed expenses from 1,238 to 1,265; the raw export and temporary audit helper remain uncommitted. Runtime tap-through was not applicable to this seed-only slice; `adb devices -l` still returned no attached devices and `emulator -accel-check` still reports that the Android Emulator hypervisor driver is not installed.
- ✅ The scoped `dev/notification-merchant-hints-followup` branch passes `:ingestion:sms-parser:test`, `:ingestion:notification-listener:test`, and the full JVM test/build/lint stack after adding conservative notification merchant/counterparty hints for `for`, `on`, Arabic `في`, `new transaction: merchant amount`, and `credit of ... from` copy. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `ANDROID_HOME=C:\Users\waok\Android\Sdk`, `--no-daemon`, and `--max-workers=1`; runtime notification tap-through could not complete because no device was attached and `emulator -accel-check` still reports that the Android Emulator hypervisor driver is not installed.
- ✅ The scoped `dev/transaction-category-labels` branch passes `:feature:today:testDebugUnitTest` and the full JVM test/build/lint stack after replacing raw transaction category IDs in Today and History rows with localized category names, including archived-category labels for older rows. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `ANDROID_HOME=C:\Users\waok\Android\Sdk`, `--no-daemon`, and `--max-workers=1`; runtime Today/History tap-through could not complete because no device was attached and the local `AtharPixelQaApi35` x86_64 AVD exited with `x86_64 emulation currently requires hardware acceleration`.
- ✅ The scoped `dev/curated-seed-rules-public-leftovers` branch passes `:core:data:testDebugUnitTest` and the full JVM test/build/lint stack after adding 8 reviewed priority-90 public/common seed rules, bringing the active seed to 686 rules. Broad catalog leftovers such as `pizza`, `hospital`, `pharmacy`, `gym`, `airport`, `metro`, and `university` remain excluded to avoid false positives; runtime tap-through is not applicable to this seed-only slice.
- ✅ The scoped `dev/notification-package-coverage-followup` branch passes `:ingestion:notification-listener:test`, `:ingestion:sms-parser:test`, and the full JVM test/build/lint stack after allowing more known finance-app packages from MENA, Australia, India/Southeast Asia, and the US through both the Android notification listener filter and the existing conservative notification parser. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `ANDROID_HOME=C:\Users\waok\Android\Sdk`, `--no-daemon`, and `--max-workers=1`; runtime notification tap-through could not complete because no device was attached, the x86_64 AVD requires the missing Android Emulator hypervisor driver, and the arm64 AVD is not supported on this x86_64 host.
- ✅ The scoped `dev/notification-labeled-fields` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after preserving merchants/counterparties from structured notification labels such as `Merchant:`, `Sender:`, `Recipient:`, `Beneficiary:`, and colon-separated `From:` fields. Validation was run with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`, `ANDROID_HOME=C:\Users\waok\Android\Sdk`, `--no-daemon`, and `--max-workers=1`; runtime notification tap-through could not complete because no ADB device was attached, the arm64 AVD is not supported on this x86_64 host, and a bounded headless `AtharPixelQaApi35` x86_64 launch with `-accel off` stayed `offline` in ADB after reconnect.
- ✅ The scoped `dev/sms-audit-full-filter-history` branch passes `:feature:settings:testDebugUnitTest` and the full JVM test/build/lint stack after making SMS audit status filters select their visible 200 rows from the full audit history, so older Failed/Ignored rows remain reachable after large backfills instead of being hidden behind the All-window cap. Runtime tap-through could not complete because no ADB device was attached and `emulator -accel-check` still reports that the Android Emulator hypervisor driver is not installed.
- ✅ The scoped `dev/notification-party-label-fields` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after preserving structured notification party labels such as `Biller:`, Arabic `المفوتر`, `Payer:`, `Remitter:`, `Receiver:`, and transfer `Payee:` so more store-safe rows carry useful merchants/counterparties for categorization and local learning. Runtime notification tap-through could not complete because no ADB device was attached, `emulator -accel-check` reports that the Android Emulator hypervisor driver is not installed, and a bounded `AtharPixelQaApi35` software launch did not expose an online ADB device after 3 minutes.
- ✅ The scoped `dev/curated-seed-rules-arabic-public-labels` branch passes `:core:data:testDebugUnitTest` and the full JVM test/build/lint stack after adding Arabic public-platform seed coverage for Ejar, Mobily, Gonsure, and Tameeni, bringing the active seed to 690 rules while keeping the raw private SMS export uncommitted. Runtime install/launch was attempted, but the software-launched `AtharPixelQaApi35` AVD stayed `offline` in ADB.
- ✅ The scoped `dev/multiple-receipts-per-transaction` branch passes focused data/Today tests and the full JVM test/build/lint stack after letting one transaction keep multiple encrypted receipt images. It bumps Room to v7, removes the unique receipt-per-transaction index, preserves backup/export behavior, and updates Add/Edit transaction receipt controls so each saved receipt can be viewed, exported, or removed independently. Runtime install/launch was attempted, but no local AVD reached an online ADB state.
- ✅ The scoped `dev/wishlist-planning-summary` branch passes focused Wishlist domain tests, Plan compilation, and the full JVM test/build/lint stack after adding the W-09 Wishlist planning summary. Runtime install/launch was attempted; `AtharPixelQaApi35` exited with Windows access-violation code `-1073741819`, and a windowed-hidden retry logged `Failed to load opengl32sw` before opening an emulator crash dialog with no ADB device.
- ✅ The scoped `dev/notification-balance-amount-selection` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after making generic bank-app notifications prefer the posted transaction amount over earlier balance/available-balance amounts in English and Arabic notification copy. Runtime install/launch was attempted; `emulator -accel-check` still reports no Android Emulator hypervisor driver, and a bounded `AtharPixelQaApi35` software boot left only an `emulator-5554 offline` ADB transport with no emulator process.
- ✅ The scoped `dev/notification-marketing-discount-guards` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after preventing Arabic bank marketing discount offers from becoming fake expense rows while preserving real Arabic `خصم ...` debit notifications. Runtime install/launch was attempted; after clearing a stale headless qemu process and AVD locks, `AtharPixelQaApi35` still stayed `offline` in ADB for 3 minutes under software acceleration.
- ✅ The scoped `dev/notification-reward-amount-guards` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after ignoring reward-only cashback notifications, skipping reward-adjacent amounts when a real spend amount is present, and preserving cashback credits as income. Runtime install/launch was attempted; `AtharPixelQaApi35` again stayed `offline` in ADB for 3 minutes under software acceleration and the stuck headless process/locks were cleaned up afterward.
- ✅ The scoped `dev/notification-security-code-amount-guards` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after ignoring OTP/verification-code payment authorization notifications with amounts in English and Arabic while preserving real posted payment notifications. Runtime install/launch was attempted; `AtharPixelQaApi35` emitted boot logs but never exposed an online ADB device, and stale AVD locks were cleaned afterward.
- ✅ The scoped `dev/notification-authorization-hold-guards` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after ignoring pending authorization, pre-authorization, temporary card-hold, and Arabic temporary-hold notification amounts while preserving real posted card-transaction notifications. Runtime install/launch was attempted; `AtharPixelQaApi35` crashed before exposing ADB after `Failed to load opengl32sw`, and stale AVD locks were cleaned afterward.
- ✅ The scoped `dev/notification-balance-only-hint-guards` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after ignoring balance-only notifications even when they contain account/location hint wording such as `at your checking account` or Arabic `لدى حسابك`, while preserving balance-before-spend debit notifications. Runtime install/launch was attempted; `AtharPixelQaApi35` crashed before exposing ADB after `Failed to load opengl32sw`, and stale AVD locks were cleaned afterward.
- ✅ The scoped `dev/notification-credit-state-guards` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after ignoring available-credit, credit-limit, cash-advance-limit, and Arabic credit-state notification amounts while preserving real credited-income notifications. Runtime install/launch was attempted; `AtharPixelQaApi35` crashed before exposing ADB after `Failed to load opengl32sw`, and stale AVD locks were cleaned afterward.
- ✅ The scoped `dev/notification-admin-notice-guards` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after ignoring finance-app card/account administration notices such as card activation, terms updates, device-link notices, and Arabic card activation copy while preserving real card-use transaction notifications. Runtime install/launch was attempted; `AtharPixelQaApi35` did not reach online ADB, logged `Failed to load opengl32sw`, opened an emulator crash dialog, and stale AVD locks were cleaned afterward.
- ✅ The scoped `dev/notification-spending-summary-guards` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after ignoring weekly/monthly spending recaps, English `you spent ... this month` summaries, and Arabic spending-summary notices while preserving real merchant spend notifications. Runtime install/launch was attempted; `AtharPixelQaApi35` did not reach online ADB, logged `Failed to load opengl32sw`, opened an emulator crash dialog, and stale AVD locks were cleaned afterward.
- ✅ The scoped `dev/private-corpus-micro-parser-audit` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after moving the built-in SMS parser template order into a shared parser-module registry used by both app DI and corpus tests. Runtime install/launch was attempted; `AtharPixelQaApi35` did not reach online ADB, logged `Failed to load opengl32sw`, opened an emulator crash dialog, and stale AVD locks were cleaned afterward.
- ✅ The scoped `dev/curated-seed-rules-global-common-batch` branch passes `:core:data:testDebugUnitTest` and the full JVM test/build/lint stack after adding 15 concrete global public/common seed rules for subscription/software, delivery, and grocery labels, bringing the active seed to 705 rules without using or committing private SMS contents. Runtime install/launch was attempted; a bounded `AtharPixelQaApi35` software boot emitted startup logs but never exposed an online ADB device, and the default x86_64 launch exited with `x86_64 emulation currently requires hardware acceleration`; stale AVD locks were cleaned afterward.
- ✅ The scoped `dev/private-corpus-admin-ignore-followup` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after adding sanitized ignores for private-corpus bank administration/account-state notices with numbers, and parsing structured wallet refunds as income. A temporary received-message audit over the private export reduced parsed expenses with missing merchants from 18 to 5; the raw export and scratch audit helper remain uncommitted. Runtime install/launch was attempted; `AtharPixelQaApi35` emitted startup logs under `-accel off` but never exposed an online ADB device, so install could not run.
- ✅ The scoped `dev/alrajhi-electronic-payment-labels` branch passes `:ingestion:sms-parser:test` and the full JVM test/build/lint stack after preserving Al Rajhi header-only electronic-payment and Ministry of Interior public-service labels as merchants. A temporary received-message audit over the private export reduced parsed expenses with missing merchants from 5 to 0; the raw export and scratch audit helper remain uncommitted. Runtime install/launch was attempted; `AtharPixelQaApi35` emitted startup logs under `-accel off` but never exposed an online ADB device, so APK install and screenshot capture could not run.
- ✅ The scoped `dev/curated-seed-rules-post-r16-audit` branch passes `:ingestion:sms-parser:test`, `:core:data:testDebugUnitTest`, and the full JVM test/build/lint stack after adding a gated redacted private-corpus aggregate audit and promoting the public `buffet` restaurant seed rule, bringing the active seed to 706 rules. The redacted local report inspects the private export without writing raw bodies, keeps failed known-bank messages and missing-merchant expenses at 0, and reduces uncategorized parsed expenses from 1,102 to 1,101. Runtime install/launch was attempted; `AtharPixelQaApi35` stayed `emulator-5554 offline` under `-accel off`, so APK install and screenshot capture could not run, and the stuck headless qemu process/locks were cleaned.
- ✅ The scoped `dev/support-diagnostics-category-backlog` branch passes `:core:data:testDebugUnitTest` and the full JVM test/build/lint stack after adding redacted transaction category-backlog counts and hashed uncategorized-merchant groups to support diagnostics. Runtime install/launch was attempted with the built `personalFullSmsDebug` APK; `AtharPixelQaApi35` exited before exposing ADB with Windows access-violation code `-1073741819` under both `-gpu swiftshader_indirect` and `-gpu off` software launches, and stale AVD locks were cleaned.
- ✅ The scoped `dev/category-backlog-action-summary` branch passes `:core:data:testDebugUnitTest`, the gated private SMS export audit, and the full JVM test/build/lint stack after making the bulk category CSV skip transfers, group repeated merchants first, and expose `merchant_group_count`. The private audit still reports 7,887 records, 3,864 parser successes, 0 failed known-bank messages, 0 missing-merchant parsed expenses, and 1,101 uncategorized parsed expenses for this workflow. Runtime install/launch was attempted; `AtharPixelQaApi35` exited before exposing ADB with Windows access-violation code `-1073741819`, and stale AVD locks were cleaned.
- ✅ The scoped `dev/bulk-categorize-category-options` branch passes `:core:data:testDebugUnitTest`, the gated private SMS export audit, and the full JVM test/build/lint stack after adding row-level `category_options` to the bulk categorization CSV so ChatGPT/Claude or a human can choose valid active category IDs from the current app category table. The latest module-scoped private audit report shows 7,887 records, 3,864 parser successes, 0 parser failures, 0 failed known-bank messages, 193 missing-merchant parsed expenses, and 1,385 uncategorized parsed expenses. Runtime install/launch was attempted; `AtharPixelQaApi35` stayed `emulator-5554 offline` under a bounded `-no-window -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect -accel off` launch, `AtharPixelQaApi35Arm` exited because arm64 system images are unsupported on this x86_64 host, and cleanup finished with no attached ADB device, no emulator/qemu/netsim process, and no AVD locks.
- ✅ The scoped `dev/private-audit-missing-merchant-shapes` branch passes `:ingestion:sms-parser:test`, the gated private SMS export audit, and the full JVM test/build/lint stack after adding redacted missing-merchant shape groups to the private audit and fixing universal Arabic action detection so fallback `حوالة` rows become transfers instead of uncategorized expenses. The latest module-scoped private audit report shows 7,887 records, 3,864 parser successes, 0 parser failures, 0 failed known-bank messages, 2,374 parsed expenses, 1,262 categorized parsed expenses, 1,112 uncategorized parsed expenses, 0 missing-merchant parsed expenses, 0 raw bodies written, and no inactive catalog matches. Runtime install/launch was attempted; `AtharPixelQaApi35` stayed `emulator-5554 offline` under a bounded `-no-window -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect -accel off` launch, and cleanup finished with no attached ADB device, no emulator/qemu/netsim process, and no AVD locks.
- ✅ The scoped `dev/private-audit-uncategorized-groups` branch passes `:ingestion:sms-parser:test`, the gated private SMS export audit, and the full JVM test/build/lint stack after adding top hashed uncategorized merchant groups to the gated private audit report. The latest report still writes 0 raw bodies and shows the categorization backlog is concentrated enough to act on: top hashed groups have 55, 27, 12, 11, and 11 samples, with merchant length/script buckets and parser-template counts but no raw labels. Runtime install/launch was attempted; `AtharPixelQaApi35` stayed `emulator-5554 offline` under a bounded `-no-window -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect -accel off` launch, so APK install could not run, and cleanup finished with no attached ADB device, no emulator/qemu/netsim process, and no AVD locks.
- ✅ The scoped `dev/bulk-categorize-group-import` branch passes `:core:data:testDebugUnitTest` and the full JVM test/build/lint stack after making bulk categorization import apply one unambiguous category choice to blank peers in the same imported repeated-merchant group. Conflicting merchant-group choices update only explicit rows and do not train a learned rule. Runtime install/launch was attempted; `AtharPixelQaApi35` exited before exposing ADB with Windows access-violation code `-1073741819`, stale AVD locks were removed, and cleanup finished with no attached ADB device, no emulator/qemu/netsim process, and no AVD locks.
- ✅ The scoped `dev/bulk-categorize-exact-import-rules` branch passes `:core:data:testDebugUnitTest` and the full JVM test/build/lint stack after making bulk category imports train exact local merchant rules and making community-rule export omit exact local rules from bulk import or repeated-history learning. Runtime install/launch was attempted; `AtharPixelQaApi35` exposed only an offline ADB transport briefly, then exited before install with Windows access-violation code `-1073741819`, and cleanup finished with no attached ADB device, no emulator/qemu/netsim process, and no AVD locks.
- ✅ The scoped `dev/notification-trailing-party-hints` branch passes `:ingestion:sms-parser:test`, `:ingestion:notification-listener:test`, and the full JVM test/build/lint stack after making store-safe notification parsing preserve compact amount-adjacent merchant/biller labels while stripping trailing balance/card/status suffixes. Runtime store-safe install was attempted; `AtharPixelQaApi35` exposed only an offline ADB transport, exited before install with Windows access-violation code `-1073741819`, and cleanup finished with no attached ADB device, no emulator/qemu/netsim process, and no AVD locks.
- ✅ The scoped `dev/notification-regional-currency-symbols` branch passes `:ingestion:sms-parser:test`, `:ingestion:notification-listener:test`, and the full JVM test/build/lint stack after preserving regional notification currency symbols such as `S$`, `R$`, `RM`, `Rp`, `₱`, `₩`, `฿`, and `₫` as the correct ISO currencies instead of defaulting to SAR or plain USD. Runtime store-safe install was attempted; `AtharPixelQaApi35` exited before exposing ADB with Windows access-violation code `-1073741819`, stale AVD locks were cleaned, and final cleanup had no attached ADB device, no emulator/qemu/netsim process, and no AVD locks.
- ⏳ Device E2E, screenshot UI audit, and notification tap-through still need a physical Android device or a working accelerated emulator. On 2026-06-14 `AtharPixelQaApi35` emitted software-boot logs but exited before exposing ADB, later W-09 retries crashed before ADB, the G-11 balance retry left only an offline ADB transport, the G-11 discount-guard and reward-guard retries stayed offline after stale-lock cleanup, the G-11 security-code retry emitted boot logs without an online ADB device, the G-11 authorization-hold, balance-only, credit-state, admin-notice, spending-summary, and parser-registry retries crashed before ADB after `Failed to load opengl32sw`, the global-common seed retry either stayed without online ADB under software boot or exited because x86_64 emulation requires hardware acceleration, the R-15 admin-ignore retry and R-16 electronic-payment retry stayed without online ADB under `-accel off`, the R-05 post-R16 audit retry stayed `emulator-5554 offline` under `-accel off`, the S-21 diagnostics and R-07 backlog-context retries exited before ADB with `-1073741819`, the R-07 category-options, R-17 missing-merchant-shapes, and R-18 uncategorized-groups retries stayed `emulator-5554 offline` under `-accel off`, and `AtharPixelQaApi35Arm` exited because arm64 system images are not supported by this x86_64 host, preventing install, launch, screenshots, and tap-through.
- ⏳ The latest R-07 group-import runtime retry also could not install or launch on 2026-06-14: `AtharPixelQaApi35` exited before exposing ADB with Windows access-violation code `-1073741819` after stale-lock cleanup.
- ⏳ The latest R-07 exact-import-rules runtime retry also could not install or launch on 2026-06-14: `AtharPixelQaApi35` briefly exposed only `emulator-5554 offline`, then exited before ADB with Windows access-violation code `-1073741819`; stale AVD locks were cleaned.
- ⏳ The latest G-11 trailing-party-hints runtime retry also could not install or launch on 2026-06-14: the built `storeSafeDebug` APK was ready, but `AtharPixelQaApi35` briefly exposed only `emulator-5554 offline`, then exited before install with Windows access-violation code `-1073741819`; stale AVD locks were cleaned.
- ⏳ The latest G-11 regional-currency-symbols runtime retry also could not install or launch on 2026-06-14: the built `storeSafeDebug` APK was ready, but `AtharPixelQaApi35` exited before exposing ADB with Windows access-violation code `-1073741819`; stale AVD locks were cleaned and ADB was restarted.

### In flight / remaining

| ID | Feature | Status | Effort | Notes |
|---|---|---|---|---|
| QA-01 | Runtime E2E + UI audit | ⏳ | 0.5–1 day | Needs physical Android device or accelerated emulator |
| G-7 | Bill reminders | ✅ | — | Bills calendar + opt-in reminders shipped; runtime notification QA remains under QA-01 |
| G-8 | Manual transaction UX upgrades | ✅ | 1.5 days | ✅ Recent-merchant autocomplete + quick-add chips · ✅ quick phrase/voice fill · ✅ multiple local encrypted receipt attachments · ✅ receipt view/export/remove from edit sheet |
| G-10 | Savings-rate goals + emergency fund | ✅ | — | Plan → Goals tab plus Today Goals check shipped; device visual QA remains under QA-01 |
| W-09 | Wishlist planning summary | ✅ | — | Remaining backlog, ready-now count, next reachable month, target pressure, and capacity-risk count shipped |
| G-11 | Broader notification handlers | ◐ | 3 days | Generic parser, wallet/card phrases, peer-payment/global-currency handlers, regional currency symbols, localized comma-decimal amount parsing, bank-specific card-charge/debit-card merchant extraction, merchant-first charge/card-transaction parsing, amount-adjacent trailing merchant/biller extraction, structured label-field extraction, balance-aware amount selection plus balance/credit/admin/summary ignores, reward/cashback guards, OTP/payment-authorization and authorization-hold ignores, scheduled-payment and marketing-discount ignores, and broader package coverage shipped; more real bank copy remains |
| G-12 | Bank statement / XLSX / CSV / OFX / QFX / MT940 import wizard | ✅ | 3 days | XLSX transaction grids, CSV/TSV delimiter + header auto-detect, debit/credit indicator support, duplicate-safe preview/confirm, OFX/QFX, MT940, destination-account selection, manual CSV column mapping, row exclusion, currency review, field-level row editing, and per-row account review shipped |
| G-13 | Zero-knowledge sync to companion devices | ⏳ | 5 days | E2E-encrypted via Dropbox / Drive / iCloud / WebDAV / S3 — user holds the key |
| G-14 | Tax-export PDF for accountants | ✅ | 2 days | Annual category totals + transaction list in user's locale |

Full gap analysis: [`docs/ROADMAP_GLOBAL.md`](docs/ROADMAP_GLOBAL.md).

### Deferred (technical-debt only)

- ⏳ TFLite merchant classifier (rule engine handles ~90% of cases)
- ⏳ Paparazzi snapshot baselines (need to record on a real machine)
- ⏳ Macrobenchmarks (need a device)

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
