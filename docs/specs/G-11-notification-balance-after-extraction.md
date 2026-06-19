# G-11 - Notification Balance-After Extraction

## Problem

Some bank-app transaction notifications include both the posted transaction
amount and the resulting account balance. Earlier G-11 work made the generic
notification parser choose the transaction amount instead of a nearby balance
amount, but it still discards the balance metadata by returning
`balanceAfter = null`.

For bank-specific SMS templates, `balanceAfter` is already part of the parser
contract. Store-safe notification parsing should preserve the same metadata when
the text is clear enough, without changing what becomes a pending transaction.

## Decision

Extend only `GenericBankNotificationTemplate` after the known-finance package
gate and after the transaction amount has already been selected:

- keep transaction amount selection unchanged
- find a separate amount with nearby balance wording such as `balance`,
  `available`, `remaining balance`, `الرصيد`, or `رصيدك`
- accept balance amounts that appear after the selected transaction amount
- accept a balance amount before the transaction amount only when the text
  between them explicitly indicates after-transaction wording such as
  `after debit`, `after purchase`, or `بعد خصم`
- use the balance amount currency marker when present, otherwise fall back to the
  selected transaction currency
- keep balance-only notifications ignored and keep random-package guards intact

This is deliberately parser-only. Persisting or reconciling against
`balanceAfter` remains future account-ledger work.

## Acceptance

- A notification like `You spent SAR 42.00 at Starbucks. Balance SAR 958.00`
  parses the spend and sets `balanceAfter` to `958.00 SAR`.
- Arabic copy like `رصيدك ٩٦٤٫٥٠ ر.س بعد خصم ٣٥٫٥٠ ر.س لدى كارفور` preserves
  `balanceAfter` while still using `35.50 SAR` as the transaction amount.
- Ambiguous balance-before-spend copy does not set `balanceAfter` unless there
  is after-transaction wording.
- Existing notification parser guards continue to pass.

## Validation

- 2026-06-14: `:ingestion:sms-parser:test` passed with portable JDK 21,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: `:ingestion:notification-listener:test` passed with portable
  JDK 21, `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: full JVM test/build/lint stack passed:

  ```powershell
  .\gradlew.bat --console=plain test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon --max-workers=1
  ```

- Runtime store-safe install/launch could not complete on this PC. The
  `storeSafeDebug` APK was ready, but `emulator -accel-check` still reported no
  Android Emulator hypervisor driver, and a bounded hidden `AtharPixelQaApi35`
  software boot exited after 2 seconds with Windows access-violation code
  `-1073741819` before exposing an online ADB device. Stale AVD locks were
  cleaned afterward.
