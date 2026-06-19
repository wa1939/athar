# G-11 - Notification Car-Payment Labels

## Problem

The category model separates car ownership payments from generic debt, matching
the TMOAP-style budget view where a car instalment is tracked differently from a
personal loan or credit-card repayment. Store-safe bank notifications can emit
generic posted auto-finance copy such as:

- `Auto loan payment USD 420.00 posted`
- `Car payment SAR 1,200.00 posted`
- `Vehicle finance installment AED 1,250.00 debited`
- `تم سداد قسط سيارة بمبلغ ١٢٠٠ ر.س`

Without a specific shared label, these rows can fall back to the generic
`Loan instalment` debt label. Nearby false positives include auto-finance
offers, pre-approvals, schedules, due reminders, and estimates that contain
amounts but are not posted money movement.

## Decision

- Add a conservative car-payment detector behind the existing known
  finance-package notification gate.
- Normalize posted car-payment, auto-loan, vehicle-finance, vehicle-lease, and Arabic
  car-instalment copy to exact shared label `Car payment`.
- Keep generic non-car loan repayments mapped to `Loan instalment`.
- Ignore auto-finance offers, due reminders, schedules, estimates, quotes,
  pre-approvals, and eligibility copy before amount parsing.
- Add one exact priority-90 seed rule for `Car payment` so the label categorizes
  immediately without broad `car`, `loan`, or `finance` substring rules.

## Acceptance

- Posted `Auto loan payment USD 420.00 posted` parses as an expense with
  merchant `Car payment`.
- Posted `Car payment SAR 1,200.00 posted` parses as an expense with merchant
  `Car payment`.
- Posted `Vehicle finance installment AED 1,250.00 debited` parses as an
  expense with merchant `Car payment`.
- Arabic posted car-instalment copy parses as an expense with merchant
  `Car payment`.
- Existing generic `Loan installment of AED 1,000.00 debited` still maps to
  `Loan instalment`.
- Auto-finance offers, due reminders, schedules, estimates, and pre-approval
  copy with amounts are ignored.
- Seed rules reference existing categories, stay unique, and include the exact
  shared label.

## Non-goals

- Do not add broad public seed rules for `car`, `loan`, `finance`, `vehicle`,
  or `lease`.
- Do not change SMS parser debt-payment behavior.
- Do not infer auto-loan account routing or ownership state from notification
  text.
- Do not treat offers, pre-approvals, schedules, due reminders, estimates, or
  quotes as posted transactions.

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
  `build/qa/notification-car-payment-labels-emulator/`.
