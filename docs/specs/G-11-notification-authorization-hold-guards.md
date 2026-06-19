# G-11 Notification Authorization-Hold Guards

## Problem

Finance apps can send card-authorization notices before a transaction is
posted. These often contain an amount plus a merchant hint, for example:

- `Temporary authorization hold of USD 50.00 at Grand Hotel`
- `Pre-authorization of SAR 1.00 at Apple Services`
- `Pending authorization AED 200.00 with Booking.com`
- `تم حجز مبلغ ٥٠٠ ر.س مؤقتاً لدى فندق الرياض`

The generic notification parser accepts known finance packages and can classify
amount-plus-merchant-hint copy as an expense even without a posted spend verb.
That is useful for terse real transaction copy, but it can turn temporary holds
into fake pending expenses.

## Decision

Add a narrow authorization/hold guard to `GenericBankNotificationTemplate`
before amount selection:

- ignore English pre-authorization, pending authorization, authorization hold,
  temporary/card hold, amount-held, and payment/transaction authorization wording
- ignore Arabic temporary hold, held amount, and authorization wording
- keep the known-finance-package gate unchanged
- preserve real posted card-transaction notifications that do not contain hold
  or authorization-state wording

## Acceptance

- Temporary authorization holds with merchant hints are ignored.
- Pre-authorization and pending-authorization notifications with amounts are
  ignored.
- Arabic temporary-hold notifications with merchant hints are ignored.
- Real posted card-transaction notifications still parse as expenses.
- Existing notification parser coverage keeps passing.

## Validation

- 2026-06-14: `:ingestion:sms-parser:test` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: full JVM test/build/lint stack passed:

  ```powershell
  .\gradlew.bat --console=plain test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon --max-workers=1
  ```

- Runtime install/launch could not complete on this PC. `adb devices -l`
  showed no online device, `emulator -accel-check` reported that the Android
  Emulator hypervisor driver is not installed, and a bounded
  `AtharPixelQaApi35` software boot logged `Failed to load opengl32sw`, opened
  an emulator crash dialog, and never exposed an online ADB device. The stale
  AVD lock directory/file were cleaned afterward.
