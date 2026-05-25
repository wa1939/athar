# Roadmap — making Athar a global TMOAP replacement

This document is the answer to: *"What does Athar still need to be useful for any user, anywhere, the way TMOAP is?"*

Athar today is **better than TMOAP for Saudi users** (SMS automation, encrypted storage, Arabic-first UI). It is **worse than TMOAP for everyone else** — the SMS layer is irrelevant, currency is hard-coded SAR, and several TMOAP analysis features aren't shipped.

Below is the prioritized gap list, in delivery order.

---

## Tier 1 — Required for any non-Saudi user (high impact, ~2 weeks)

### G-1 — Currency abstraction
**Status:** Hard-coded SAR throughout (UI symbol `﷼`, `Money.zero()` defaults to SAR).
**Gap:** A user in Egypt, Dubai, India, the US, anywhere — sees Saudi riyals in their dashboard. No way to switch.
**Fix:**
- Pick a primary display currency in onboarding (USD / EUR / GBP / AED / EGP / INR / PKR / TRY / SAR / KWD / QAR / BHD / OMR / JOD / …).
- Persist in `UserPreferencesRepository.displayCurrency`.
- Wire through `Money.zero()` defaults and the `AtharNumber` formatter.
- Multi-currency transactions (foreign-card spend): keep the raw foreign amount AND the user-currency equivalent at the time of transaction; aggregate in user-currency.
- The merchant catalog stays as-is — McDonald's is McDonald's everywhere.

### G-2 — Income tracker as a first-class screen
**Status:** Income exists as `TxType.INCOME` but there's no dedicated UI. Users can confirm income from SMS but can't add it manually with any ergonomics.
**Gap:** TMOAP has a 5000-row Income sheet alongside Expenses. Many users (especially salaried) need to see their income separately and compare against expenses to compute savings rate.
**Fix:**
- New `feature/income/` module OR a tab on Today.
- Manual income entry sheet (similar to AddTransactionSheet but with income categories: Salary, Side income, Investments, Other).
- "Recurring income" (salary every 25th).
- Income breakdown on Trends (we currently have only expense breakdown).

### G-3 — Recurring transactions
**Status:** Not supported.
**Gap:** Rent every 1st, salary every 25th, Netflix every 15th, Spotify family every 3rd. Users do this manually today. TMOAP doesn't either, but every modern budgeting app does.
**Fix:**
- New `recurring_transaction` Room table: `template_tx` + `schedule` (monthly / yearly / weekly / cron-like).
- WorkManager job runs daily, materializes upcoming recurring → PENDING transactions for tomorrow.
- "Next 30 days" projection on Plan: actual + recurring = realistic month-end forecast.

### G-4 — Net worth + accounts view
**Status:** `Account` exists in the data model but is single-default ("MANUAL_ACCOUNT_ID"). No UI for multi-account.
**Gap:** TMOAP users track checking + savings + credit card + investments separately. A user wants to see: "My net worth is X across these accounts."
**Fix:**
- Settings → Accounts screen: add / rename / archive accounts (checking, savings, credit, cash).
- Each account has a starting balance + a current balance derived from transactions.
- Net Worth widget on Today: sum of all positive accounts minus credit-card balances.

### G-5 — Localization to English (and French, Hindi, Urdu, Turkish later)
**Status:** Strings are Arabic literals in Compose code. No `strings.xml`. The `localePicker` doesn't exist.
**Gap:** Non-Arabic-readers can't use the app at all.
**Fix:**
- Extract all UI strings into `res/values/strings.xml` + `res/values-en/strings.xml`.
- `LocalLayoutDirection` already switches RTL/LTR — bind to locale.
- Default to system language with Arabic fallback for Gulf locales.
- A "Language" toggle in Settings.

---

## Tier 2 — Significantly improves UX for everyone (high value, ~2 weeks)

### G-6 — Custom date-range selector on Trends and Plan
**Status:** Trends has fixed segments (month / 3 months / year / comparison). Plan is locked to current month.
**Gap:** TMOAP lets users pick any start + end date for analysis. "Show me April 2024 → June 2024."
**Fix:**
- Custom-range bottom sheet on Trends and Plan: start date + end date pickers.
- Save recent ranges as quick-picks (last quarter / YTD / last 6 months).

### G-7 — Bills calendar
**Status:** Not in app.
**Gap:** A user wants to see "What bills are due this week?" without searching transactions.
**Fix:**
- Plan → Bills tab: lists upcoming recurring transactions in date order.
- Visual calendar view (month grid with dots on bill days).
- Push notifications 2 days before each bill, on the bill day, and if missed.

### G-8 — Manual transaction UX improvements
**Status:** AddTransactionSheet exists but is barebones.
**Gap:** Users without SMS need to enter every transaction by hand. Friction must be minimal.
**Fix:**
- Recent merchants autocomplete (top 20 by frequency).
- Quick-add chips: "Coffee 25" → expense, restaurant category, current account.
- Receipt photo attachment (local-only, encrypted).
- Voice entry ("Spent 50 on lunch at McDonalds").

### G-9 — Better Trends — drill-down + monthly bars + pie charts
**Status:** Top-categories bar chart + 12-month drill-down sheet. No pie charts (banned in brief).
**Gap:** TMOAP has monthly bars for income / expenses / savings + per-category drill-down with monthly bars.
**Fix:**
- Monthly Income / Expenses / Savings bar chart (12 months) on Trends — TMOAP's "Income by Month" / "Expenses by Month" / "Savings by Month" equivalent.
- Per-category trend line / mini-chart inline in the comparison table.
- "Top 5 merchants" within a category drill-down.

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

## Suggested next sprint (the "global" sprint)

**One sprint to ship Athar as a global app:**

| # | Feature | Effort |
|---|---|---|
| 1 | G-1 currency abstraction | 3 days |
| 2 | G-5 localization (en + ar baseline) | 2 days |
| 3 | G-2 income tracker | 2 days |
| 4 | G-3 recurring transactions | 3 days |
| 5 | G-4 multi-account + net worth | 3 days |
| 6 | G-6 custom date ranges | 1 day |

Total: ~14 person-days. Result: Athar usable by any user worldwide, in either Arabic or English, with their own currency and multiple accounts and recurring bills.

The current Saudi-specialized features (SMS parser, AlRajhi/STC/D360/Barq templates, merchant catalog, Hijri toggle, Thmanyah typeface) remain intact — they just become "best-in-class for Saudi users, available to anyone."
