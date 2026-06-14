# G-11 - Notification Essential Life Labels

## Problem

Store-safe notification parsing already normalizes generic utility, recurring,
telecom, public-service, and mobility payments to reusable labels that seed
rules can categorize. Common bank-app notifications for medical, pharmacy,
education, school-fee, charity, and zakat payments can be just as generic:

- `Medical payment SAR 220.00 completed`
- `Pharmacy purchase AED 75.00 posted`
- `Tuition payment USD 500.00 completed`
- `Zakat payment SAR 250.00 completed`

Without shared labels, those rows can parse as valid expenses but still require
manual category cleanup. The same domain also has many reminders, appeals, and
due notices that mention amounts but are not posted transactions.

## Decision

- Add conservative essential-life payment detectors behind the existing known
  finance-package notification gate.
- Normalize generic posted medical/pharmacy/education/charity/zakat copy to:
  - `Medical payment`
  - `Pharmacy payment`
  - `Education payment`
  - `Charity donation`
  - `Zakat payment`
- Preserve specific counterparties when the notification provides one, such as
  `Nahdi Care Clinic` or `Red Crescent`.
- Ignore due/reminder/appeal copy before amount parsing, including school-fee
  reminders, medical-bill reminders, donation campaign prompts, and Arabic
  school-fee due notices.
- Keep generic bank-fee parsing from winning over domain-specific Arabic
  `رسوم مدرسية` / education-fee copy.
- Add exact priority-90 seed rules for the shared labels so the rows categorize
  immediately without broad `pharmacy`, `hospital`, `school`, or `donation`
  substring rules.

## Acceptance

- Posted medical payment copy parses as an expense with `Medical payment`.
- Posted pharmacy purchase copy parses as an expense with `Pharmacy payment`.
- Posted tuition/school-fee copy parses as an expense with `Education payment`.
- Posted zakat copy parses as an expense with `Zakat payment`.
- Posted donation copy preserves a specific charity/counterparty when present.
- Essential-life due/reminder/appeal copy with amounts is ignored.
- Seed rules reference existing categories, stay unique, and include the new
  shared labels.

## Non-goals

- Do not add broad public seed rules for words such as `hospital`, `clinic`,
  `pharmacy`, `school`, or `donation`.
- Do not parse arbitrary non-finance notifications.
- Do not infer real charities, schools, or clinics from private/local names.

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
  is not installed and the bounded hidden x86_64 software launch stayed
  `emulator-5554 offline`, so install/screenshot/UI-dump/logcat capture could
  not run. Evidence was written to
  `build/qa/notification-essential-life-labels-emulator/`, and cleanup stopped
  the qemu/netsim process with no attached ADB device remaining.
