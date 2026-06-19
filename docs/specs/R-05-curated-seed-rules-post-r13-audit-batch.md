# R-05 - Curated Seed Rules Post-R13 Audit Batch

## Problem

R-13 reduced private family-device parsed expense rows with missing merchants to
20, so the remaining categorization backlog moved to preserved merchant labels.
A post-R13 aggregate audit also exposed reusable parser-quality issues: Arabic
online-purchase rows could pick an account/card tail from `من:` before the real
merchant in `لدى:`, and Arabic ATM / fee rows kept bank names or account tails as
merchant labels.

## Decision

Keep parser cleanup narrow and sender-scoped:

- Al Rajhi POS/online fallback now prefers `At` / `لدى` / `لـ` merchant labels
  before falling back to `من`.
- Structured bank rows label Arabic cash withdrawals as `ATM Withdrawal`.
- Structured bank account-only fee debits label the merchant as `Bank fees`.
- Al Rajhi generic withdrawal rows also normalize to `ATM Withdrawal`.

Promote only public/common priority-90 seed patterns from the aggregate review:

- Normalized parser labels: `atm withdrawal`, `bank fees`
- Public services/platforms: `ejar`, `temu`
- Merchant variants: `mcdo`, `andalusi`, `seoudi market`, `hofer`, `tap*tamee`,
  `tamwinat`

Private-person strings, account tails, unclear local fragments, and broad words
such as generic `market`, `station`, `company`, or `vehicle` remain excluded.

## Acceptance

- `seed_rules.json` increases from 657 to 667 active rules.
- Every new pattern references an existing category id and is pinned at priority
  90 in the asset test.
- Sanitized parser tests cover account-tail merchant precedence, D360 Arabic cash
  withdrawal labeling, and Jazira account-only fee debit labeling.
- The raw SMS export and temporary audit helper are not committed.

## Private Audit Result

Temporary local audit over `All Conversations 2026-05-27 175517.txt`, using the
app-equivalent template order:

- Records: 8,136.
- Parser totals stayed stable: 3,880 successes, 2,395 parsed expenses, 20 parsed
  expense rows with missing merchants.
- Categorized parsed expenses increased from 1,113 to 1,238.
- Uncategorized merchant expenses decreased from 1,262 to 1,137.

## Validation

- 2026-06-13: `:ingestion:sms-parser:test` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: `:core:data:testDebugUnitTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: full JVM/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug` with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: runtime install/tap-through was attempted, but no ADB
  device was attached. `AtharPixelQaApi35` exists locally, but
  `emulator -accel-check` reports that the Android Emulator hypervisor driver
  is not installed, and a bounded headless software-rendered boot exited with
  `-1073741819` before exposing an ADB target.
