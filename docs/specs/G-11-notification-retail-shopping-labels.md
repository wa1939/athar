# G-11 - Notification Retail Shopping Labels

## Problem

Store-safe notification parsing can preserve named retail merchants, but some
bank apps emit generic posted retail copy with no reusable merchant:

- `Clothing purchase GBP 89.99 completed`
- `Electronics purchase USD 399.00 completed`
- `Online shopping payment AED 79.00 completed`

Those rows parse as valid expenses, but without stable shared labels they still
require manual categorization. Retail and marketplace notifications also
produce many non-posted amount-bearing messages: offers, cart reminders, price
drops, shipping updates, delivery status, order-status changes, and pickup
notices.

## Decision

- Add conservative posted retail-shopping detectors behind the existing known
  finance-package notification gate.
- Normalize generic posted copy to exact shared labels:
  - `Clothing purchase`
  - `Electronics purchase`
  - `Online shopping purchase`
- Preserve specific merchants when present, such as `Zara` or `Best Buy`.
- Ignore non-posted retail offer/cart/price-drop/shipping/order-status copy
  before amount parsing.
- Add exact priority-90 seed rules for the shared labels so these rows
  categorize without broad `clothing`, `electronics`, or `shopping` substring
  rules.

## Acceptance

- Posted clothing/apparel/footwear copy parses as an expense with
  `Clothing purchase`.
- Posted electronics/device/computer copy parses as an expense with
  `Electronics purchase`.
- Posted online-shopping/e-commerce/marketplace copy parses as an expense with
  `Online shopping purchase`.
- Specific retail merchants override generic labels when present.
- Retail offer, cart, price-drop, shipping, delivery-status, order-status, and
  pickup copy with amounts is ignored.
- Seed rules reference existing categories, stay unique, and include the new
  shared labels.

## Non-goals

- Do not add broad public seed rules for words such as `clothing`,
  `electronics`, `fashion`, `device`, `shopping`, or `marketplace`.
- Do not parse arbitrary non-finance notifications.
- Do not infer private/local retail merchants from private data.
- Do not treat cart reminders, price drops, shipping updates, delivery status,
  or pickup notices as posted transactions.

## Validation

- Focused parser tests:
  `:ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`.
- Seed asset and data tests:
  `:core:data:testDebugUnitTest`.
- Full parser and listener tests:
  `:ingestion:sms-parser:test` and `:ingestion:notification-listener:test`.
- Forced gated private SMS audit:
  7,887 records, 3,864 parser successes, 4,023 ignored, 0 parser failures,
  0 failed known-bank messages, and 0 missing-merchant parsed expenses.
- Full JVM test/build/lint stack passed with JDK 21:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime `storeSafeDebug` install/launch was attempted against
  `AtharPixelQaApi35`; the built APK was present, but
  `emulator -accel-check` reported that the Android Emulator hypervisor driver
  is not installed and the bounded hidden x86_64 software launch did not expose
  a boot-complete online ADB device, so install/screenshot/UI-dump/logcat
  capture could not run. Evidence was written to
  `build/qa/notification-retail-shopping-labels-emulator/`, and cleanup left no
  attached ADB device and no emulator/qemu/netsim process.
