# G-11 - Notification Home-Goods Labels

## Problem

Store-safe notification parsing can preserve named merchants, but some bank apps
emit generic posted home-goods copy with no reusable merchant:

- `Furniture purchase SAR 900.00 completed`
- `Home goods payment USD 75.00 posted`
- `Appliance purchase AED 1,200.00 at IKEA`
- `تم شراء أثاث بمبلغ ٨٠٠ ر.س`

Those rows parse as valid expenses, but without stable shared labels they still
require manual categorization. The same domain also produces quotes, carts,
wishlists, delivery/order statuses, installation or assembly reminders,
warranty notices, and offers with amounts that must not become fake expenses.

## Decision

- Add conservative posted home-goods detectors behind the existing known
  finance-package notification gate.
- Normalize generic posted copy to exact shared labels:
  - `Furniture purchase`
  - `Home goods purchase`
  - `Appliance purchase`
- Preserve specific stores when present, such as `IKEA`.
- Ignore quotes, carts/wishlists, delivery/order statuses, installation or
  assembly reminders, warranty notices, reminders, and offers before amount
  parsing.
- Add exact priority-90 seed rules for the shared labels so these rows
  categorize immediately without broad `furniture`, `home`, `decor`,
  `appliance`, or `warranty` substring rules.

## Acceptance

- Posted furniture purchase copy parses as an expense with
  `Furniture purchase` when no store is present.
- Posted home-goods payment copy parses as an expense with
  `Home goods purchase`.
- Posted appliance purchase copy parses as an expense with
  `Appliance purchase` unless a specific store is present.
- Specific stores override generic labels when present.
- Arabic posted furniture purchase copy maps to the same shared label.
- Quotes, carts/wishlists, delivery/order statuses, installation or assembly
  reminders, warranty notices, reminders, and offers with amounts are ignored.
- Seed rules reference existing categories, stay unique, and include the new
  shared labels.

## Non-goals

- Do not add broad public seed rules for category words such as `furniture`,
  `home`, `decor`, `appliance`, `installation`, or `warranty`.
- Do not parse arbitrary non-finance notifications.
- Do not infer private/local delivery, installation, warranty, or household
  details from notification text.
- Do not treat quotes, carts, wishlists, delivery/order statuses, installation
  or assembly reminders, warranty notices, reminders, or offers as posted
  transactions.

## Validation

- Focused parser and data tests passed:
  `:ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`
  and `:core:data:testDebugUnitTest`.
- Full parser, data, and listener tests passed:
  `:ingestion:sms-parser:test :core:data:testDebugUnitTest :ingestion:notification-listener:test`.
- Forced gated private SMS audit passed with redacted aggregate output only:
  7,887 records, 3,864 parser successes, 4,023 ignored, 0 parser failures,
  0 failed known-bank messages, 2,374 parsed expenses, 1,276 categorized
  expenses, 1,098 uncategorized expenses, 0 missing-merchant parsed expenses,
  20 hashed uncategorized merchant groups, and `rawBodiesWritten = 0`.
- Full JVM test/build/lint stack passed with JDK 21:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime `storeSafeDebug` install/launch was attempted with the built APK.
  `AtharPixelQaApi35` exited before exposing ADB because x86_64 emulation
  requires hardware acceleration and the Android Emulator hypervisor driver is
  not installed. `AtharPixelQaApi35Arm` exited because arm64 system images are
  unsupported on this x86_64 host. Evidence was written to
  `build/qa/notification-home-goods-labels-emulator/`.
