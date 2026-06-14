# G-11 - Notification Everyday Commerce Labels

## Problem

Store-safe notification parsing can already preserve clear merchants, but some
bank apps emit generic posted spending copy with no reusable merchant:

- `Fuel payment SAR 80.00 completed`
- `Grocery purchase CAD 45.67 posted`
- `Restaurant payment USD 32.10 completed`
- `Coffee shop payment GBP 4.20 completed`
- `Food delivery payment AED 64.00 completed`
- `Taxi ride payment SAR 18.50 completed`

Those rows parse as valid expenses, but without stable shared labels they still
land in the user's manual categorization backlog. The same everyday-commerce
domains also produce offer, estimate, reminder, reservation, and discount copy
with amounts that must not become fake expenses.

## Decision

- Add conservative posted everyday-commerce detectors behind the existing known
  finance-package notification gate.
- Normalize generic posted copy to exact shared labels:
  - `Fuel purchase`
  - `Grocery purchase`
  - `Restaurant payment`
  - `Coffee payment`
  - `Food delivery payment`
  - `Taxi ride payment`
- Preserve specific merchants when present, such as `Aldrees` or `Uber`.
- Ignore non-posted offer/discount/estimate/reservation/reminder copy before
  amount parsing.
- Keep Arabic `خصم` debit copy parseable as posted spending; only Arabic offer
  words such as `عرض` are used for the new non-posted guard.
- Add exact priority-90 seed rules for the shared labels so these rows
  categorize immediately without broad `restaurant`, `coffee`, `taxi`,
  `grocery`, or `fuel` substring rules.

## Acceptance

- Posted fuel copy parses as an expense with `Fuel purchase`.
- Posted grocery/supermarket copy parses as an expense with `Grocery purchase`.
- Posted restaurant copy parses as an expense with `Restaurant payment`.
- Posted coffee/cafe copy parses as an expense with `Coffee payment`.
- Posted food-delivery copy parses as an expense with `Food delivery payment`.
- Posted taxi/ride-fare copy parses as an expense with `Taxi ride payment`.
- Specific merchants override generic labels when present.
- Everyday-commerce promo, estimate, reservation, discount, and reminder copy
  with amounts is ignored.
- Existing Arabic wallet debit copy such as `تم خصم ... لدى متجر القهوة`
  remains a posted expense.
- Seed rules reference existing categories, stay unique, and include the new
  shared labels.

## Non-goals

- Do not add broad public seed rules for category words such as `restaurant`,
  `coffee`, `taxi`, `grocery`, or `fuel`.
- Do not parse arbitrary non-finance notifications.
- Do not infer local/private merchant names from the private corpus.
- Do not treat ride estimates, reservations, or merchant offers as posted
  transactions.

## Validation

- Focused parser tests:
  `:ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`.
- Full parser and listener tests:
  `:ingestion:sms-parser:test` and `:ingestion:notification-listener:test`.
- Seed asset and data tests:
  `:core:data:testDebugUnitTest`.
- Forced gated private SMS audit:
  7,887 records, 3,864 parser successes, 4,023 ignored, 0 parser failures,
  0 failed known-bank messages, and 0 missing-merchant parsed expenses.
- Full JVM test/build/lint stack passed with JDK 21:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime `storeSafeDebug` install/launch was attempted against
  `AtharPixelQaApi35`; the built APK was present, but
  `emulator -accel-check` reported that the Android Emulator hypervisor driver
  is not installed and the bounded hidden x86_64 software launch only exposed
  `emulator-5554 offline`, so install/screenshot/UI-dump/logcat capture could
  not run. Evidence was written to
  `build/qa/notification-everyday-commerce-labels-emulator/`, and cleanup left
  no attached ADB device and no emulator/qemu/netsim process.
