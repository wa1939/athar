# G-11 - Notification Party Stopword Suffixes

## Problem

Notification party cleanup stripped connector and payment-method words anywhere
inside a merchant candidate:

- `for`
- `using`
- `with`
- `via`
- `card`
- `ending`

That removed useful suffixes such as `with Apple Pay`, but it also destroyed
legitimate merchant names that begin with those words. Real examples include
`Card Factory` and venue names such as `Via Roma Cafe`.

The failure mode was especially bad for explicit `at` hints: the parser first
captured `Card Factory`, cleaned it to nothing, then fell through to a weaker
fallback that returned `at`.

## Decision

Keep suffix cleanup, but make it context-aware:

- remove connector words only when they appear after an accepted party name;
- remove card/ending payment-method tails only when they look like card suffix
  context, such as `card ending 1234`;
- do not strip those words when they start a legitimate party name.

## Acceptance

- `You spent GBP 12.00 at Card Factory` parses with merchant `Card Factory`.
- `You spent EUR 8.90 at Via Roma Cafe` parses with merchant `Via Roma Cafe`.
- `You paid $9.99 to Apple Services with Apple Pay` still parses with merchant
  `Apple Services`.
- `You paid USD 9.99 to Apple Services via Apple Pay` still parses with
  merchant `Apple Services`.
- `You paid USD 9.99 to Apple Services using card ending 1234` still parses
  with merchant `Apple Services`.
- Existing `Debit card transaction from Trader Joe's for $23.10` still parses
  with merchant `Trader Joe's`.

## Non-goals

- Do not infer merchants from arbitrary payment method names.
- Do not broaden package allow-listing.
- Do not change seed rules, categories, or account routing.
- Do not change amount selection.

## Validation

- Focused parser regression first failed on `Card Factory` and `Via Roma Cafe`,
  returning merchant `at`.
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
  `AtharPixelQaApi35` exposed no ADB device through the bounded wait, and
  `:app:installStoreSafeDebug` failed with `No connected devices!`. Cleanup
  ended with no attached devices and no emulator/qemu/netsim process. Evidence is in
  `build/qa/notification-party-stopword-suffixes-emulator/`.
