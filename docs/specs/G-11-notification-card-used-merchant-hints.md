# G-11 - Notification Card-Used Merchant Hints

## Problem

Some bank and wallet push notifications describe posted card spend with a
generic card-used action, then put the merchant before the amount:

- `Card used: Lulu SAR 55.00`
- `Card ending in 1234 was used - Amazon USD 12.99`

The generic notification parser already treats card-used copy as expense
evidence. However, when the merchant appears after the card-used status label
instead of after `at`, `to`, or a structured `Merchant:` field, the row can
arrive without a merchant. That creates a pending transaction that still needs
manual cleanup and cannot train category rules from the merchant label.

## Decision

- Keep the known finance-app package gate unchanged.
- Keep card-used wording as posted expense evidence, including the common
  `ending in 1234` variant.
- Add a merchant-before-amount extractor for clear `card used:` and
  `card ending ... was used -` labels.
- Preserve existing request, security-code, authorization-hold, marketing,
  balance, and transfer guards.

## Acceptance

- `Card used: Lulu SAR 55.00` parses as an expense with merchant `Lulu`.
- `Card ending in 1234 was used - Amazon USD 12.99` parses as an expense with
  merchant `Amazon`.
- Existing card-transaction, status-prefix, recipient-label, transfer, and
  false-positive guard coverage keeps passing.

## Non-goals

- Do not parse arbitrary notification packages.
- Do not infer a merchant from card-used copy without a clear colon/dash label
  or another existing merchant hint.
- Do not change categorization rules or seed data in this slice.

## Validation

- `:ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`
- `:ingestion:sms-parser:test :ingestion:notification-listener:test`
- `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Bounded runtime QA was attempted against the built `storeSafeDebug` APK.
  The local emulator still cannot reach an installable online ADB state:
  `emulator -accel-check` reports that the Android Emulator hypervisor driver
  is not installed, `AtharPixelQaApi35Arm` exits because arm64 system images
  are not supported on this x86_64 host, a visible x86 software launch opened
  an emulator crash dialog, and a cleaned hidden x86 software launch stayed
  `emulator-5554 offline` through the bounded wait. Install, screenshot, UI
  dump, and logcat capture could not run. Evidence is under
  `build/qa/notification-card-used-merchant-hints-emulator*`, and cleanup
  verification finished with no adb/emulator/qemu/netsim process and no AVD
  lock files.
