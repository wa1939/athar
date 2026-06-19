# G-11 - Notification Status-Prefix Merchant Hints

## Problem

Some bank and wallet push notifications put the posting status before a
colon/dash merchant label, then the amount:

- `Payment successful: Noon SAR 99.00`
- `Transaction completed - Carrefour AED 42.00`
- `Purchase approved: Toast Box SGD 6.40`

The existing generic notification parser already recognized these as posted
expenses, but it could leave the merchant blank or preserve the status word as
part of the merchant. That creates pending rows that still need manual cleanup
and weakens category-rule learning.

## Decision

- Keep the existing known finance-app package gate unchanged.
- Extend only the merchant-before-amount hint extraction for clear
  status-prefixed posted transaction copy.
- Accept `payment`, `purchase`, `transaction`, `card transaction`, `card
  purchase`, and `debit card transaction` followed by `successful`,
  `completed`, `approved`, `posted`, or `confirmed`, then a colon/dash merchant
  label before the amount.
- Preserve existing request, scheduled-payment, security-code, authorization
  hold, marketing, reward, balance, and random-package guards.

## Acceptance

- `Payment successful: Noon SAR 99.00` parses as an expense with merchant
  `Noon`.
- `Transaction completed - Carrefour AED 42.00` parses as an expense with
  merchant `Carrefour`.
- `Purchase approved: Toast Box SGD 6.40` parses as an expense with merchant
  `Toast Box`.
- Existing notification parser coverage keeps passing.

## Non-goals

- Do not parse arbitrary notification packages.
- Do not infer merchants from status-only copy without a clear colon/dash label.
- Do not change transaction type ordering for transfers, income, or specialized
  shared labels.

## Validation

- `:ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`
- `:ingestion:sms-parser:test :ingestion:notification-listener:test`
- `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Bounded runtime QA was attempted against the built `storeSafeDebug` APK on
  `AtharPixelQaApi35`. The local emulator still cannot reach an online ADB
  state because `emulator -accel-check` reports that the Android Emulator
  hypervisor driver is not installed; the software launch stayed
  `emulator-5554 offline`, so install, screenshot, UI dump, and logcat capture
  could not run. Evidence is under
  `build/qa/notification-status-prefix-merchant-hints-emulator/`, and cleanup
  finished with no adb/emulator/qemu/netsim process and no AVD lock files.
