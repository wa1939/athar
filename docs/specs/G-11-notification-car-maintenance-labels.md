# G-11 - Notification Car-Maintenance Labels

## Problem

Store-safe notification parsing can preserve named automotive providers, but
some bank apps emit generic posted car-maintenance copy with no reusable
merchant:

- `Vehicle service payment SAR 450.00 completed`
- `Oil change payment USD 90.00 at Petromin`
- `Car wash payment AED 30.00 posted`
- `Tyre replacement fee GBP 120.00 paid`

Those rows parse as valid expenses, but without stable shared labels they still
require manual categorization. The same domain also produces quote, estimate,
appointment, reminder, and offer notifications with amounts that must not become
fake expenses.

## Decision

- Add conservative posted car-maintenance detectors behind the existing known
  finance-package notification gate.
- Normalize generic posted copy to exact shared labels:
  - `Car service payment`
  - `Oil change payment`
  - `Car wash payment`
  - `Tire service payment`
- Preserve specific automotive providers when present, such as `Petromin`.
- Ignore non-posted quote, estimate, appointment, reminder, and offer copy
  before amount parsing.
- Add exact priority-90 seed rules for the shared labels so these rows
  categorize without broad `car`, `garage`, `tire`, `tyre`, or `oil`
  substring rules.

## Acceptance

- Posted vehicle service, car service, maintenance, and repair copy parses as an
  expense with `Car service payment` when no provider is present.
- Posted oil-change copy parses as an expense with `Oil change payment`.
- Posted car-wash copy parses as an expense with `Car wash payment`.
- Posted tire, tyre, and replacement-service copy parses as an expense with
  `Tire service payment`.
- Specific providers override generic labels when present.
- Arabic posted oil-change payment copy maps to the same shared label.
- Car-maintenance quotes, estimates, appointments, reminders, and offers with
  amounts are ignored.
- Seed rules reference existing categories, stay unique, and include the new
  shared labels.

## Non-goals

- Do not add broad public seed rules for category words such as `car`,
  `vehicle`, `garage`, `service`, `repair`, `tire`, `tyre`, `oil`, or `wash`.
- Do not parse arbitrary non-finance notifications.
- Do not infer private/local provider names from the private corpus.
- Do not treat quotes, estimates, appointments, reminders, or offers as posted
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
  and `rawBodiesWritten = 0`.
- Full JVM test/build/lint stack passed with JDK 21:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime `storeSafeDebug` install/launch was attempted with the built APK.
  `AtharPixelQaApi35` exited before exposing ADB because x86_64 emulation
  requires hardware acceleration and the Android Emulator hypervisor driver is
  not installed. `AtharPixelQaApi35Arm` exited because arm64 system images are
  unsupported on this x86_64 host. Evidence was written to
  `build/qa/notification-car-maintenance-labels-emulator/`.
