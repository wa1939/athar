# G-11 Notification Balance-Only Hint Guards

## Problem

The generic notification parser uses known finance-app packages plus transaction
wording or merchant hints. That keeps terse bank pushes useful, but balance-only
messages can also contain hint words:

- `Available balance SAR 1,234.56 at your checking account`
- `رصيدك ٥٠٠ ر.س لدى حسابك الجاري`

Those notices are not money movement. Before this slice, the `at`/`لدى` hint
could make them look like expenses.

## Decision

Ignore balance notices that do not also contain spend, income, or transfer
action wording:

- treat English balance/available wording as balance-only context
- treat Arabic balance terms with suffixes such as `رصيدك` as balance-only
  context
- keep balance-before-transaction messages parsing when they include a real
  action, such as `You spent ...` or Arabic `بعد خصم ...`
- keep the package allow-list and transaction amount selection unchanged

## Acceptance

- English balance-only notifications with account/location hints are ignored.
- Arabic balance-only notifications with `لدى` account hints are ignored.
- Existing balance-before-spend notification tests still parse the posted debit
  amount, not the balance amount.
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
