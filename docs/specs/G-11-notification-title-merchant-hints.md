# G-11 - Notification Title Merchant Hints

## Problem

Some Android finance notifications split the merchant into the notification title
or first visible line, while the body carries only the posted action/status and
amount. The generic store-safe notification parser already treats those bodies
as posted money movement, but without the title fallback they become
merchant-less pending rows.

Merchant-less rows increase manual cleanup and weaken local categorization,
exact-rule learning, and recurring-suggestion history.

## Decision

When an amount-bearing notification has line breaks before the matched amount,
allow the first nonblank line before the amount to become the merchant only
after stronger explicit hints fail.

The fallback is conservative:

- the title must start with a letter;
- the title must not contain an amount;
- generic action/status titles such as `Payment`, `Transaction`, or `Card`
  are rejected;
- exact known bank/app titles such as `Wise` are rejected;
- explicit `at`, `to`, `from`, and structured label hints continue to win.

## Acceptance

- `Noon\nCard purchase SAR 99.00 approved` parses as an expense with merchant
  `Noon`.
- `Carrefour\nAED 42.00 card transaction settled` parses as an expense with
  merchant `Carrefour`.
- `Toast Box\nPurchase processed\nSGD 6.40` parses as an expense with merchant
  `Toast Box`.
- `Wise\nCard purchase SAR 42.00 approved` parses as an expense with no merchant
  instead of using the bank app title as a merchant.

## Non-goals

- Do not use arbitrary package or app names as merchant hints.
- Do not let generic action/status titles become merchant labels.
- Do not change package allow-listing, seed rules, or transaction categories.

## Validation

- Focused parser regression:
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
  `build/qa/notification-title-merchant-hints-emulator/`.
