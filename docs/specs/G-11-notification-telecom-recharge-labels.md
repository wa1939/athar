# G-11 - Notification Telecom Recharge Labels

## Problem

Generic store-safe notification parsing treats `top up` as income so wallet
funding notifications can be captured. That creates a bad edge case for bank
pushes about mobile, prepaid, or airtime top-ups: posted telecom recharge
spending can be misclassified as income, or land without a reusable merchant
label when no carrier is present.

Nearby false positives include bonus, bundle, discount, and "next top-up" promo
copy that mention a recharge amount but are not posted money movement.

## Decision

Keep the known-finance-package gate unchanged and add narrow parser behavior:

- Treat explicit mobile, phone, prepaid, and airtime recharge/top-up copy as an
  expense before the generic income `top up` signal runs.
- Normalize generic posted recharge copy to `Mobile recharge`.
- Preserve specific carriers when present, such as `Mobily`.
- Keep non-telecom wallet/account top-up copy as income.
- Ignore telecom recharge promotional or reminder-style copy with amounts.
- Add one exact priority-90 seed rule for `mobile recharge` in `cat-telecom`.

## Acceptance

- `Mobile top up SAR 50.00 completed` parses as an expense with merchant
  `Mobile recharge`.
- `Airtime purchase AED 25.00 completed` parses as an expense with merchant
  `Mobile recharge`.
- `Mobile recharge paid to Mobily SAR 50.00` preserves merchant `Mobily`.
- `تم شحن رصيد الجوال بمبلغ ٥٠ ر.س` parses as an expense with merchant
  `Mobile recharge`.
- `Wallet top up SAR 100.00 from Bank Account` remains income.
- Mobile recharge promotional copy with an amount is ignored.
- `seed_rules.json` grows from 712 to 713 active rules, and the new pattern is
  unique and references an existing category.

## Non-goals

- Do not add telecom app package identifiers.
- Do not change SMS recharge parsing.
- Do not add broad seed patterns such as `mobile`, `recharge`, `airtime`, or
  `top up`.
- Do not reclassify generic wallet/account top-ups without telecom wording.

## Validation

- `.\gradlew.bat --console=plain :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest" :core:data:testDebugUnitTest --no-daemon --max-workers=1`
- `.\gradlew.bat --console=plain :ingestion:sms-parser:test :ingestion:notification-listener:test --no-daemon --max-workers=1`
- `.\gradlew.bat --console=plain test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon --max-workers=1`
- Runtime `storeSafeDebug` install/launch was attempted on
  `AtharPixelQaApi35`. `emulator -accel-check` reported that the Android
  Emulator hypervisor driver is not installed, and the bounded software launch
  stayed `emulator-5554 offline`. APK install, screenshot capture, and logcat
  capture could not run. Stale emulator/qemu/netsim processes and AVD locks were
  cleaned afterward. Runtime logs are under
  `build/qa/notification-telecom-recharge-emulator/`.
