# G-11 - Notification Public-Service And Mobility Non-Posted Guards

## Problem

The public-service and mobility notification labels reduce manual
categorization for posted traffic-fine, government-service, parking, toll, and
transit payments. Some banks and public-service apps also send quote, estimate,
discount, status, application, appointment, or reservation notifications with
real-looking amounts. Copy such as `Traffic fine payment discount SAR 300.00`
or `Transit fare estimate USD 2.75` contains the same domain words as posted
payments but is not posted money movement.

## Decision

Add a narrow parser-level guard before amount selection:

- Ignore traffic/parking-fine, government-service, ministry-fee, parking, toll,
  transit, metro, bus, train, and tram quote/estimate/offer/promo/discount/
  coupon/status/application/appointment/reservation copy with amounts.
- Ignore equivalent Arabic public-service and mobility offer, estimate, status,
  request, appointment, and reservation copy with amounts.
- Keep the existing posted label paths unchanged for `Traffic fine payment`,
  `Government service payment`, `Parking payment`, `Toll payment`, and
  `Transit fare`.
- Keep the existing due/reminder guards in place for public-service and
  mobility notices.

## Acceptance

- `Government service fee estimate AED 150.00` is ignored.
- `Traffic fine payment discount SAR 300.00` is ignored.
- `Parking fee estimate SAR 12.00` is ignored.
- `Transit fare estimate USD 2.75` is ignored.
- `عرض سداد مخالفة مرورية ٣٠٠ ر.س` is ignored.
- Posted `Traffic fine payment SAR 300.00 completed` still parses as
  `Traffic fine payment`.
- Posted `Government service payment AED 150.00 completed` still parses as
  `Government service payment`.
- Posted `Parking payment SAR 12.00 completed` still parses as
  `Parking payment`.
- Posted `Road toll payment AED 8.00 posted` still parses as `Toll payment`.
- Posted `Transit fare USD 2.75 charged` still parses as `Transit fare`.

## Non-goals

- Do not change SMS public-service or mobility parsing.
- Do not add notification package identifiers.
- Do not add seed rules or change the public seed count.
- Do not infer agencies, transit operators, or parking providers from
  non-posted copy.

## Validation

- `.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`
- `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime `storeSafeDebug` install/launch was attempted. The built APK was
  ready, `adb devices -l` listed no connected devices, `emulator -list-avds`
  found `AtharPixelQaApi35` and `AtharPixelQaApi35Arm`, and
  `emulator -accel-check` reported that the Android Emulator hypervisor driver
  is not installed. A bounded hidden `AtharPixelQaApi35` launch exited with
  Windows access-violation code `-1073741819` before any online ADB device
  appeared, so `:app:installStoreSafeDebug` failed with `No connected
  devices!`. Evidence is under
  `build/qa/notification-public-mobility-nonposted-guards-emulator/`.
