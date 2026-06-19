# G-11 - Notification Processed/Settled Merchant Hints

## Problem

The generic store-safe notification parser already preserves merchants from
status-prefixed posted copy such as `Payment successful: Noon SAR 99.00`,
`Transaction completed - Carrefour AED 42.00`, and `Purchase approved: Toast
Box SGD 6.40`. Real bank and wallet push copy also uses `processed` and
`settled` for posted transactions. Those shapes should not become merchant-less
pending rows or parser failures, because missing merchants block seed rules,
local learning, and repeated-recurring suggestions.

## Decision

Extend the existing status-prefixed posted transaction path to treat
`processed` and `settled` as posted status words only in the same bounded
merchant-before-amount shape:

- `Payment processed: <merchant> <amount>`
- `Transaction settled - <merchant> <amount>`
- `Purchase processed: <merchant> <amount>`

Do not broaden generic action detection for free-form `processing` text.
The change is limited to posted status labels followed by a merchant and amount.

## Acceptance

- `Payment processed: Noon SAR 99.00` parses as an expense with merchant `Noon`.
- `Transaction settled - Carrefour AED 42.00` parses as an expense with merchant
  `Carrefour`.
- `Purchase processed: Toast Box SGD 6.40` parses as an expense with merchant
  `Toast Box`.
- Existing scheduled, reminder, and non-posted guards continue to run before
  amount selection.

## Non-goals

- Do not parse vague `payment is processing` notifications.
- Do not change package allow-listing.
- Do not add new seed rules or public merchant categories.

## Validation

- Focused parser regression:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`
- Parser/listener module tests:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test :ingestion:notification-listener:test`
- Diff and full stack:
  `git diff --check`
  `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime install/launch attempt:
  the built `storeSafeDebug` APK was ready, `emulator -accel-check`
  reported that the Android Emulator hypervisor driver is not installed,
  bounded hidden `AtharPixelQaApi35` software launches did not reach an online
  ADB transport, `:app:installStoreSafeDebug` failed with
  `No connected devices!`, and final `adb devices -l` was empty. Evidence is
  in `build/qa/notification-processed-settled-merchant-hints-emulator/`.
