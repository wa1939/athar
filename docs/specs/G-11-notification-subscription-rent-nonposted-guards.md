# G-11 - Notification Subscription And Rent Non-Posted Guards

## Problem

The recurring-expense notification parser normalizes posted subscription,
membership, recurring-payment, and rent-payment pushes so first-run users get
seed-backed labels without manual entry. The same phrases can also appear in
marketing, quote, and discount notifications with real-looking amounts, such as
`Membership fee offer USD 49.00` or `Rent payment discount SAR 2,500.00`.
Those are not posted money movement and should not become pending expenses.

## Decision

Add a narrow parser-level non-posted guard before amount selection:

- Ignore subscription, membership, and recurring-payment offer, promo,
  discount, coupon, quote, estimate, trial, and upgrade copy with amounts.
- Ignore rent-payment and Ejar/rent offer, quote, estimate, discount, and deal
  copy with amounts.
- Ignore equivalent Arabic subscription/rent offer, discount, trial, and quote
  copy.
- Keep posted recurring-expense paths unchanged: completed subscription
  payments and rent payments still normalize to the existing shared labels.

## Acceptance

- `Membership fee offer USD 49.00 today` is ignored.
- `Recurring payment promo SAR 29.00` is ignored.
- `Rent payment discount SAR 2,500.00` is ignored.
- `عرض سداد إيجار ٢٥٠٠ ر.س` is ignored.
- `Subscription payment USD 39.00 completed` still parses as an expense with
  merchant `Subscription payment`.
- `Rent payment SAR 2,500.00 completed` still parses as an expense with merchant
  `Rent payment`.

## Non-goals

- Do not change SMS recurring-expense parsing.
- Do not add package identifiers.
- Do not add seed rules or change the public seed count.
- Do not infer arbitrary subscription providers or landlords from offer copy.

## Validation

- `.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`
- `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime `storeSafeDebug` install/launch was attempted. In this resumed shell,
  `adb devices -l` listed no connected devices, `where emulator` could not find
  an Android emulator executable, and `:app:installStoreSafeDebug` failed with
  `No connected devices!`. Evidence is under
  `build/qa/notification-subscription-rent-nonposted-guards-emulator/`.
