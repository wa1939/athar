# G-11 - Notification ATM Withdrawal Guards

## Problem

SMS parser templates already normalize cash-withdrawal rows to the shared
`ATM Withdrawal` merchant label so existing seed rules and local learning can
categorize them consistently. Generic store-safe bank notifications currently
preserve whatever ATM/location phrase appears after `from` or `من`, which makes
withdrawals harder to categorize across banks.

Another nearby false positive is withdrawal-limit copy. A bank app can notify
the user that an ATM/cash withdrawal limit changed and include an amount. That
is account state, not a posted transaction, but the generic parser already
treats `withdrawal` as expense wording.

## Decision

Keep the existing known-finance-package gate and add only parser-level behavior:

- Treat English `withdrawal`/`withdrawn` and Arabic `سحب`/`صراف` cash-withdrawal
  notifications as expenses.
- Normalize their merchant to `ATM Withdrawal`, matching SMS parser behavior and
  active seed rules.
- Ignore ATM/cash-withdrawal limit notifications before amount parsing.

## Acceptance

- `ATM withdrawal of USD 100.00 from Main Street ATM` parses as an expense with
  merchant `ATM Withdrawal`.
- `Cash withdrawn AED 250.00 from ATM 123` parses as an expense with merchant
  `ATM Withdrawal`.
- Arabic `تم سحب ٥٠٠ ر.س من صراف آلي` parses as an expense with merchant
  `ATM Withdrawal`.
- `Your ATM withdrawal limit is now USD 1,000.00` is ignored.
- Existing balance, authorization-hold, marketing, and random-package guards keep
  passing.

## Non-goals

- Do not add new package identifiers.
- Do not classify arbitrary non-finance-app notifications.
- Do not change SMS parser withdrawal behavior.

## Validation

- 2026-06-14: focused `GenericBankNotificationTemplateTest` passed with JDK 21,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: `:ingestion:sms-parser:test` and
  `:ingestion:notification-listener:test` passed with JDK 21, `--no-daemon`,
  and `--max-workers=1`.
- 2026-06-14: full stack passed with JDK 21:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon
  --max-workers=1`.
- 2026-06-14: `git diff --check` passed.
- 2026-06-14: runtime `storeSafeDebug` install was attempted on
  `AtharPixelQaApi35`. `emulator -accel-check` still reports that the Android
  Emulator hypervisor driver is not installed, and the bounded hidden software
  launch (`-gpu swiftshader_indirect -accel off`) stayed `emulator-5554 offline`
  in ADB for 3 minutes. APK install, launch, screenshot, and logcat capture
  could not run. The stale `qemu-system-x86_64-headless` process and AVD lock
  files were cleaned afterward; final cleanup had no attached ADB device, no
  emulator/qemu/netsim process, and no AVD lock files.
