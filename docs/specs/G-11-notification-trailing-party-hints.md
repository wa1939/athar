# G-11 - Notification Trailing Party Hints

## Problem

Store-safe users rely on bank-app push notifications instead of SMS. The generic
notification parser already preserves merchants from labeled fields and common
preposition hints such as `at`, `to`, `for`, `on`, and Arabic `في`. Some compact
bank notifications omit those labels and put the merchant or biller directly
after the amount:

- `POS purchase SAR 42.00 Carrefour`
- `POS purchase SAR 42.00 Carrefour الرصيد ١٠٠ ر.س`
- `تم دفع ١٢٥ ر.س فاتورة الكهرباء`

Those notifications can still parse as expenses, but losing the merchant makes
the row less useful for category seed rules, local exact-merchant learning,
recurring detection, and user review.

## Decision

Add a conservative trailing-party fallback inside `GenericBankNotificationTemplate`.
It runs only after the notification has already passed the known-finance package
gate and after stronger labeled/preposition/merchant-before-amount extraction
has failed.

The fallback accepts only amount-adjacent text that starts with a Latin or Arabic
letter, strips obvious non-party suffixes such as balance, card/account, and
status words, then reuses the existing `cleanParty` sanitizer. It does not add
new package identifiers, does not parse arbitrary notifications, and does not
move any security, marketing, statement, scheduled-payment, reward, balance, or
authorization-hold guard later in the parse order.

## Acceptance

- Compact English POS notification copy preserves the merchant after the amount.
- Arabic amount-then-biller copy preserves the biller as the expense merchant.
- A balance suffix after the merchant is not included in the merchant label.
- A trailing status word such as `approved` is not treated as a merchant.
- Existing notification false-positive guards continue to pass.

## Validation

- 2026-06-14: `:ingestion:sms-parser:test` passed with portable JDK 21,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: `:ingestion:notification-listener:test` passed with portable
  JDK 21, `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: full stack passed with portable JDK 21:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- 2026-06-14: runtime store-safe install was attempted with
  `AtharPixelQaApi35`; the `storeSafeDebug` APK was ready, but the emulator
  exposed only `emulator-5554 offline`, exited before install with Windows
  access-violation code `-1073741819`, and cleanup finished with no attached
  ADB device, no emulator/qemu/netsim process, and no AVD lock files.
