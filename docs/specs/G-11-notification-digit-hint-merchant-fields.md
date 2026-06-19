# G-11 - Notification Digit-Start Explicit Merchant Hints

## Problem

The previous digit-starting merchant slice fixed title-first, body-line, and
trailing amount fallback paths, but explicit merchant hints still required the
captured value to start with a letter.

That meant real merchant values could be dropped or returned with the hint
prefix still attached when the value started with a digit:

- `You spent USD 8.50 at 7-Eleven`
- `Card purchase SAR 18.00 approved\nMerchant: 7-Eleven`
- `Payment to 3M USD 12.99 completed`

At the same time, broadening those hint captures can accidentally surface
amount-only or numeric-only values, so the existing numeric identifier guard
must remain.

## Decision

Let explicit merchant, recipient, sender, and preposition hints capture any
non-empty line, then clean each candidate before choosing it.

The parser now chooses the first cleaned usable party rather than the first raw
regex match. This lets the parser skip numeric-only or amount-only captures and
continue to later useful hints.

The shared party cleanup still requires at least one Latin or Arabic letter, so
`7-Eleven` and `3M` survive while values such as `123456789` stay rejected.

## Acceptance

- `You spent USD 8.50 at 7-Eleven` parses as an expense with merchant
  `7-Eleven`.
- `Card purchase SAR 18.00 approved\nMerchant: 7-Eleven` parses as an expense
  with merchant `7-Eleven`.
- `Payment to 3M USD 12.99 completed` parses as an expense with merchant `3M`.
- `Card purchase AED 42.00\nMerchant: 123456789` parses as an expense with no
  merchant.
- Existing `Debit card transaction from Trader Joe's for $23.10` still parses
  with merchant `Trader Joe's` instead of keeping the `from` prefix.

## Non-goals

- Do not accept numeric-only structured fields as merchants.
- Do not broaden package allow-listing.
- Do not change seed rules, categories, or account routing.
- Do not infer merchants from arbitrary non-finance notifications.

## Validation

- Focused parser regression first failed on the three new digit-starting hint
  cases: `Payment to 3M` returned no merchant, and `at 7-Eleven` plus
  `Merchant: 7-Eleven` kept their hint prefixes.
- After broadening hint captures, the focused parser regression caught the
  existing `Debit card transaction from Trader Joe's for $23.10` case returning
  `from Trader Joe's`; choosing the first cleaned usable party fixed it.
- Focused parser regression after the fix:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`
- Parser/listener module tests:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest" :ingestion:notification-listener:test`
- Diff and full stack:
  `git diff --check`
  `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime install/launch attempt:
  the built `storeSafeDebug` APK was ready, `emulator -accel-check` reported
  that the Android Emulator hypervisor driver is not installed, no online ADB
  device appeared, and `:app:installStoreSafeDebug` failed with
  `No connected devices!`. Cleanup ended with no attached devices and no
  emulator/qemu/netsim process. Evidence is in
  `build/qa/notification-digit-hint-merchant-fields-emulator/`.
