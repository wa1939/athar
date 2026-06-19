# G-11 - Notification Bank Fee Guards

## Problem

SMS structured-bank templates already normalize account-only fee debits to the
shared `Bank fees` merchant label. That label has an active seed rule, so fee
rows categorize without the user training one bank-specific merchant at a time.

Generic store-safe bank notifications can contain posted fees, but they often
have no merchant field. Some parse only because the copy says `charged`; others
with just `fee` wording can fail. Nearby false positives are fee schedules,
tariff updates, and pricing-change notices that include amounts but are not
posted money movement.

## Decision

Keep the existing known-finance-package gate and add only parser-level behavior:

- Treat posted fee wording such as `service fee`, `foreign transaction fee`,
  and Arabic `رسوم` as expense action evidence.
- Normalize posted fee notifications to merchant `Bank fees`.
- Ignore fee schedule, tariff, and pricing-change notices before amount parsing.

## Acceptance

- `Monthly service fee SAR 15.00 charged` parses as an expense with merchant
  `Bank fees`.
- `Foreign transaction fee of USD 1.20` parses as an expense with merchant
  `Bank fees`.
- Arabic `تم خصم رسوم ١٥ ر.س من حسابك` parses as an expense with merchant
  `Bank fees`.
- `New fee schedule: ATM fee USD 3.00 from July 1` is ignored.
- Existing random-package, withdrawal, marketing, balance, and authorization
  guards keep passing.

## Non-goals

- Do not add new package identifiers.
- Do not classify arbitrary non-finance-app notifications.
- Do not change SMS parser fee behavior.
- Do not infer fee categories outside the existing shared `Bank fees` label.

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
  launch (`-gpu swiftshader_indirect -accel off`) exited before exposing an
  online ADB device with Windows access-violation code `-1073741819`. APK
  install, launch, screenshot, and logcat capture could not run. Stale AVD lock
  files were cleaned afterward; final cleanup had no attached ADB device, no
  emulator/qemu/netsim process, and no AVD lock files.
