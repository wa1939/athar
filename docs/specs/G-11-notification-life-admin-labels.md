# G-11 - Notification Life-Admin Labels

## Problem

Store-safe notification parsing can already preserve clear providers, but some
bank apps emit generic posted life-admin copy with no reusable merchant:

- `Home repair payment SAR 350.00 completed`
- `Gym membership payment GBP 45.00 completed`
- `Daycare fee payment AED 800.00 posted`

Those rows parse as valid expenses, but without stable shared labels they still
land in the user's manual categorization backlog. The same domains also emit
offers, quotes, estimates, trials, and due/reminder copy with amounts that must
not become fake expenses.

## Decision

- Add conservative posted life-admin detectors behind the existing known
  finance-package notification gate.
- Normalize generic posted copy to exact shared labels:
  - `Home service payment`
  - `Gym membership`
  - `Childcare payment`
- Preserve specific providers when present, such as `Handy`.
- Ignore non-posted offer, quote, estimate, scheduled, trial, due, and reminder
  copy before amount parsing.
- Add exact priority-90 seed rules for the shared labels so these rows
  categorize immediately without broad `gym`, `daycare`, `cleaning`, or
  `maintenance` substring rules.

## Acceptance

- Posted home repair, home maintenance, cleaning service, plumbing, electrician,
  and handyman copy parses as an expense with `Home service payment` when no
  provider is present.
- Posted gym, fitness, or health-club membership copy parses as an expense with
  `Gym membership`.
- Posted childcare, daycare, nursery, or preschool fee copy parses as an
  expense with `Childcare payment`.
- Specific providers override generic labels when present.
- Arabic posted home-service payment copy maps to the same shared label.
- Life-admin offers, quotes, estimates, trials, due notices, and reminders with
  amounts are ignored.
- Seed rules reference existing categories, stay unique, and include the new
  shared labels.

## Non-goals

- Do not add broad public seed rules for category words such as `gym`,
  `daycare`, `nursery`, `cleaning`, `plumbing`, or `maintenance`.
- Do not parse arbitrary non-finance notifications.
- Do not infer private/local provider names from the private corpus.
- Do not treat quotes, estimates, trials, or due reminders as posted
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
  `build/qa/notification-life-admin-labels-emulator/`.
