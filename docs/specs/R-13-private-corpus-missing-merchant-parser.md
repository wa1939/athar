# R-13 - Private Corpus Missing-Merchant Parser Hardening

## Problem

R-12 reduced the private family-device SMS export's missing-merchant expense rows
from 3,800 to 474, but the remaining backlog still created poor pending rows:
card settlements were counted as expenses, salaries and own-account transfers were
falling through the universal fallback, and public-service or bill-payment rows often
had enough structure to keep a useful merchant label.

## Decision

Keep the fix in conservative sender-scoped parser paths:

- Treat Arabic Al Rajhi credit-card settlement shapes as `TRANSFER`, including compact
  `بطاقة ...:سداد بـ...` headers.
- Preserve compact Arabic Al Rajhi purchase merchants that use `لـ` with no separator.
- Let Al Rajhi generic fallback classify salary and transfer confirmations correctly,
  and use `سداد فاتورة` / fee descriptions as merchant labels when no biller field
  exists.
- Let structured-bank templates keep `الجهة` / `الخدمة` service labels, classify
  Arabic credit-card settlements as transfers, and parse D360 Arabic SAR-in-parentheses
  amounts through the structured fallback.
- Treat Arabic D360 `تحويل بين حساباتك` rows as transfers, not expenses.
- Ignore narrow account/card administration notices: beneficiary status, savings-account
  activation, inactive-card declines, terms/renewal copy, and statement notices.
- Treat Arabic card cashback as income instead of expense.

The raw export remains private. Only sanitized tests and aggregate counts are committed.

## Acceptance Criteria

- Sanitized corpus tests cover the new Arabic settlement, purchase, salary, transfer,
  bill-payment, D360, SNB, AlJazira, cashback, and admin-notice shapes.
- Existing parser behavior stays green.
- Unknown senders remain ignored; the universal fallback stays restricted to known banks.
- The temporary private audit helper is deleted before commit.

## Private Audit Result

Temporary local audit over `All Conversations 2026-05-27 175517.txt`:

- Records: 8,136.
- Previous tracked parser backlog: 474 parsed expense rows with missing merchants.
- After this slice, using the app-equivalent SMS template order: 3,880 successes,
  2,395 parsed expenses, and 20 parsed expense rows with missing merchants.

The remaining rows are small, mixed, and not safe enough for broad parser assumptions.

## Validation

- 2026-06-13: `:ingestion:sms-parser:test` passed with JDK 17, `--no-daemon`, and
  `--max-workers=1`.
- 2026-06-13: full JVM/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug` with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: runtime install/tap-through was attempted but could not run because
  `adb devices -l` returned no attached devices. The local `AtharPixelQaApi35`
  AVD is present, but `emulator -accel-check` reports the Android Emulator
  hypervisor driver is not installed, and a bounded software-acceleration boot
  exited before exposing ADB.
