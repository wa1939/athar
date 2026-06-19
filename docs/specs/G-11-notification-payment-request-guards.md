# G-11 - Notification Payment Request Guards

## Problem

Peer-payment and wallet apps can send request notifications that contain a money
amount and transaction-like words without representing posted money movement.
Examples include:

- `Payment request from John for USD 25.00`
- `John is requesting SAR 25.00 payment from you`
- `You have a money request for AED 75.00 from Ahmed`

The generic notification parser already ignores simple `requested` copy, but
`payment request` shapes include `payment`, which is also a posted-transaction
action word. If parsed, these rows become fake expenses and create manual cleanup
work.

## Decision

- Keep the known finance-app package gate unchanged.
- Treat request-shaped notifications with an amount as non-posted events and
  ignore them before amount selection.
- Preserve real posted payment, income, and transfer notifications that do not
  contain request wording.
- Do not broaden arbitrary package parsing or change amount selection for real
  transactions.

## Acceptance

- `Payment request from John for USD 25.00` is ignored.
- `John is requesting SAR 25.00 payment from you` is ignored.
- `You have a money request for AED 75.00 from Ahmed` is ignored.
- Existing posted payment, incoming payment, and transfer notification tests keep
  passing.

## Non-goals

- Do not create a separate pending "request" transaction type.
- Do not infer whether a later request was accepted or paid.
- Do not change peer-payment income parsing such as `sent you` or `paid you`.

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
  `build/qa/notification-payment-request-guards-emulator/`, and cleanup
  finished with no adb/emulator/qemu/netsim process and no AVD lock files.
