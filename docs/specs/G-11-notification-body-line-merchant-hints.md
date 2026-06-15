# G-11 - Notification Body-Line Merchant Hints

## Problem

Some Android finance notifications put the posted action and amount in the title
or first line, then put the merchant on the next visible body line. The generic
store-safe notification parser already treats those messages as posted expenses,
but the merchant can stay blank when the line after the amount has no `at`,
`Merchant:`, or other explicit label.

Merchant-less pending rows make category learning, repeated cleanup, and
recurring detection less useful.

## Decision

After explicit labels, title-first merchant hints, and merchant-before-amount
patterns fail, scan the following notification lines after the selected amount
for a merchant candidate.

The fallback is conservative:

- it only runs when there is a line break after the amount;
- blank lines and generic action/status lines such as `approved` or
  `card transaction settled` are skipped;
- lines containing another amount are rejected;
- exact known bank/app titles such as `Wise` are rejected;
- same-line trailing merchant extraction remains unchanged.

## Acceptance

- `Card purchase SAR 99.00\nNoon` parses as an expense with merchant `Noon`.
- `AED 42.00 card transaction settled\nCarrefour` parses as an expense with
  merchant `Carrefour`.
- `Card purchase SAR 42.00\nWise` parses as an expense with no merchant instead
  of using the bank app title as a merchant.

## Non-goals

- Do not use arbitrary app titles or generic status text as merchant labels.
- Do not change package allow-listing, seed rules, or transaction categories.
- Do not broaden same-line trailing merchant extraction.

## Validation

- Focused parser regression first failed on the two new positive cases, proving
  the gap.
- Focused parser regression after the fix:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`
- Parser/listener module tests:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest" :ingestion:notification-listener:test`
- Diff and full stack:
  `git diff --check`
  `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime install/launch attempt:
  the built `storeSafeDebug` APK was ready, `emulator -accel-check` reported
  that the Android Emulator hypervisor driver is not installed, `adb devices`
  stayed empty through the wait window, `:app:installStoreSafeDebug` failed with
  `No connected devices!`, and cleanup ended with no attached devices and no
  emulator/qemu/netsim process. Evidence is in
  `build/qa/notification-body-line-merchant-hints-emulator/`.
