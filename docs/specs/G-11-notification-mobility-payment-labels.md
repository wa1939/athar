# G-11 - Notification Mobility Payment Labels

## Problem

Generic store-safe bank notifications can announce small transport-adjacent
payments with very generic text such as `Parking payment`, `Road toll payment`,
or `Transit fare`. Those rows can parse as expenses but still lack reusable
merchant labels, so they may land uncategorized even though Athar already has a
public-transport category and seed coverage for concrete operators such as
Riyadh Parking.

Nearby false positives include parking sessions, unpaid toll notices, and
parking fines. Parking fines belong to the public-service/fine label path, not
the mobility-payment label path.

## Decision

Keep the known-finance-package notification gate unchanged and add narrow
parser-level normalization:

- Normalize explicit posted parking payment copy to `Parking payment`.
- Normalize explicit posted road-toll payment copy to `Toll payment`.
- Normalize explicit posted transit, metro, bus, train, or tram fare copy to
  `Transit fare`.
- Preserve specific operators when present, such as `Riyadh Parking`.
- Ignore mobility due, scheduled, upcoming, expiry, unpaid, and reminder copy
  with amounts before amount parsing.
- Keep parking fine/violation payments on the public-service label path.
- Add exact priority-90 seed rules for the three shared labels in
  `cat-public-transport`.

## Acceptance

- `Parking payment SAR 12.00 completed` parses as an expense with merchant
  `Parking payment`.
- `Parking payment SAR 12.00 at Riyadh Parking` preserves merchant
  `Riyadh Parking`.
- `Road toll payment AED 8.00 posted` parses as an expense with merchant
  `Toll payment`.
- `Transit fare USD 2.75 charged` parses as an expense with merchant
  `Transit fare`, not trailing status text.
- `تم دفع تذكرة المترو بمبلغ ٤ ر.س` parses as an expense with merchant
  `Transit fare`.
- Mobility due/reminder/unpaid notices with amounts are ignored.
- `Parking fine payment SAR 120.00 completed` stays on
  `Traffic fine payment`.
- `seed_rules.json` grows from 715 to 718 active rules, and the new patterns are
  unique and reference existing categories.

## Non-goals

- Do not add new notification package identifiers.
- Do not change SMS mobility parsing.
- Do not add broad seed patterns such as `parking`, `toll`, `metro`, `bus`, or
  `train`.
- Do not infer a public-transport category for arbitrary operators without
  explicit posted mobility-payment wording.

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
  no attached ADB device and no emulator/qemu/netsim process. Runtime logs are under
  `build/qa/notification-mobility-payment-emulator/`.
