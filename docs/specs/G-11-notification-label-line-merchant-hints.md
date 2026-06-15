# G-11 - Notification Label-Line Merchant Hints

## Problem

Some Android finance notifications split a merchant field across body lines:

- the posted action and amount appear on the title or first body line;
- the next body line is only a label such as `Merchant` or `Location`;
- the actual merchant value appears on the following line.

The body-line merchant fallback could previously treat the label itself as the
merchant. That creates low-quality pending rows such as merchant `Merchant`,
which cannot support category learning, repeated cleanup, or recurring
suggestions.

## Decision

Keep the body-line merchant fallback from the previous slice, but reject
label-only body lines before accepting a candidate merchant.

The fallback remains conservative:

- lines containing another amount are still rejected;
- generic status/action lines and known bank-app titles are still rejected;
- label-only words such as `Merchant`, `Merchant Name`, `Location`, `Store`,
  `Payee`, `Biller`, `Recipient`, `Receiver`, and `Beneficiary` are skipped;
- a later body line can still become the merchant when it is a real value;
- existing explicit structured labels such as `Merchant: Noon` remain on the
  structured-field path.

## Acceptance

- `Card purchase SAR 99.00\nMerchant\nNoon` parses as an expense with merchant
  `Noon`.
- `AED 42.00 card transaction settled\nLocation\nCarrefour` parses as an
  expense with merchant `Carrefour`.
- `Card purchase SAR 42.00\nMerchant` parses as an expense with no merchant
  instead of using `Merchant` as the merchant value.

## Non-goals

- Do not broaden package allow-listing.
- Do not change seed rules, categories, or account routing.
- Do not parse arbitrary bank/app titles as merchants.
- Do not change explicit structured `Merchant: value` extraction.

## Validation

- Focused parser regression first failed on the three new cases, proving the
  gap:
  expected `Noon` but got `Merchant`, expected `Carrefour` but got `Location`,
  and expected no merchant but got `Merchant`.
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
  `No online devices found`. Cleanup stopped emulator/qemu children and ended
  with no attached devices. Evidence is in
  `build/qa/notification-label-line-merchant-hints-emulator/`.
