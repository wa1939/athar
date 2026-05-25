# أثر — ATHAR · Backlog v1.0

> Companion to `Athar_Master_Brief.md`. Each row below is a ticketable unit. Estimates are calendar days assuming one developer working with Claude Code in the spec-first loop. Order is execution order; do not skip ahead without reading the dependency column.

Legend
- **P0** = blocks v1 daily-driver use
- **P1** = needed for full Excel parity
- **P2** = polish / future
- **Est** = optimistic working days

---

## Phase 0 — Foundations (Week 1–2)

| ID | Title | Priority | Est | Depends on | Acceptance |
|---|---|---|---|---|---|
| F-01 | Initialize Gradle multi-module project with version catalogs | P0 | 0.5 | — | `./gradlew build` green; `:app`, `:core:*`, `:feature:*`, `:ingestion:*`, `:ml:*` modules present |
| F-02 | Configure GitHub Actions: build + ktlint + detekt + unit tests | P0 | 0.5 | F-01 | PR check fails if any check fails |
| F-03 | Add Hilt + Compose + Material 3 Expressive dependencies | P0 | 0.5 | F-01 | App boots to a blank Material 3 screen |
| F-04 | Build `core:design-system` — palette tokens, typography, spacing | P0 | 1 | F-03 | All tokens accessible via `MaterialTheme.athar.*`; sample preview screen demonstrates ink/parchment/ember |
| F-05 | Compose base components: `AtharText`, `AtharNumber`, `AtharCard`, `AtharListRow`, `AtharBottomBar`, `AtharSegmentedControl` | P0 | 1.5 | F-04 | All have `@Preview` in Arabic + English, light + dark |
| F-06 | Add Paparazzi; snapshot all base components | P0 | 0.5 | F-05 | `./gradlew verifyPaparazziDebug` green |
| F-07 | Add Room with SQLCipher; define `Account`, `Category`, `Transaction`, `CategoryRule`, `WishlistItem`, `InvestmentPool`, `InvestmentContribution`, `SmsMessage` entities | P0 | 1.5 | F-03 | Schema export JSON committed; in-memory test DB CRUDs each entity |
| F-08 | Write `MASTER_BRIEF.md`, `CLAUDE.md`, first two ADRs (ADR-001 stack, ADR-002 local-first) | P0 | 0.5 | F-01 | Files committed, reviewed |
| F-09 | App scaffold: single-activity, Compose Navigation, bottom bar with Today / Trends / Plan, empty screens | P0 | 1 | F-05, F-07 | App boots to Today; bottom nav switches screens |
| F-10 | Seed `Category` table from `assets/seed_categories.json` (88 cats) on first run | P0 | 0.5 | F-07 | Database has 88 categories on fresh install |

**Phase 0 total:** ~8 days

---

## Phase 1 — MVP / Excel parity, manual entry only (Week 3–6)

| ID | Title | Priority | Est | Depends on | Acceptance |
|---|---|---|---|---|---|
| M-01 | Transaction repository + use cases (add, edit, delete, list, filter by period) | P0 | 1 | F-07 | Unit tests cover happy + edge cases; flow returns reactive list |
| M-02 | Today screen: landmark net-flow number, daily list of transactions, FAB to add | P0 | 1.5 | M-01, F-09 | Renders with seeded data; tap row → edit sheet |
| M-03 | Add/edit transaction bottom sheet: amount, merchant, category picker, date, notes | P0 | 1.5 | M-01 | All fields work; Arabic + English entry tested |
| M-04 | Category picker component with search + recent + Arabic names | P0 | 1 | M-03 | Search filters by both EN and AR; ≤200ms response |
| M-05 | Period selector (segmented control: month / 3-month / year / custom) used app-wide | P0 | 1 | F-05 | Selection persists across screens within session |
| M-06 | Trends screen: period selector + category breakdown horizontal bar chart (Vico) | P0 | 1.5 | M-05, M-01 | Bars sorted desc by total, tap a bar → drill into category history |
| M-07 | Category drill-down: line chart of monthly spend in that category for last 12 months | P0 | 1 | M-06 | Drawn with Vico; loads in < 200ms on 1k-tx dataset |
| M-08 | Comparison strip on Trends: current period vs previous, % delta per top 5 categories | P1 | 1 | M-06 | Numbers match the Excel's Historical Comparison sheet for the same data |
| M-09 | Plan screen: list of categories with monthly target, actual MTD, variance pill | P0 | 1.5 | M-01 | Variance pill = ember when over, olive when under, divider otherwise |
| M-10 | Set / edit monthly target per category (long-press → edit) | P0 | 0.5 | M-09 | Target persists, recomputes variance on next render |
| M-11 | Settings screen + drawer entry | P0 | 0.5 | F-09 | Accessible from Today top-right; lists subscreens |
| M-12 | Settings → Categories management: list, edit Arabic + English name, archive, reorder | P0 | 1 | M-04 | Archived categories hidden from picker but kept in DB |
| M-13 | Encrypted backup: export entire DB as `.athar` file (AES-256-GCM, Argon2id passphrase) | P0 | 1.5 | F-07 | Export file decryptable by reference Python tool; restore tested in instrumented test |
| M-14 | CSV import: one-time backfill from your existing Excel | P0 | 1 | M-01 | Maps your 80 categories to seeded categories; preview before commit |
| M-15 | Onboarding flow: 4 screens (welcome → name → currency → first import) | P1 | 1 | M-14 | Skipped if DB has any transactions |
| M-16 | Locale toggle (Arabic / English) without app restart | P0 | 0.5 | F-04 | All strings flip immediately |
| M-17 | Dark mode parity check across all screens | P0 | 0.5 | all above | Paparazzi snapshots in both themes, no token violations |
| M-18 | Maestro flow: add → list → edit → delete a transaction | P0 | 0.5 | M-03 | Passes locally and in CI on emulator |

**Phase 1 total:** ~17 days · cumulative ~25 days

---

## Phase 2 — SMS magic (Week 7–9)

| ID | Title | Priority | Est | Depends on | Acceptance |
|---|---|---|---|---|---|
| S-01 | Build `corpus/sms/` with 30+ anonymized real SMS bodies + expected outputs | P0 | 1 | — | YAML or markdown table parseable by tests |
| S-02 | Define `RawIngestEvent` and pipeline interfaces in `ingestion:` | P0 | 0.5 | F-07 | Pure-Kotlin, JVM-testable |
| S-03 | Al Rajhi purchase template (Arabic + English variants) | P0 | 1 | S-01, S-02 | Parses ≥ 90% of purchase samples in corpus |
| S-04 | Al Rajhi transfer-out template | P0 | 0.5 | S-03 | Parses ≥ 90% of transfer samples |
| S-05 | Al Rajhi deposit template | P0 | 0.5 | S-03 | Parses ≥ 90% of deposit samples |
| S-06 | Al Rajhi refund + balance-alert templates (latter → IGNORED) | P0 | 0.5 | S-03 | Refunds become EXPENSE with negative impact; balance alerts dropped |
| S-07 | Parser dispatcher with template registry + confidence scoring | P0 | 1 | S-03..S-06 | Unit tests: each template scored independently |
| S-08 | Categorizer rules engine (exact, substring, regex, priority) | P0 | 1 | F-07 | Unit tests with seed dictionary |
| S-09 | Seed `seed_rules.json` with ~200 Saudi merchant patterns | P0 | 1 | S-08 | Spot-check 20 merchants on real corpus → expected category |
| S-10 | TFLite classifier scaffolding (load model, run inference, return top-1) | P1 | 1 | F-03 | Returns category id + softmax score; ~2MB model |
| S-11 | Train initial classifier on labeled merchant set (Python pipeline in `tools/`) | P1 | 1.5 | S-09 | ≥ 75% accuracy on held-out test set |
| S-12 | Combined categorizer: rules → classifier → unknown | P0 | 0.5 | S-08, S-10 | Returns `CategorySuggestion` with `source` tag |
| S-13 | SMS BroadcastReceiver (Android) → enqueue `RawIngestEvent` | P0 | 1 | S-02 | Receives test SMS from `adb emu sms send`; persists to DB |
| S-14 | Permission flow: request RECEIVE_SMS + READ_SMS on first run, with clear copy | P0 | 0.5 | S-13 | Denial path: app still works manually, prompt to reconsider in Settings |
| S-15 | One-time SMS backfill: scan last 90 days on first install | P1 | 1 | S-13 | Foreground service with progress; cancellable |
| S-16 | Pending tray UI on Today screen (collapsible card showing count, expandable to list) | P0 | 1 | M-02, S-13 | Pending TXs visually distinct (lighter, marked) |
| S-17 | One-tap confirm: swipe right or tap "✓" on a pending row | P0 | 0.5 | S-16 | Status → CONFIRMED, moves into main feed |
| S-18 | One-tap dismiss: swipe left | P0 | 0.5 | S-16 | Status → DISMISSED, hidden from feed, kept in DB for audit |
| S-19 | Re-categorize with learning prompt: "Always categorize STARBUCKS as Coffee? (Yes / No / Just this one)" | P0 | 1 | S-08, S-17 | On Yes, new `CategoryRule` inserted with priority 100, learnedFromUser=true |
| S-20 | Settings → SMS sources: list configured senders, add / remove | P0 | 0.5 | S-13 | Inactive sources don't ingest |
| S-21 | Settings → Parse failures: list SMS we couldn't parse, with "teach" affordance | P1 | 1 | S-13 | User can supply expected output → adds corpus entry |
| S-22 | Maestro flow: send test SMS → see pending → confirm → see in feed and trends | P0 | 0.5 | S-17, M-06 | Passes locally and in CI |
| S-23 | Audit log: every parsed/ignored/failed SMS retained in `SmsMessage` | P0 | 0.5 | S-13 | Queryable in Settings → "SMS history" |

**Phase 2 total:** ~17 days · cumulative ~42 days

---

## Phase 3 — Polish & analytics (Week 10–11)

| ID | Title | Priority | Est | Depends on | Acceptance |
|---|---|---|---|---|---|
| P-01 | Number count-up animation on Today landmark | P1 | 0.5 | M-02 | Animates ≤ 600ms, ease-out |
| P-02 | List enter/exit animations on Today feed | P1 | 0.5 | M-02 | No jitter; respects reduce-motion accessibility setting |
| P-03 | Empty states with real copy (no illustrations) | P0 | 0.5 | all screens | Today empty: "لا حركات اليوم." Trends empty: "أضف بعض الحركات لرؤية النمط." |
| P-04 | Performance pass: cold start < 800ms on Pixel 6 | P0 | 1 | all above | Macrobenchmark green |
| P-05 | Performance pass: Today render < 100ms warm on 10k tx | P0 | 1 | P-04 | Macrobenchmark + paged list |
| P-06 | RTL parity check: every screen mirrored correctly | P0 | 0.5 | all UI | Walk-through both directions; bug-bash file emptied |
| P-07 | Hijri date display toggle (Settings → Display) | P2 | 1 | F-04 | Today shows both calendars when enabled |
| P-08 | Activity log (Settings) — auto-generated audit of edits | P2 | 1 | M-01 | Each edit writes a row; read-only |
| P-09 | Vision QA review pass (Claude reviews screenshots vs design tokens) | P0 | 1 | all above | Zero token violations |

**Phase 3 total:** ~7 days · cumulative ~49 days

---

## Phase 4 — Wishlist + Family Investments (Week 12)

| ID | Title | Priority | Est | Depends on | Acceptance |
|---|---|---|---|---|---|
| W-01 | Wishlist model: item, cost, current saved, optional months, start month | P1 | 0.5 | F-07 | DAO + repo tests pass |
| W-02 | Savings capacity computation: (monthly income − monthly expenses − reserve) | P1 | 0.5 | M-01 | Computed reactively from last-3-month average |
| W-03 | Wishlist screen: list of items with status pill (NOW / WAIT until YYYY-MM / INFEASIBLE) | P1 | 1 | W-02 | Matches Excel Wishlist outputs for same inputs |
| W-04 | Wishlist add/edit sheet | P1 | 0.5 | W-03 | All fields, with calendar picker for start month |
| W-05 | Investment pool model + contribution model | P1 | 0.5 | F-07 | DAO tests pass |
| W-06 | Investments screen: pool list, expand to show contributors with share %, return, net | P1 | 1.5 | W-05 | Numbers match the Family Investments sheet for same inputs |
| W-07 | Add pool / add contributor sheets | P1 | 1 | W-06 | Validation: contributions must sum to pool corpus |
| W-08 | Plan tab updates: Wishlist and Investments accessible as sections under Plan | P1 | 0.5 | W-03, W-06 | One tap from Plan; back returns to Plan |
| W-09 | Paparazzi snapshots for all new screens | P1 | 0.5 | W-03, W-06 | Green in both locales, both themes |

**Phase 4 total:** ~6.5 days · cumulative ~55.5 days

---

## Phase 5 — Beta + iteration (Week 13+)

| ID | Title | Priority | Est | Depends on | Acceptance |
|---|---|---|---|---|---|
| B-01 | Switch off Excel; declare Athar system of record | P0 | 0 | all of Phases 0–4 | A symbolic, ritual commit. Tag `v1.0`. |
| B-02 | Daily journal: log friction every evening for 2 weeks | P0 | 0 (ongoing) | B-01 | `journal.md` with 14 entries |
| B-03 | Weekly friction-fix commit | P0 | 1 / week | B-02 | One PR per week named `friction: <issue>` |
| B-04 | NotificationListenerService (Path B) — bank app push parsing | P1 | 2 | S-07 | Same `RawIngestEvent` from notifications; emulator-tested |
| B-05 | Auto-snapshot backup every Sunday to two locations | P1 | 1 | M-13 | WorkManager job; user configures destinations |
| B-06 | Subscription detection (heuristic: same merchant + similar amount monthly) | P2 | 2 | S-07 | Settings → Subscriptions list |
| B-07 | Receipt photo OCR via ML Kit (camera + parse → prefill transaction sheet) | P2 | 3 | M-03 | 70%+ accuracy on Saudi receipts |
| B-08 | Multi-account support (add a second account with its own SMS senders) | P2 | 2 | S-20 | Account filter on Today / Trends |
| H-01 | All-Transactions / History screen — full-list view with merchant search + category filter + date range + bulk re-categorize | P1 | 2 | M-01, M-04 | New `feature/history` module (or Settings entry); list shows all CONFIRMED transactions across time; search box filters by merchant substring; category-filter chip row; tap row opens existing EditTransactionSheet; "select multiple → reassign category" bulk action; works on 10k+ rows without jank (LazyColumn + paged DAO). |

**Phase 5 total:** open-ended

---

## Cumulative critical-path estimate

- Working solo, evenings: **~12 calendar weeks** to end of Phase 4.
- Working solo, full-time (1.5x throughput, no other distractions): **~7 calendar weeks**.

These assume Claude Code is doing 80%+ of code authoring with you reviewing and steering. If you write code by hand, double these.

---

## Suggested workflow per ticket (the "loop")

1. Open the ticket. Re-read its row above.
2. Write `docs/specs/{ID}-{slug}.md` — 200–500 words. Goal, inputs, outputs, edges, acceptance.
3. Open Claude Code. Point at the spec + the relevant module(s).
4. Claude proposes: code diff + tests + previews.
5. Build locally. Run tests. Boot emulator. Tap through.
6. Comment on the diff with what's wrong. Iterate 1–3 rounds max.
7. Approve. Squash-merge to `main`. Update `BACKLOG.md` row → strike through.
8. Move to next ticket.

If you find yourself doing more than 3 iteration rounds on a ticket, the spec was wrong. Stop, rewrite the spec, restart.

---

*End of Backlog v1.0.*
