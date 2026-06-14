# G-11 - Notification Gift-Payment Labels

## Problem

Store-safe notification parsing can preserve named merchants, but some bank apps
emit generic posted gift copy with no reusable merchant:

- `Gift card purchase USD 50.00 completed`
- `Gift payment AED 220.00 posted`
- `Flower delivery payment SAR 180.00 to Floward`
- `تم شراء بطاقة هدية بمبلغ ١٠٠ ر.س`

Those rows parse as valid expenses, but without stable shared labels they still
require manual categorization. The same domain also produces free-gift promos,
gift-card balance notices, expiry reminders, delivery reminders, and offers
with amounts that must not become fake expenses.

## Decision

- Add conservative posted gift detectors behind the existing known
  finance-package notification gate.
- Normalize generic posted copy to exact shared labels:
  - `Gift card purchase`
  - `Gift purchase`
  - `Flower delivery`
- Preserve specific florists when present, such as `Floward`.
- Ignore free-gift promos, gift-card balances, expiry/reminder copy, delivery
  reminders, and offers before amount parsing.
- Add exact priority-90 seed rules for the shared labels so these rows
  categorize immediately without broad `gift`, `card`, `flower`, or `delivery`
  substring rules.

## Acceptance

- Posted gift-card purchase copy parses as an expense with
  `Gift card purchase` when no provider is present.
- Posted gift payment copy parses as an expense with `Gift purchase`.
- Posted flower-delivery payment copy parses as an expense with
  `Flower delivery` unless a specific florist is present.
- Specific florists override generic labels when present.
- Arabic posted gift-card purchase copy maps to the same shared label.
- Free-gift promos, gift-card balances, expiry/reminder copy, delivery
  reminders, and offers with amounts are ignored.
- Seed rules reference existing categories, stay unique, and include the new
  shared labels.

## Non-goals

- Do not add broad public seed rules for category words such as `gift`,
  `gift card`, `flower`, `florist`, or `delivery`.
- Do not parse arbitrary non-finance notifications.
- Do not infer private/local recipient or occasion names from notification
  text.
- Do not treat promos, balances, expiry notices, delivery reminders, or offers
  as posted transactions.

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
  `build/qa/notification-gift-payment-labels-emulator/`.
