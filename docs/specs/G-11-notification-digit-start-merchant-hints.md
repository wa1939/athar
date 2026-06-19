# G-11 - Notification Digit-Start Merchant Hints

## Problem

Some real merchants start with digits, including common names such as
`7-Eleven` and `6th Street`. The generic store-safe notification parser used
letter-starting checks to avoid accepting numeric identifiers as merchants.
That protected reference numbers, but it also dropped specific merchant names
when they appeared as:

- the title before an amount line;
- the body line after a posted amount line;
- the trailing party after a compact amount-first notification.

The cleanup path could also strip the leading number from `7-Eleven`, leaving
the weaker merchant value `Eleven`.

## Decision

Treat a merchant candidate as valid when it contains at least one Latin or
Arabic letter, not only when it starts with a letter.

To keep the numeric-identifier guard, amount cleanup now removes only
standalone amount-shaped tokens:

- tokens with a currency marker;
- decimal or grouped numeric tokens;
- long numeric tokens that look like identifiers or amounts.

Digits attached to letters or hyphens stay part of the merchant. Body-line
fallbacks also explicitly skip metadata-only lines such as date/time,
reference, RRN, STAN, transaction ID, approval code, receipt number, terminal
ID, and POS ID when they include digits.

## Acceptance

- `7-Eleven\nCard purchase USD 8.50 approved` parses as an expense with merchant
  `7-Eleven`.
- `Card purchase AED 42.00\n6th Street` parses as an expense with merchant
  `6th Street`.
- `POS purchase SAR 18.00 7-Eleven` parses as an expense with merchant
  `7-Eleven`.
- `Card purchase USD 12.99\n123456789` parses as an expense with no merchant.
- `Card purchase AED 42.00\nReference 123456789\nCarrefour` skips the metadata
  line and preserves `Carrefour`.

## Non-goals

- Do not accept numeric-only lines as merchants.
- Do not broaden package allow-listing.
- Do not change seed rules, categories, or account routing.
- Do not infer merchants from arbitrary non-finance notifications.

## Validation

- Focused parser regression first failed on the digit-starting merchant cases:
  title and body-line merchants were `null`, and compact trailing
  `7-Eleven` was cleaned to `Eleven`.
- Focused parser regression after the fix:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`
- Parser/listener module tests:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest" :ingestion:notification-listener:test`
- Diff and full stack:
  `git diff --check`
  `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime install/launch attempt:
  the built `storeSafeDebug` APK was ready, `emulator -accel-check` reported
  that the Android Emulator hypervisor driver is not installed,
  `AtharPixelQaApi35` stayed `emulator-5554 offline`, and
  `:app:installStoreSafeDebug` skipped the offline device and failed with
  `No online devices found`. Cleanup stopped the emulator/qemu children and
  ended with no attached devices. Evidence is in
  `build/qa/notification-digit-start-merchant-hints-emulator/`.
