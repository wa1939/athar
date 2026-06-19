# G-11 - Notification POS Transaction Phrases

## Problem

Some store-safe bank notifications use generic transaction wording instead of
the already-covered spend verbs. Examples include:

- `Transaction of USD 23.10 at Trader Joe's was approved`
- `POS transaction at Carrefour AED 42.00`

The amount and merchant are present, and the notification still comes through
the known finance-app package gate, but merchant cleanup could retain status
tail text such as `was approved`. That produces noisy merchant labels and weaker
local category learning.

## Decision

- Keep the existing known-package notification gate unchanged.
- Treat these POS/card-transaction shapes as expense notifications through the
  existing generic notification parser.
- Preserve merchant extraction from the existing `at` and amount-adjacent paths.
- Strip terminal status phrases such as `was approved`, `has been posted`, and
  `is confirmed` from merchant candidates.

## Acceptance

- `Transaction of USD 23.10 at Trader Joe's was approved` parses as an expense
  for `Trader Joe's`.
- `POS transaction at Carrefour AED 42.00` parses as an expense for
  `Carrefour`.
- Existing notification false-positive guards and package gating remain in
  force.

## Validation

- `:ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`
- `:ingestion:sms-parser:test :ingestion:notification-listener:test`
- `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Bounded runtime QA attempted against the built `storeSafeDebug` APK on
  `AtharPixelQaApi35`. The local emulator still cannot reach an online ADB
  state because `emulator -accel-check` reports that the Android Emulator
  hypervisor driver is not installed; the software launch stayed
  `emulator-5554 offline`, so install, screenshot, UI dump, and logcat capture
  could not run. Evidence is under
  `build/qa/notification-pos-transaction-phrases-emulator/`, and cleanup
  finished with no adb/emulator/qemu/netsim process and no AVD lock files.

## Non-goals

- Do not accept arbitrary non-bank app notifications.
- Do not change amount selection, balance extraction, or package matching.
- Do not add new shared seed rules.
