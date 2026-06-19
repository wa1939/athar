# G-11 - Notification Recurring Renewals

## Problem

Store-safe notification ingestion already recognizes generic subscription,
membership, insurance, and rent payments, and it ignores non-posted renewal or
due-date reminders. A common bank-push shape is slightly different:
`Netflix subscription renewed for USD 9.99` or `Subscription renewal for Spotify
AED 19.99 completed`. Those are posted charges, not future reminders. If Athar
does not parse them, users must manually add or categorize repeat subscription
rows, and recurring suggestion detection loses merchant-specific history.

## Decision

Treat subscription or membership renewal wording as a posted recurring expense
only when the notification also has posted-settlement language such as
`renewed`, `completed`, `posted`, `paid`, `charged`, or Arabic `تم تجديد`.

When the merchant is present before the renewal phrase or after `for`, preserve
that merchant instead of collapsing every row to the generic
`Subscription payment` label. If no merchant can be found safely, keep the
existing generic shared label.

## Acceptance

- `Your Netflix subscription renewed for USD 9.99` parses as an expense with
  merchant `Netflix`.
- `Subscription renewal for Spotify AED 19.99 completed` parses as an expense
  with merchant `Spotify`.
- Arabic posted renewal copy such as `تم تجديد اشتراك نتفلكس بمبلغ ٣٩ ر.س`
  parses as an expense and keeps the named merchant.
- Future reminders such as `Your Netflix subscription renews tomorrow for USD
  9.99` remain ignored.

## Non-goals

- Do not parse subscription offers, discounts, trials, upgrades, or quotes.
- Do not add cloud or AI categorization.
- Do not change recurring-rule detection thresholds.

## Validation

- `.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`
- `.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test :ingestion:notification-listener:test`
- `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- `git diff --check`
- Runtime install was attempted on 2026-06-15 with the built `storeSafeDebug`
  APK. `emulator -accel-check` reported that the Android Emulator hypervisor
  driver is not installed, the bounded `AtharPixelQaApi35` launch exposed only
  `emulator-5554 offline`, and `:app:installStoreSafeDebug` failed with
  `No online devices found`. Evidence is in
  `build/qa/notification-recurring-merchant-renewal-emulator/`.
