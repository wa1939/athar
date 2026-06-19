# G-11 - Notification Mortgage-Payment Labels

## Problem

The category model separates housing debt from generic loan repayment, matching
the TMOAP-style budget view where mortgage or home-finance payments should not
hide inside broad debt cleanup. Store-safe bank notifications can emit posted
home-finance copy such as:

- `Mortgage payment USD 2,100.00 posted`
- `Home loan repayment SAR 3,500.00 debited`
- `Housing finance payment AED 4,800.00 paid`
- `تم سداد قسط عقاري بمبلغ ٤٠٠٠ ر.س`

Without a specific shared label, these rows can fall back to the generic
`Loan instalment` debt label. Nearby false positives include mortgage offers,
pre-approvals, due reminders, schedules, refinance pitches, eligibility copy,
and rate estimates that contain amounts but are not posted money movement.

## Decision

- Add a conservative mortgage-payment detector behind the existing known
  finance-package notification gate.
- Normalize posted mortgage, home-loan, housing/property-finance, and Arabic
  mortgage-instalment copy to exact shared label `Mortgage payment`.
- Keep generic non-mortgage loan repayments mapped to `Loan instalment`.
- Ignore mortgage offers, due reminders, schedules, refinance/rate estimates,
  pre-approvals, and eligibility copy before amount parsing.
- Add one exact priority-90 seed rule for `Mortgage payment` so the label
  categorizes immediately without broad `mortgage`, `loan`, or `finance`
  substring rules.

## Acceptance

- Posted `Mortgage payment USD 2,100.00 posted` parses as an expense with
  merchant `Mortgage payment`.
- Posted `Home loan repayment SAR 3,500.00 debited` parses as an expense with
  merchant `Mortgage payment`.
- Arabic posted mortgage-instalment copy parses as an expense with merchant
  `Mortgage payment`.
- Existing generic `Loan installment of AED 1,000.00 debited` still maps to
  `Loan instalment`.
- Mortgage offers, due reminders, schedules, refinance/rate estimates,
  pre-approval, and eligibility copy with amounts are ignored.
- Seed rules reference existing categories, stay unique, and include the exact
  shared label.

## Non-goals

- Do not add broad public seed rules for `mortgage`, `home loan`, `housing`,
  `property`, `loan`, or `finance`.
- Do not change SMS parser debt-payment behavior.
- Do not infer mortgage account routing, escrow, interest, or ownership state
  from notification text.
- Do not treat offers, pre-approvals, schedules, due reminders, refinance
  pitches, rate estimates, or eligibility copy as posted transactions.

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
  `build/qa/notification-mortgage-payment-labels-emulator/`.
