# G-11 - Notification Recurring Expense Labels

## Problem

Generic store-safe bank notifications can announce posted recurring expenses with
very generic copy, such as `Subscription payment`, `Insurance premium`, or
`Rent payment`. Without a reusable merchant label, those rows can land in
Pending with weak or empty merchant text, so first-run users do not benefit from
the active seed categories for subscriptions, insurance, and rent.

Nearby false positives include renewal notices, due reminders, and upcoming
payment reminders that contain an amount but are not posted money movement.

## Decision

Keep the known-finance-package notification gate unchanged and add narrow
parser-level normalization:

- Normalize explicit posted subscription payment notifications to
  `Subscription payment`.
- Normalize explicit posted insurance premium notifications to
  `Insurance premium`.
- Normalize explicit posted rent payment notifications to `Rent payment`,
  including a conservative Arabic rent-payment shape.
- Preserve specific merchants when a party is present, such as `Tawuniya`.
- Ignore recurring-expense due, renewal, scheduled, upcoming, and reminder copy
  before amount parsing.
- Add exact priority-90 seed rules for the three shared labels. Avoid broad
  substring rules such as `rent`, `premium`, or `subscription`.

## Acceptance

- `Subscription payment USD 39.00 completed` parses as an expense with merchant
  `Subscription payment`.
- `Insurance premium AED 500.00 completed` parses as an expense with merchant
  `Insurance premium`.
- `Insurance premium paid to Tawuniya AED 500.00` preserves merchant
  `Tawuniya`.
- `Rent payment SAR 2,500.00 completed` parses as an expense with merchant
  `Rent payment`.
- `تم سداد إيجار بمبلغ ٢٥٠٠ ر.س` parses as an expense with merchant
  `Rent payment`.
- Recurring expense due, renewal, upcoming, and reminder notices with amounts
  are ignored.
- `seed_rules.json` grows from 709 to 712 active rules, and the new patterns
  are unique and reference existing categories.

## Non-goals

- Do not add new notification package identifiers.
- Do not change SMS recurring expense parsing.
- Do not add broad generic seed patterns.
- Do not infer a category for arbitrary landlord, insurer, or subscription
  merchant names without explicit posted recurring-payment wording.

## Validation

- `.\gradlew.bat --console=plain :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest" --no-daemon --max-workers=1`
- `.\gradlew.bat --console=plain :core:data:testDebugUnitTest --no-daemon --max-workers=1`
- `.\gradlew.bat --console=plain :ingestion:sms-parser:test :ingestion:notification-listener:test --no-daemon --max-workers=1`
- `.\gradlew.bat --console=plain test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon --max-workers=1`
- Runtime `storeSafeDebug` install/launch was attempted on
  `AtharPixelQaApi35`. `emulator -accel-check` reported that the Android
  Emulator hypervisor driver is not installed, and the bounded software launch
  stayed `emulator-5554 offline`. APK install, screenshot capture, and logcat
  capture could not run. Stale emulator/qemu/netsim processes and AVD locks were
  cleaned afterward. Runtime logs are under
  `build/qa/notification-recurring-expense-emulator/`.
