# G-11 - Notification Travel Labels

## Problem

Store-safe notification parsing can preserve named travel merchants, but some
bank apps emit generic posted travel copy with no reusable merchant:

- `Flight ticket payment USD 420.00 completed`
- `Hotel payment USD 180.25 completed`
- `Travel booking payment CAD 300.00 posted`
- `Car rental payment USD 89.90 completed`

Those rows parse as valid expenses, but without stable shared labels they still
require manual categorization. Travel notifications also contain many
non-posted messages with amounts: offers, itineraries, reservations, quotes,
check-in prompts, boarding-pass messages, and reminders.

## Decision

- Add conservative posted travel payment detectors behind the existing known
  finance-package notification gate.
- Normalize generic posted copy to exact shared labels:
  - `Flight ticket`
  - `Hotel payment`
  - `Travel booking`
  - `Car rental payment`
- Preserve specific merchants/providers when present, such as `Qatar Airways`.
- Ignore non-posted travel offer/itinerary/reservation/quote/reminder copy
  before amount parsing.
- Add exact priority-90 seed rules for the shared labels so these rows
  categorize as `cat-travel` without broad `hotel`, `flight`, `travel`, or
  `rental` substring rules.

## Acceptance

- Posted flight-ticket copy parses as an expense with `Flight ticket`.
- Posted hotel-payment copy parses as an expense with `Hotel payment`.
- Posted travel-booking copy parses as an expense with `Travel booking`.
- Posted car-rental copy parses as an expense with `Car rental payment`.
- Specific travel providers override generic labels when present.
- Travel offer, itinerary, reservation, quote, and reminder copy with amounts is
  ignored.
- Seed rules reference existing categories, stay unique, and include the new
  shared labels.

## Non-goals

- Do not add broad public seed rules for words such as `hotel`, `flight`,
  `travel`, `rental`, or `airline`.
- Do not parse arbitrary non-finance notifications.
- Do not infer private/local travel providers from private data.
- Do not treat reservations, itineraries, quotes, check-in prompts, or boarding
  pass notices as posted transactions.

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
  an online ADB device, so install/screenshot/UI-dump/logcat capture could not
  run. Evidence was written to
  `build/qa/notification-travel-labels-emulator/`, and cleanup left no attached
  ADB device and no emulator/qemu/netsim process.
