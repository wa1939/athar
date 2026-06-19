# G-11 - Notification Refund/Reversal Income

## Problem

The store-safe notification parser already treats plain `refund` as income, but
many card apps describe returned money as a `refunded`, `reversal`, `reversed`,
or `chargeback` notification. Before this slice, those shapes could either fail
or be treated as expenses because they often also include words such as
`purchase`.

SMS templates already classify reversals/refunds as income. Notification
ingestion should match that behavior when a known finance app sends posted
refund/reversal copy.

## Decision

Extend `GenericBankNotificationTemplate` income action words to include:

- `refunded`
- `reversal`
- `reversed`
- `chargeback`

The change stays behind the existing known-finance-package sender gate and
keeps existing OTP, authorization-hold, statement, marketing, and reward guards
unchanged.

## Acceptance

- `You were refunded GBP 12.50 from Amazon` parses as `INCOME`.
- `Purchase reversal AED 18.75 from Uber Trip` parses as `INCOME`, even though
  it contains `purchase`.
- `Chargeback of USD 15.00 from Hotel Desk` parses as `INCOME`.
- The counterparty is preserved from the `from ...` hint.
- Existing cashback/reward, authorization-hold, and random-package guards keep
  passing.

## Non-goals

- Do not treat Arabic `استرداد نقدي` cashback-promo wording as income in this
  slice.
- Do not add new package names or broaden arbitrary notification package
  matching.
- Do not change SMS parser refund behavior.

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
  Emulator hypervisor driver is not installed, and both bounded hidden software
  launches (`-gpu swiftshader_indirect -accel off` and `-gpu off -accel off`)
  exited before exposing an online ADB device with Windows access-violation code
  `-1073741819`. APK install, launch, screenshot, and logcat capture could not
  run. Cleanup finished with no attached ADB device, no emulator/qemu/netsim
  process, and no AVD lock files.
