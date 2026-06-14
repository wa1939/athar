# G-11 - Notification Public Service Labels

## Problem

Generic store-safe bank notifications can announce posted public-service
payments with generic text such as `Traffic fine payment` or
`Government service payment`. Without a reusable merchant label, those rows can
land in Pending without a category even though Athar already has utility/public
service seed coverage for government and Ministry of Interior style fees.

Nearby false positives include fine/fee due reminders and broad words such as
`fine` in unrelated merchants like restaurants.

## Decision

Keep the known-finance-package notification gate unchanged and add narrow
parser-level normalization:

- Normalize explicit posted traffic or parking fine/violation payment copy to
  `Traffic fine payment`.
- Normalize explicit posted government/public-service payment copy to
  `Government service payment`.
- Preserve specific agencies when present, such as `Ministry of Interior`.
- Ignore public-service due, scheduled, upcoming, deadline, and reminder copy
  with amounts before amount parsing.
- Avoid matching broad `fine`, `fee`, or `government` words without posted
  payment context.
- Add exact priority-90 seed rules for the two shared labels in `cat-utilities`.

## Acceptance

- `Traffic fine payment SAR 300.00 completed` parses as an expense with merchant
  `Traffic fine payment`.
- `Government service payment AED 150.00 completed` parses as an expense with
  merchant `Government service payment`.
- `Government service payment SAR 100.00 to Ministry of Interior` preserves
  merchant `Ministry of Interior`.
- `تم سداد مخالفة مرورية بمبلغ ٣٠٠ ر.س` parses as an expense with merchant
  `Traffic fine payment`.
- Public-service due/reminder notices with amounts are ignored.
- `Fine Dining charged your card SAR 80.00` is not normalized to a traffic-fine
  label.
- `seed_rules.json` grows from 713 to 715 active rules, and the new patterns are
  unique and reference existing categories.

## Non-goals

- Do not add new notification package identifiers.
- Do not change SMS public-service parsing.
- Do not add broad seed patterns such as `fine`, `violation`, `government`, or
  `ministry`.
- Do not infer a public-service category for arbitrary agencies without explicit
  posted public-service payment wording.

## Validation

- `.\gradlew.bat --console=plain :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest" :core:data:testDebugUnitTest --no-daemon --max-workers=1`
- `.\gradlew.bat --console=plain :ingestion:sms-parser:test :ingestion:notification-listener:test --no-daemon --max-workers=1`
- `.\gradlew.bat --console=plain test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon --max-workers=1`
- Runtime `storeSafeDebug` install/launch was attempted on
  `AtharPixelQaApi35`. `emulator -accel-check` reported that the Android
  Emulator hypervisor driver is not installed, and the bounded hidden software
  launch exited with Windows access-violation code `-1073741819` before exposing
  an online ADB device. APK install, screenshot capture, and logcat capture
  could not run. Stale AVD locks were cleaned afterward, and final cleanup had
  no attached ADB device and no emulator/qemu/netsim process. Runtime logs are
  under
  `build/qa/notification-public-service-emulator/`.
