# G-11 Notification Marketing Discount Guards

## Problem

Arabic bank and card apps use `خصم` for real debits, but promotion copy also
uses it for discounts. After Arabic notification debit support was broadened, a
message such as `عرض خصم 20 ر.س عند استخدام بطاقتك` could look like a posted
expense even though it is only an offer.

That is a worse failure than a missed parse: the store-safe notification path
would create plausible fake spending from bank marketing copy.

## Decision

Keep `خصم` as a valid debit action, but add a promotion-only guard inside
`GenericBankNotificationTemplate`:

- ignore Arabic and English offer/coupon/reward/discount wording when there is
  no clear posted-transaction evidence
- keep parsing real debit copy such as `تم خصم ...`, `بعد خصم ...`, and short
  `خصم ... لدى ...` transaction messages
- keep the known-finance-package gate and existing security, statement,
  scheduled-payment, request, and random-package protections

## Acceptance

- Arabic discount offers with money amounts are ignored.
- Arabic percentage-discount offers are ignored.
- Real Arabic debit notifications without offer wording still parse as
  expenses.
- Existing notification parser coverage keeps passing.

## Validation

- 2026-06-14: `:ingestion:sms-parser:test` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: full JVM test/build/lint stack passed:

  ```powershell
  .\gradlew.bat --console=plain test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon --max-workers=1
  ```

- Runtime install/launch could not complete on this PC: `emulator -accel-check`
  reports no Android Emulator hypervisor driver. After clearing stale AVD locks
  and a stuck `qemu-system-x86_64-headless` process, a bounded
  `AtharPixelQaApi35` software boot still stayed `emulator-5554 offline` in ADB
  for 3 minutes.
