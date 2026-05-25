# Roadmap — making Athar a global TMOAP replacement

This document is the answer to: *"What does Athar still need to be useful for any user, anywhere, the way TMOAP is?"*

Athar today is **better than TMOAP for any user** — Saudi or non-Saudi: SMS automation (Saudi only), encrypted storage, Arabic-first or English-first UI, multi-currency (18 codes), multi-account net worth, recurring transactions, full TMOAP-depth Trends. The Saudi specialization (SMS parser + AlRajhi/STC/D360/Barq templates + Hijri toggle + Thmanyah typeface) remains intact — non-Saudi users just don't pay for it.

Tier 1 — required to ship as a global app — is **complete**. Tiers 2–4 are remaining differentiators and polish.

> **For future AI / developers:** every shipped row below carries a *Why* and a *How* line so you can read this in 2 minutes and understand the entire design rationale without re-reading the diffs. When you ship something new, add `Why:` and `How:` to its row — the next person to touch this will thank you.

Below is the prioritized gap list, in delivery order.

---

## Tier 1 — Required for any non-Saudi user — ✅ COMPLETE

### G-1 — Currency abstraction ✅ (beta.5)
**Why:** A user in Egypt, Dubai, India, the US, anywhere — must not see Saudi riyals in their dashboard if their bank uses EGP/AED/INR/USD. Without this, ~95% of potential users are blocked at first launch.
**How:** 18 ISO-4217 codes wired through `CurrencyFormat` lookup (Arabic + English labels per currency). User picks display currency in Settings → persisted via `UserPreferencesRepository.displayCurrency` (DataStore). `Money.zero()` default removed in favor of explicit currency at every construction site. `AtharNumber` formatter reads `LocalDisplayCurrency` CompositionLocal so every monetary value renders in the user's chosen unit. Multi-currency transactions keep their original currency stored — Money.sumAmounts(items, intoCurrency) handles 1:1 projection (no FX conversion in v1; the brief is explicit that we don't lie about exchange rates).
**Saudi merchant catalog stays:** the brief explicitly says "McDonald's is McDonald's everywhere" — no localization of merchant names.

### G-2 — Income tracker ✅ (beta.6)
**Why:** TMOAP has a 5000-row Income sheet alongside Expenses. Salaried users must see income vs expenses with a savings-rate at a glance to feel the app reflects their life. Without it, expense-only tracking is depressing and incomplete.
**How:** Rather than building a separate `feature/income/` module (over-engineered), we surfaced income as a first-class pill on the Today header alongside Expenses and Net Worth. Manual income entry uses the same `AddTransactionSheet` with an Expense/Income segmented control. `TodayState` now exposes `totalIncome`, `totalExpense`, and a computed `savingsRate` — rendered with olive (positive) / ember (negative) color tint. Income breakdown on Trends uses the same `MonthlyBars` component as expenses.

### G-3 — Recurring transactions ✅ (beta.7–beta.8)
**Why:** Rent on the 1st, salary on the 25th, Netflix on the 15th. Without this, the user re-types the same 6–10 transactions every month. Every modern budgeting app has it. TMOAP doesn't, so this is one place we exceed it.
**How:** New `recurring_rule` Room table with `(merchant, amount, type, cadence, dayOfMonth, dayOfWeek, monthOfYear, nextRunDate, lastRunDate)`. Cadence enum: MONTHLY / WEEKLY / YEARLY. WorkManager `RecurringMaterializationWorker` runs daily, calls `materializeDue(today)` which creates PENDING transactions for any rule whose `nextRunDate <= today`, then advances `nextRunDate` to the next occurrence. Pending transactions land in the Today pending-tray; user confirms with one tap (per the brief's "never auto-confirm" rule). **Beta.8 addition:** auto-detection — `RecurringDetector` scans the user's last 6 months of confirmed transactions, groups by merchant+amount, and emits `RecurringSuggestion`s for any pattern that repeats ≥3 times. User confirms → `acceptSuggestion()` upserts as a real rule.

### G-4 — Multi-account + net worth ✅ (beta.9)
**Why:** TMOAP users track checking + savings + credit card + investments separately. Without multi-account, the app's net-worth number is a single black box and credit-card debt is invisible. A user wants: "my net worth is X across these specific accounts."
**How:** Room v4→v5 migration adds 6 columns to the existing `account` table — `openingBalanceMinor`, `openingBalanceCurrency`, `notes`, `sortOrder`, `archivedAt`, `updatedAt`. Legacy `DEBIT/CREDIT` enum values remapped to `CHECKING/SAVINGS/CREDIT_CARD/CASH/INVESTMENT/OTHER` via a SQL `UPDATE` in the migration. New `AccountRepositoryImpl.observeNetWorth(displayCurrency)` flat-maps the account list with `TransactionDao.observeBalancesByAccount()` and returns a `NetWorth(total, byCurrency, accounts)` flow. The Today header now shows a third pill ("Net Worth · 8,709.68 SAR") with an `ink` accent. New Settings → Accounts CRUD screen lets the user add, rename, edit opening balance, archive, or delete (with FK-RESTRICT protection — accounts with transactions can't be deleted, they can only be archived). The MANUAL_ACCOUNT_ID seed cash account is non-deletable but renameable. Mixed-currency net worth shows a caption noting the limitation (no FX projection in v1).

### G-5 — Localization (English + Arabic) ✅ (beta.9–beta.10)
**Why:** A non-Arabic reader literally cannot use the app — every label is `AtharText(text = "حسنًا")` in code. Even Arabic readers in tri-script environments (Saudi diaspora in the US, for example) often prefer English UI. This is the single highest-impact change for global reach.
**How:** Five-layer fix, each addressing a different gap that surfaced during implementation:
1. **Locale picker + persistence** — Settings card with 3 options (Follow system / Arabic / English). Persisted in DataStore (`appLocale`) and mirrored in SharedPreferences so `LocaleHelper.wrap()` can read it synchronously from `attachBaseContext` before any Composable mounts.
2. **Configuration override** — `LocaleHelper.wrap()` creates a configuration context with `setLocale(locale)` + explicit `setLayoutDirection(locale)` and returns it from `MainActivity.attachBaseContext`. The explicit `setLayoutDirection` matters: API 28+ does not propagate layoutDirection from a programmatic `setLocale` alone.
3. **Compose LocalLayoutDirection** — even with the Configuration override applied, Compose reads layoutDirection from a `CompositionLocal`, not the wrapped Configuration. We wrap the entire `AtharApp` in `CompositionLocalProvider(LocalLayoutDirection provides if (Locale.getDefault().language == "ar") Rtl else Ltr)`. Without this, English text renders correctly but RTL stays glued — dots on the right, FAB on the left, etc.
4. **String extraction** — ~300 strings across `app/`, `core/design-system/`, `feature/today/`, `feature/trends/`, `feature/plan/`, `feature/settings/`. Each module owns its own `res/values/strings.xml` (Arabic default) + `res/values-en/strings.xml`. Helper functions called from `@Composable` scopes (`accountTypeLabel`, `cadenceLabel`, `typeLabel`, `categoryLabel`) became `@Composable` so they can call `stringResource()`.
5. **ViewModel-emitted strings** — ViewModels must stay Context-free for testability. So instead of `errorMessage: StateFlow<String?>` we emit `error: StateFlow<AccountError?>` (sealed enum) and the Composable does the `when()` to resolve. Backup status moved from `Success(message)`/`Failure(reason)` to typed variants (`ExportSuccess`, `ImportSuccess`, `ExportFailure(detail?)`, `ImportFailure(detail?)`). For auto-detected recurring rules' notes field (which gets stored in DB), the `acceptSuggestion(suggestion, notes: String?)` signature now takes the resolved string from the call site.

**Residual debt (acceptable):** seed account name "النقدي" and SMS self-transfer merchant string get persisted to DB at write-time, so existing users keep their old language for old records. New records would need `@ApplicationContext` injection into `SmsIngestionPipeline` or a sentinel-resolve-at-render pattern. Both are minor and deferred. See `docs/adr/ADR-007-localization.md` for the full rationale.

---

## Tier 2 — Significantly improves UX for everyone (high value, ~2 weeks)

### G-6 — Custom date-range selector on Trends and Plan ✅ (beta.9)
**Why:** TMOAP lets users pick any start+end date. "Show me April 2024 → June 2024." Locking Plan to the current calendar month makes it useless for anyone reviewing quarterly or YTD spending.
**How:** Plan now mirrors Trends's segmented control (Month / 3 months / Year / Custom). Selecting Custom opens a bottom-sheet date-range picker with validation (start ≤ end, both within a sensible window). When the range isn't a multiple of one month, monthly category targets scale by `daysInRange / 30.4375` so budget-vs-actual stays apples-to-apples (e.g., 3-month range → target × 2.92, with a caption "× 2.9 multiplier" so the user understands what they're seeing). State holders: `PlanPeriodKey { MONTH, MONTHS_3, YEAR, CUSTOM }` + two MutableStateFlows (`selectedKey`, `customRange`) flat-mapped through the budget calculator.

### G-7 — Bills calendar
**Status:** Not in app.
**Gap:** A user wants to see "What bills are due this week?" without searching transactions.
**Why:** Recurring rules already exist (G-3), but the user has to mentally chain "Netflix on the 15th, rent on the 1st, gym on the 5th" to know what week is going to be expensive. A calendar view collapses that mental work into one glance.
**How (planned):**
- Plan → Bills tab: lists upcoming recurring transactions in date order, plus uncategorized pending (which often are bills the user hasn't tagged yet).
- Visual calendar view (month grid with ember dots on bill days).
- Push notifications 2 days before each bill, on the bill day, and once if missed (per brief: no guilt-trip nags — single reminder, no streak shaming).

### G-8 — Manual transaction UX improvements
**Status:** AddTransactionSheet exists but is barebones.
**Gap:** Users without SMS need to enter every transaction by hand. Friction must be minimal.
**Fix:**
- Recent merchants autocomplete (top 20 by frequency).
- Quick-add chips: "Coffee 25" → expense, restaurant category, current account.
- Receipt photo attachment (local-only, encrypted).
- Voice entry ("Spent 50 on lunch at McDonalds").

### G-9 — Better Trends — drill-down + monthly bars ✅ (beta.6)
**Why:** TMOAP's killer feature is the monthly bars: "Income by Month / Expenses by Month / Savings by Month." Without those three charts, a user has no sense of trajectory. Adding per-category drill-down lets the user answer "is my coffee habit creeping up?" without scrolling raw transactions.
**How:** Three vertically-stacked `AtharMonthlyChartWithLines` instances on Trends — one each for income (olive bars), expenses (ember bars), and savings (ink bars). Each shows last-12-months with a dashed average line and (for expenses) a solid target line if the user has set one in Plan. Per-category drill-down on tap: opens a sheet with a 12-month bar mini-chart for that category alone + top-5 merchants. Pie charts remain banned per Master Brief §2.4 — `AtharProportionBar` is the categorical-breakdown component (horizontal stacked bar with segment labels).

### G-10 — Savings rate goals + emergency fund
**Status:** Wishlist exists. No "savings rate" goal, no "emergency fund" goal.
**Gap:** A common budgeting practice is "I want to save 20% of my income" or "I want 6 months of expenses in emergency fund."
**Fix:**
- Plan → Goals tab (new): savings-rate target, emergency-fund target.
- Today screen: progress ring + nudge if user is on / off track.

---

## Tier 3 — Differentiators that exceed TMOAP (long tail, ~3+ weeks each)

### G-11 — Notification-based ingestion (Play-Store-eligible build)
**Status:** `notification-listener` module exists in `ingestion/`. Wired but not battle-tested.
**Gap:** The Saudi-sideload flavor reads SMS directly. The Play-Store flavor must use `NotificationListenerService` — needs more bank-app coverage.
**Fix:** Add notification handlers for Apple Wallet, Google Pay, Revolut, Wise, N26, Monzo, Starling, Chase, Capital One, Mercury, plus regional non-Saudi banks.

### G-12 — Bank statement / CSV / OFX import
**Status:** CSV import exists for a single hand-rolled column layout.
**Gap:** Banks export CSV, OFX, QFX with different shapes. Users on every continent need to be able to drag in their statement.
**Fix:**
- Import wizard: auto-detect format (CSV / OFX / QFX / MT940), preview rows, let user map columns to fields.
- Support multi-currency statements.

### G-13 — Sync to companion devices (zero-knowledge)
**Status:** Single-device only (encrypted local Room + manual backup export).
**Gap:** A user with a phone + iPad + laptop wants to see their data on all three.
**Fix:**
- End-to-end encrypted sync via a user-supplied object-storage backend (Dropbox / Drive / iCloud / WebDAV / S3). User holds the encryption key. We never see plaintext.
- Optional. Off by default.

### G-14 — Public API for accountants / tax export
**Status:** No API.
**Gap:** End-of-year, a user wants to hand their CPA a clean expense report.
**Fix:**
- "Export for tax" produces a PDF with each category's annual total + transaction list, in the user's locale.

### G-15 — Family / shared budgets
**Status:** No multi-user concept.
**Gap:** A spouse wants to see joint expenses without one of them shouldering the data entry.
**Fix:**
- "Shared notebook" — a second device adds shared categories. Sync via G-13.

### G-16 — Investment portfolio tracker
**Status:** "Family Investments" pool exists; it's a single-shot snapshot of contributors and total return. Not a portfolio.
**Gap:** A user with stocks / ETFs / crypto wants to see live performance.
**Fix:** Out of scope — Athar is a budgeting app, not a brokerage app. Could optionally accept a manual portfolio snapshot.

---

## Tier 4 — Polish / future

### G-17 — Widgets (Today net flow, month-vs-budget, next bill due).
### G-18 — Apple Watch / Wear OS quick-add.
### G-19 — Voice-controlled entry ("Hey Athar, spent 200 SAR at Othaim").
### G-20 — Tax-deduction tracking (flag deductible expenses).

---

## What we deliberately won't build

- **A backend for analytics.** Master Brief §2.2: no cloud, no telemetry.
- **Pie charts.** Master Brief §2.4 bans them.
- **Streak gamification / shaming notifications.** Master Brief §2.2.
- **In-app purchases / ads.** Master Brief §2.2.
- **ML model fine-tuned on user data without explicit consent.** ADR-005 keeps any future ML on-device only.

---

## Tier 1 retrospective — what we shipped

The global sprint Tier 1 is complete (G-1 currency, G-2 income, G-3 recurring + auto-detect, G-4 net worth, G-5 localization, G-6 custom date ranges, G-9 monthly bars). Total elapsed: ~5 sessions. The original 14-day estimate was reasonable for a senior engineer working alone; with parallel sub-agents most steps ran in 2–3× wall-clock concurrency.

**Verified end-to-end on emulator** in both Arabic (RTL) and English (LTR) locales — see `docs/screenshots/`.

The current Saudi-specialized features (SMS parser, AlRajhi/STC/D360/Barq templates, merchant catalog, Hijri toggle, Thmanyah typeface) remain intact — they're now "best-in-class for Saudi users, available to anyone."

## Suggested next sprint (the "polish + reach" sprint)

| # | Feature | Effort | Why |
|---|---|---|---|
| 1 | G-7 bills calendar | 2 days | Recurring rules already exist; calendar view unlocks the value |
| 2 | G-8 manual transaction UX | 2 days | Friction for non-Saudi users (no SMS) — autocomplete, quick-add chips |
| 3 | G-10 savings-rate goals | 2 days | TMOAP doesn't have it — clear differentiator + matches FIRE/financial-independence crowd |
| 4 | G-11 broader notification handlers | 3 days | Play-Store eligibility for non-Saudi (Wise, Revolut, Chase, Mercury, etc.) |
| 5 | G-5b residual seed/SMS strings | 0.5 day | Inject `@ApplicationContext` into `SmsIngestionPipeline` for new self-transfer transactions; convert seed account name to a sentinel resolved at render |

Total: ~9.5 person-days.

## How to continue this work (for any AI or developer)

1. **Read `Athar_Master_Brief.md` first.** It's the single source of truth. The brief explicitly says: "When the brief and reality disagree, edit the brief first, then the code."
2. **Read this file** to see what shipped and why.
3. **Check `Athar_Backlog.md`** for ticket-by-ticket execution order on remaining items.
4. **Before writing Kotlin/Compose/Gradle code:** invoke the matching skill from `.claude/skills/` (`kotlin-project-feature-implementation`, `kotlin-project-state-management`, etc.). The routing table is in `CLAUDE.md` at the repo root.
5. **Spec-first workflow:** write `docs/specs/{ID}-{slug}.md` (200–500 words) before coding. Acceptance criteria, edge cases, where the code lives, what tests cover it.
6. **Build commands:** see `CLAUDE.md` § "Build & test commands". The `personalFullSms` flavor has SMS permissions (sideload only); `storeSafe` flavor is for Play Store (no SMS).
7. **Local-first stays.** No backend. No telemetry. No cloud. Master Brief §2.2 is non-negotiable.
8. **When in doubt, ask before writing.** The brief is opinionated and the design tokens are tight (8 sizes, 8 spacings, 1 accent color). Don't introduce new tokens without updating the brief.
