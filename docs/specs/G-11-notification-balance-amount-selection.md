# G-11 Notification Balance-Aware Amount Selection

## Problem

Bank-app push notifications often include both the posted transaction amount and
the resulting account balance. If the balance appears first, a parser that takes
the first currency amount can create a pending transaction for the balance
instead of the spend.

Example shapes:

- `Available balance: SAR 1,234.56. You spent SAR 42.00 at Starbucks`
- `رصيدك ١٬٠٠٠٫٠٠ ر.س بعد خصم ٣٥٫٥٠ ر.س لدى كارفور`

That failure is worse than a missed parse because it creates a plausible but
wrong amount.

## Decision

Improve only `GenericBankNotificationTemplate` amount selection:

- collect all amount candidates as before
- prefer currency-marked candidates
- prefer candidates after the detected transaction action
- skip candidates whose nearby context is clearly balance/available/remaining
  balance text when another candidate is available
- keep the known-finance-package gate and existing false-positive guards

Arabic debit words such as `خصم`, `شراء`, `دفع`, and `سحب` no longer depend on
Latin-style word boundaries when detecting the action location.

## Acceptance

- English balance-first spend notifications parse the transaction amount, not
  the balance.
- Arabic balance-first debit notifications parse the transaction amount, not the
  balance.
- Existing notification parser coverage and random-package guards keep passing.

## Validation

- 2026-06-14: `:ingestion:sms-parser:test` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: full JVM test/build/lint stack passed:

  ```powershell
  .\gradlew.bat --console=plain test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon --max-workers=1
  ```
- Runtime install/launch could not complete on this PC: `emulator -accel-check`
  reports no Android Emulator hypervisor driver, and a bounded
  `AtharPixelQaApi35` software boot left only an `emulator-5554 offline` ADB
  transport with no emulator process.
