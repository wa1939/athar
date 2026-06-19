# G-11 - Notification Debt Payment Labels

## Problem

SMS bank templates already emit shared debt labels such as `Credit card payment`
and `Loan instalment`, and the seed rules categorize those labels as debt.
Store-safe bank notifications can report the same posted repayments, but generic
notification parsing may leave them without a reusable merchant label or may
mistake a card last-four near a currency marker for the transaction amount.

Nearby false positives are due reminders and scheduled repayment notices. Those
contain amounts, but they are not posted money movement and should not create
pending rows.

## Decision

Keep the existing known-finance-package gate and add only parser-level behavior:

- Normalize posted credit-card repayment notifications to merchant
  `Credit card payment`.
- Normalize posted loan instalment/payment notifications to merchant
  `Loan instalment`.
- Ignore card/loan repayment due, reminder, scheduled, and upcoming notices
  before amount parsing.
- Skip card/account identifier numbers such as `card ending 2106` during amount
  selection, including when a currency token follows the identifier before the
  real amount.

## Acceptance

- `Credit card payment SAR 500.00 posted` parses as an expense with merchant
  `Credit card payment`.
- `Payment to your credit card ending 2106 USD 250.00 successful` parses amount
  `USD 250.00`, not card identifier `2106`.
- `Loan installment of AED 1,000.00 debited` parses as an expense with merchant
  `Loan instalment`.
- Arabic `تم سداد بطاقة ائتمانية بمبلغ ٥٠٠ ر.س` parses as an expense with
  merchant `Credit card payment`.
- Loan/card due reminders with amounts are ignored.
- Existing card-payment-to-merchant, random-package, balance, reward, security,
  authorization-hold, ATM, and bank-fee guards keep passing.

## Non-goals

- Do not add new package identifiers.
- Do not change SMS parser debt-payment behavior.
- Do not classify arbitrary non-finance-app notifications.
- Do not infer account transfers or multi-account routing from notification
  text; this slice only gives posted repayment rows existing shared labels.

## Validation

- 2026-06-14: focused `GenericBankNotificationTemplateTest` passed with JDK 21,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: `:ingestion:sms-parser:test` and
  `:ingestion:notification-listener:test` passed with JDK 21, `--no-daemon`,
  and `--max-workers=1`.
- 2026-06-14: full stack passed with JDK 21:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon
  --max-workers=1`.
- 2026-06-14: runtime `storeSafeDebug` install was attempted on
  `AtharPixelQaApi35`. `emulator -accel-check` still reports that the Android
  Emulator hypervisor driver is not installed. The bounded hidden software
  launch (`-gpu swiftshader_indirect -accel off`) exposed only
  `emulator-5554 offline` for 185 seconds, so APK install, launch, screenshot,
  and logcat capture could not run. The new emulator/qemu processes and stale
  AVD lock files were cleaned afterward; final cleanup had no attached ADB
  device, no emulator/qemu/netsim process, and no AVD lock files.
