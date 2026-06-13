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

## Post-Tier-1 user-testing patches (beta.11 → beta.23)

Real-user testing on top of imported SMS history surfaced bugs and one architectural-policy regression that needed fixing before the app could be trusted as a daily-driver replacement for TMOAP. All of these are now in main; full rationale in the ADR list below.

### Shipped fixes

| Build | What | Why | ADR |
|---|---|---|---|
| **beta.12** | H-01 history view — searchable, filterable, every transaction reachable from Settings → "All transactions" | User backfilled 3 months of SMS and had no way to scroll/edit historical transactions. The Today screen only shows current month + this month's recent. Without a history view, mis-categorized records were unreachable. | — |
| **beta.13** | Ember "Pending awaiting review" banner on Today + stale-edit-sheet bug fix | The pending tray was below the fold on first load; users didn't see they had work. Banner is impossible to miss. Also fixed a Compose state bug where opening Tx2 after Tx1 showed Tx1's data because `EditTransactionViewModel.load(tx)` was preserving existing state. | — |
| **beta.14** | Dust-color "%d dismissed today — review" banner + AI triage prompt | Parser false-negatives (auto-dismissed transactions) were invisible. Banner forces them above the fold. AI triage prompt unlocks user-driven bank-coverage expansion without code changes. | — |
| **beta.15** | **Auto-dismiss removed from SMS ingestion pipeline** + one-tap recovery action | The previous policy auto-DISMISSED any successfully-parsed transaction with merchant confidence < 0.50. **Real money silently disappeared from the ledger** every time the categorizer was uncertain. User-reported: *"if the user didn't categorize or delete it, record it — don't lose the money transaction."* Pipeline now lands every parsed transaction in CONFIRMED (categorizer matched) or PENDING (user must decide). DISMISSED is reachable only by explicit user swipe. Recovery action moves legacy DISMISSED rows back to PENDING. | [ADR-008](adr/ADR-008-ingestion-fail-safe.md) |
| **beta.16** | Visible feedback on Save accounts + Create rule from suggestion · TRANSFER segment in edit sheet | "Save accounts" silently persisted last-4 digits with no visible confirmation — users couldn't tell the action worked. "Activate" on a suggestion immediately upserted a rule with no visual feedback (the rule was being created — but the user was looking elsewhere). Edit sheet only offered Expense/Income — users couldn't reclassify a spend as savings. Added 3-second olive toasts, permanent caption ("Saved · N account numbers stored"), and a third TRANSFER segment that hides the category picker. | — |
| **beta.17** | **R-03 · confirm sheet before recurring-rule creation** + **R-04 · Subscriptions UI** (Active / Paused groups + monthly total) + **507 AI-seeded merchant rules** (44 → 551 across 22 categories) | R-03: "Activate" alone is too coarse — the user often needs to override the auto-detected cadence (Monthly vs Yearly) or pick a different category before saving. R-04: the rules screen showed all rules as a flat list — there was no way to say *"this subscription is no longer active"* without deleting the rule entirely (and losing the history that it was a recurring expense). Rule expansion: 44 curated rules covered only the most generic merchants (Starbucks, KFC, Panda) — leaving hundreds of real-world merchants from the user's 1,000-message corpus in UNKNOWN. Running both the developer's and a friend's SMS through an AI categorizer produced 1,310 candidate rules; 507 with confidence ≥0.70 were merged (priority 80 for ≥0.85, priority 60 for ≥0.70). `RuleSeed` now refreshes when the seed JSON rule count changes, so upgraded users get the catalog automatically. | — |
| **beta.18** | **#1 Today landmark shows todayNet not monthNet** · **#2 surfaced account-update errors** · **#4 "Always categorize X" backfills existing dismissed/pending** · **#5a Bulk categorize via external AI (CSV roundtrip)** | #1: the big number under the "اليوم/Today" header was the *month* net, not today's — directly contradicts the Master Brief §3 spec and misled the user. #2: editing the cash account silently failed with "تعذّر تحديث الحساب" and no diagnostic — added Log.e + an errorDetail StateFlow rendered in the card. #4: "Always categorize Hemmah as Home maintenance" only added a forward rule; every existing dismissed Hemmah row stayed stranded. Now `applyCategoryToMatching` runs after `learnFromCorrection` and updates every matching PENDING/DISMISSED row to CONFIRMED + the chosen category. #5a: instead of inlining an AI API key (Option 5b), we shipped a CSV roundtrip — Settings → "Bulk categorize with AI" exports a CSV of every uncategorized transaction, the user runs it through ChatGPT/Claude with the existing AI triage prompt, then imports the filled file back. Each row both updates the transaction *and* seeds a learned CategoryRule per unique merchant. Zero API keys, zero cloud cost, leverages tooling the user already pays for. The bigger picture: every "Always" tap and every CSV import permanently grows the user's local merchant library, so the app gets smarter every week of use. | — |
| **beta.19** | **Account reconciliation** — per-account "تسوية / Reconcile" chip with target-balance sheet; inserts one manual adjustment transaction so the displayed running balance matches the user's bank. | User couldn't update net worth — after first SMS backfill the displayed total didn't match their bank because money had moved to accounts the SMS feed didn't see, and the Edit→opening-balance path was throwing the opaque "تعذّر تحديث الحساب" error from beta.18. Industry-standard fix (Mint/YNAB/GnuCash all do this): instead of silently rewriting history, insert one signed adjustment transaction. INCOME if `target > current`, EXPENSE if `target < current`. Labelled "تسوية يدوية / Manual adjustment", visible in History, recorded in Activity Log. New `AccountRepository.reconcile()` + `ReconcileResult` sealed; new `ReconcileBalanceSheet` (ModalBottomSheet); new `ReconcileToast` with olive/muted/crimson colors per state. Edit→opening-balance stays as a secondary path; beta.18's `errorDetail` will surface its real exception when it next throws. | — |
| **beta.20** | **Community-rule sharing** — Settings → "ساعد المجتمع · شارك القواعد / Help others · share your rules" exports the user's `learnedFromUser=true` rules as JSON and opens a pre-filled GitHub issue. | User asked for a centralized "all-users database" of categorizations so first-time users get smart auto-categorization. **That breaks Master Brief §2.2.** Privacy-preserving alternative: same end result (curated seed grows, first-time-users benefit), zero data collection, zero backend, zero abuse vector. Power users export their personal `(pattern, categoryId, confidence)` tuples; maintainer reviews + merges accepted ones into `seed_rules.json`; next release ships the expanded seed via `RuleSeed`'s existing refresh-on-count-change mechanism (beta.17). The 507 AI-extracted rules already in beta.17 are the prior successful example of this exact flow — now any user can extend it. New `CommunityRulesShareTrigger` + impl using `kotlinx.serialization`; new Settings card; new `.github/ISSUE_TEMPLATE/community-rules.yml`; new `docs/COMMUNITY_RULES_WORKFLOW.md`. | — |
| **beta.21** | **Three Glance home-screen widgets** — Athar · Month (3×2), Athar · Today (2×1), Athar · Pending (3×3). | User wanted home-screen widgets so they can see their numbers without opening the app. Glance (not RemoteViews) because Athar is 100% Compose — reuses Athar's parchment + ember palette directly. New `feature:widgets` module; new `WidgetDataLoader` with Hilt `@EntryPoint` since Glance widgets have no lifecycle owner. One-shot `Flow.first()` per snapshot since widgets re-render on each Glance composition (no long-lived subscriptions from widget code). Tap opens the app via `actionStartActivity(componentName)` resolved flavor-agnostically through `PackageManager`. Pending widget's in-place Confirm/Dismiss action buttons deferred to next release — needs HiltWorker + Hilt-WorkManager wiring that doubled the change footprint. System refreshes widgets at 30-min cadence (`updatePeriodMillis = 1800000`) to handle midnight rollover. | — |
| **beta.22** | **Reconciliation no longer inflates expenses** — `Transaction.isReconciliation()` filter applied to Today, Trends, Plan, and widget aggregations. | A user reconciling a ~50k SAR balance gap was seeing the gap appear as a single 50k EXPENSE row, blowing up the monthly spend total to misleading numbers (e.g. −260,287 SAR). The transaction was correctly affecting net worth (the entire point) but was also being counted as real operating spend, which it is not. Industry pattern (YNAB calls them "Reconciliation Balance Adjustment", Mint "Adjust balance") is to exclude them from spend/income reports while still letting them drive the account balance. Detection uses the existing `sourceRefId = "reconcile-…"` sentinel that `AccountRepository.reconcile()` already set in beta.19 — no schema migration, just filter logic in 4 aggregation sites. Reconciliation transactions remain visible in History and per-account ledger so the audit trail is intact. | — |
| **beta.23** | **Update-availability nudge via Obtainium delegation** — Settings → "تابع التحديثات / Stay up to date" card with **Add to Obtainium** (deep link `obtainium://app/{percent-encoded-json}` that pre-fills Athar's GitHub URL in the Obtainium "Add app" flow), **Install Obtainium** fallback (opens `obtainium.imranr.dev`), and **Releases page** (opens `github.com/wa1939/athar/releases`). | User asked how he and future users would know a new release dropped. Athar has no `INTERNET` permission — adding one to poll GitHub would broaden the trust surface and contradict Master Brief §2.2's offline-first commitment. Standard pattern in the privacy-respecting sideload ecosystem (F-Droid clients, Obtainium, FFUpdater): the update-tracker app polls feeds on the user's behalf in *its* process space. We hand off to Obtainium via a deep link. Fallback chain: `runCatching { startActivity(obtainium://...) }` → if `ActivityNotFoundException` (Obtainium not installed), show toast + open browser to install page. Verified end-to-end on emulator: tap → `START result code=-91` (no handler) → Toast window opened → Chrome launched to `obtainium.imranr.dev`. No new permissions; no new dependencies; no Athar-side polling. Update-checking responsibility lives in the sideload manager forever. | [ADR-009](adr/ADR-009-update-delivery-via-obtainium.md) |
| **post-beta.23** | **Local category rule learning** — repeated confirmed exact-merchant/category history creates private priority-150 exact rules. | The app should get quieter each week without a backend. If a user confirms the same normalized merchant into the same category at least three times, Athar now creates an exact local rule for future ingests. Ambiguous merchants skip or remove auto rules; explicit priority-200 "Always" rules still win; auto rules are not community-exported. | [docs/LOCAL_CATEGORY_LEARNING.md](LOCAL_CATEGORY_LEARNING.md) |

### Known issue carried forward

| ID | What | Fix path |
|---|---|---|
| R-01 (P0) | First big SMS backfill (>100 messages) doesn't propagate to Today/History flows until activity recreate. Room's invalidation tracker is saturated by 7000+ concurrent `pipeline.process(event)` coroutines. | Route SMS dispatcher through a Channel with batched DB transactions (e.g., 50 events per `db.withTransaction { … }`). Debounce flow emissions. Estimated 1 day. Tracked in Phase 5. |
| R-05 (P2) | ~228 rules in the AI-extracted set had no category assigned (`"category": null`) because the AI flagged them low-confidence — these merchants stay UNKNOWN. `AI template and output Categorization/athar_first_user_unknown_merchant_review.json` lists them for manual review. | Open the review file, assign categories by hand (or run a second AI pass with the categorized neighbours as in-context examples), append to `seed_rules.json`. Estimated 2–3 hours of human review per 100 rules. **Note (beta.18):** the new "Bulk categorize with AI" Settings card makes this a self-service flow now — the user can export their own unknowns, send to ChatGPT, and re-import. R-05 stays here as a backlog item for *shipping* an expanded curated seed to all users. |
| R-06 (P1) | Backup-file decryption requires the user's passphrase — we have no offline tooling to inspect an athar-backup file without it. Limits our ability to triage user-reported data issues. | Two paths: (a) ship an opt-in "Export unencrypted snapshot for support" Settings action that writes plaintext JSON only with the user's explicit consent; (b) ask users to share an unencrypted CSV export instead. Path (b) needs no code. |
| R-07 (P2) | Bulk-categorize CSV uses `id` as the row key — if a user re-runs the SMS backfill between export and import, transaction IDs change and the import skips everything. | Either persist a stable hash (merchant_normalized + amount + date + sourceRefId) and match on that, or block re-backfill while a bulk-categorize export is "in flight". Low priority since the typical loop is minutes long. |
| R-08 (P1) | Pending widget's [Confirm] / [Dismiss] / [Categorize] action buttons not implemented in beta.21 — tap currently just opens the app. | Add HiltWorker + Hilt-WorkManager wiring: `AtharApplication implements Configuration.Provider`, inject `HiltWorkerFactory`, register a `WorkerFactory`. Create `ConfirmPendingWorker`, `DismissPendingWorker` (each calls `TransactionRepository.setStatus` then `MonthlyWidget().updateAll(context)` + the other two). Action buttons in `PendingWidget` use `actionRunCallback<ConfirmPendingAction>` with the tx id in `ActionParameters`. Estimated 0.5 day. |
| R-09 (P2) | Widget refresh after in-app data changes runs only at the 30-min system cadence — confirming a pending tx in the app doesn't update the widget for up to 30 minutes. | Add a thin `WidgetRefresher` interface in `core/domain` (no-op default in `core/data`); `feature:widgets` provides the real implementation that calls `updateAll(context)` for all three widgets. `TransactionRepositoryImpl.upsert/setStatus/delete` calls `widgetRefresher.refresh()`. Estimated 1 hour. |

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
