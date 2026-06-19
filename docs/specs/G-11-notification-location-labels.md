# G-11 - Notification Location Labels

## Problem

Some store-safe bank notifications expose the merchant as a structured location
or terminal label instead of the already-covered `Merchant:` or `Store:` fields.
Common shapes include:

- `Card purchase\nAmount: AED 42.00\nLocation: Carrefour`
- `Card transaction\nAmount SAR 19.90\nMerchant Name: Toast Box`
- `Debit card purchase\nAED 42.00\nCard acceptor: IKEA`

These notifications already pass the known finance-app package gate and have a
clear posted transaction amount. Losing the structured merchant makes the row
harder to categorize and weakens exact local learning.

## Decision

- Keep the existing notification package allow-list unchanged.
- Keep amount selection, balance extraction, and transaction type selection
  unchanged.
- Extend the existing structured merchant-label extraction to accept
  transaction-local labels for `Merchant Name`, `Location`, `Outlet`, and
  `Card acceptor`.
- Require the same explicit label separator (`:`, `-`, or `·`) and
  letter-starting value used by the current merchant-label path.

## Acceptance

- `Location: Carrefour` is preserved as merchant `Carrefour` on a posted card
  purchase notification.
- `Merchant Name: Toast Box` is preserved as merchant `Toast Box`.
- `Card acceptor: IKEA` is preserved as merchant `IKEA`.
- Existing arbitrary app/package ignores, amount selection, balance extraction,
  and false-positive guards remain in force.

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
  `build/qa/notification-location-labels-emulator/`, and cleanup finished with
  no adb/emulator/qemu/netsim process and no AVD lock files.

## Non-goals

- Do not parse arbitrary notification packages.
- Do not infer a merchant from unlabeled location prose.
- Do not add new category seed rules.
