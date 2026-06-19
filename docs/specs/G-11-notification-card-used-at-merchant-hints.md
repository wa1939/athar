# G-11 - Notification Card-Used At-Merchant Hints

## Problem

Some bank apps describe posted card purchases with the card-used action first,
then put the merchant after `at` or `on` before the amount:

- `Your card was used at Lulu for SAR 55.00`
- `Debit card used at Carrefour AED 42.00`

The generic notification parser already treats these as card-spend
notifications, but without a colon, dash, `to`, or trailing merchant shape they
can arrive without a merchant. That leaves a pending row that still needs manual
cleanup and cannot train a local category rule from the merchant label.

## Decision

- Keep the known finance-app package gate unchanged.
- Keep false-positive guards unchanged.
- Reuse the existing card-used expense evidence.
- Add only a merchant-before-amount extractor for clear `card used at/on
  merchant amount` and `card was used at/on merchant for amount` wording.
- Support debit-card and credit-card prefixes without treating arbitrary
  notification packages as transaction sources.

## Acceptance

- `Your card was used at Lulu for SAR 55.00` parses as an expense with merchant
  `Lulu`.
- `Debit card used at Carrefour AED 42.00` parses as an expense with merchant
  `Carrefour`.
- Existing card-used colon/dash, status-prefix, recipient-label, transfer, and
  false-positive guard coverage keeps passing.

## Non-goals

- Do not infer a merchant from unclear `card used` copy without `at`/`on`, a
  colon/dash label, or another existing merchant hint.
- Do not widen package matching.
- Do not change categorization rules or seed data.

## Validation

- `:ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`
- `:ingestion:sms-parser:test :ingestion:notification-listener:test`
- `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Bounded runtime QA was attempted against the built `storeSafeDebug` APK. The
  local `AtharPixelQaApi35` software launch still cannot reach an installable
  online ADB state because `emulator -accel-check` reports that the Android
  Emulator hypervisor driver is not installed; the launch emitted startup logs
  and exited before an online ADB device appeared, so install, screenshot, UI
  dump, and logcat capture could not run. Evidence is under
  `build/qa/notification-card-used-at-merchant-hints-emulator/`, and cleanup
  verification finished with no adb/emulator/qemu/netsim process and no AVD
  lock files.
