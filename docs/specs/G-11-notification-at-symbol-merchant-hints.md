# G-11 - Notification At-Symbol Merchant Hints

## Problem

Some bank and wallet notifications use `@` instead of `at` before the merchant:

- `You spent USD 8.50 @ Starbucks`
- `Card purchase SAR 18.00 @ 7-Eleven`
- `Purchase @Starbucks USD 8.50 approved`

The generic notification parser already understood `at Starbucks`, but these
forms reached the fallback party cleanup with the `@` still attached. That made
confirmed transaction rows less useful for categorization and local rule
learning, because the merchant became `@ Starbucks` or `@Starbucks`.

## Decision

Add an explicit at-symbol merchant hint and keep the existing cleaned-party
safety gate.

The hint avoids matching inside normal email addresses, then passes the captured
value through the same `cleanParty` function used by structured merchant fields,
preposition hints, title hints, and body-line fallbacks. `cleanParty` still
requires at least one Latin or Arabic letter, so digit-starting merchants survive
while numeric-only identifiers remain rejected.

Party cleanup now also trims a leading or trailing `@` for weaker fallback
candidates that still reach cleanup with the symbol attached.

## Acceptance

- `You spent USD 8.50 @ Starbucks` parses as an expense with merchant
  `Starbucks`.
- `Card purchase SAR 18.00 @ 7-Eleven` parses as an expense with merchant
  `7-Eleven`.
- `Card purchase AED 42.00 @ 123456789` parses as an expense with no merchant.
- `Purchase @Starbucks USD 8.50 approved` parses as an expense with merchant
  `Starbucks`.

## Non-goals

- Do not accept numeric-only `@` values as merchants.
- Do not infer merchants from email addresses.
- Do not broaden package allow-listing.
- Do not change seed rules, categories, or account routing.
- Do not change amount selection.

## Validation

- Focused parser regression first failed on the three positive `@` cases,
  returning merchants `@ Starbucks`, `@ 7-Eleven`, and `@Starbucks`.
- Focused parser regression after the fix:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`
- Parser/listener module tests:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest" :ingestion:notification-listener:test`
- Full stack:
  `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime install/launch attempt:
  the built `storeSafeDebug` APK was ready, `emulator -accel-check` reported
  that the Android Emulator hypervisor driver is not installed, and bounded
  hidden `AtharPixelQaApi35` launch exited before an online ADB device appeared
  because x86_64 emulation requires hardware acceleration.
  `:app:installStoreSafeDebug` failed with `No connected devices!`; cleanup
  ended with no attached devices and no emulator/qemu/netsim process. Evidence is
  in `build/qa/notification-at-symbol-merchant-hints-emulator/`.
