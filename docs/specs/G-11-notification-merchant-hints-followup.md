# G-11 - Notification Merchant Hint Follow-Up

## Problem

Store-safe users depend on bank-app notifications instead of SMS. The generic
notification parser already handles many `spent at`, `charged by`, and card
transaction shapes, but common finance notifications still appear as:

- `You paid $9.99 for Netflix`
- `Debit of USD 23.10 on Trader Joe's`
- `New transaction: Starbucks SGD 6.40`
- Arabic `تم خصم ٣٥٫٥٠ ر.س في كارفور`
- `Credit of USD 250.00 from ACME Payroll`

If these rows parse without a useful merchant/counterparty, local category
learning and seed rules cannot reduce future review work.

## Decision

Keep the existing package allow-list unchanged. This slice only improves parsing
after a notification has already been accepted as a known finance package.

Add conservative merchant/counterparty hint support for:

- English `for` and `on` merchant hints.
- Arabic `في` merchant hints when the following token starts with a letter, not a
  date/amount.
- `transaction: merchant amount` and `new transaction: merchant amount` prefixes.
- `credit of amount from counterparty` income copy.

Do not parse arbitrary notifications from non-finance packages, and keep
security, marketing, scheduled-payment, statement, limit, declined, and request
ignores ahead of amount extraction.

## Acceptance

- Known finance package notifications parse the five examples above with the
  expected type, amount, currency, merchant/counterparty, and template id.
- Existing notification parser tests for security, marketing, scheduled,
  statement, limit, and random-package ignores continue to pass.
- No new package keywords are added in this slice.

## Validation

- 2026-06-13: `:ingestion:sms-parser:test` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: `:ingestion:notification-listener:test` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: full JVM/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug` with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: runtime notification tap-through remains under QA-01. No Android
  device was attached, `AtharPixelQaApi35` exists locally, and
  `emulator -accel-check` reports that the Android Emulator hypervisor driver is
  not installed.
