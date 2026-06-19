# G-11 - Notification Digit-Start Counterparty Hints

## Problem

The digit-starting merchant fixes covered expense merchants, but income and
transfer counterparties can also begin with numbers:

- `3M Payroll paid you USD 250.00`
- `Sender: 3M Payroll`
- `Recipient: 401K Savings`

Structured sender/recipient labels now share the cleaned hint path, but the
peer-payment `paid you` / `sent you` capture still required the counterparty to
start with a letter. That produced a valid income row with a missing
counterparty for digit-starting payer names.

## Decision

Let the peer-payment counterparty capture start with any non-newline character,
then keep using `cleanParty` as the safety gate.

`cleanParty` still requires at least one Latin or Arabic letter after amount and
status cleanup, so digit-starting names such as `3M Payroll` and `401K Savings`
survive while numeric-only identifiers such as `123456789` stay rejected.

## Acceptance

- `3M Payroll paid you USD 250.00` parses as income with counterparty
  `3M Payroll`.
- `123456789 paid you USD 25.00` parses as income with no counterparty.
- `Sender: 3M Payroll` on an incoming transfer parses as income with
  counterparty `3M Payroll`.
- `Recipient: 401K Savings` on a sent transfer parses as a transfer with
  counterparty `401K Savings`.
- `Recipient: 123456789` on a sent transfer parses as a transfer with no
  counterparty.

## Non-goals

- Do not accept numeric-only sender or recipient fields as counterparties.
- Do not broaden package allow-listing.
- Do not change merchant/category normalization or seed rules.
- Do not infer counterparties from arbitrary non-finance notifications.

## Validation

- Focused parser regression first failed on `3M Payroll paid you USD 250.00`,
  returning a valid income row with `counterparty = null`.
- Focused parser regression after the fix:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`
- Parser/listener module tests:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest" :ingestion:notification-listener:test`
- Diff and full stack:
  `git diff --check`
  `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime install/launch attempt:
  the built `storeSafeDebug` APK was ready, `emulator -accel-check` reported
  that the Android Emulator hypervisor driver is not installed,
  `AtharPixelQaApi35` stayed `emulator-5554 offline` through the bounded wait,
  and `:app:installStoreSafeDebug` skipped the offline device and failed with
  `No online devices found`. Cleanup stopped the emulator/qemu children and
  ended with no attached devices and no emulator/qemu/netsim process. Evidence is in
  `build/qa/notification-digit-counterparty-hints-emulator/`.
