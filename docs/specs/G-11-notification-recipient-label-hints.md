# G-11 - Notification Recipient Label Hints

## Problem

Some posted bank and wallet notifications expose the merchant-like party through
payment counterparty labels rather than merchant-specific labels:

- `Payment completed / Amount: SAR 75.00 / Recipient: Amazon`
- `Card purchase / Amount: GBP 9.99 / Beneficiary: Spotify`
- `تم دفع ٤٥ ر.س / المستفيد: هنقرستيشن`

The generic notification parser already understands `Recipient`, `Receiver`,
and `Beneficiary` labels for transfer counterparties, and it understands
merchant labels such as `Merchant`, `Location`, `Biller`, and `Card acceptor`
for expenses. Posted payment notifications that use recipient-style labels can
therefore become valid expense rows with a blank merchant, creating manual
cleanup and weakening category-rule learning.

## Decision

- Keep the known finance-app package gate unchanged.
- Do not treat recipient-style labels as a transaction action by themselves.
- When a notification has already been classified as an expense, allow the
  existing recipient/receiver/beneficiary label parser to provide the merchant
  candidate before falling back to generic `to`, `at`, `by`, `for`, `from`, and
  amount-adjacent extraction.
- Preserve transfer ordering so `Transfer sent / Recipient: Ahmed` remains a
  transfer with a counterparty, not an expense merchant.

## Acceptance

- `Payment completed / Amount: SAR 75.00 / Recipient: Amazon` parses as an
  expense with merchant `Amazon`.
- `Card purchase / Amount: GBP 9.99 / Beneficiary: Spotify` parses as an
  expense with merchant `Spotify`.
- Arabic `المستفيد:` labels on posted payment notifications parse as expense
  merchants.
- Existing labeled recipient transfer coverage keeps passing.

## Non-goals

- Do not parse arbitrary notification packages.
- Do not make recipient/beneficiary labels sufficient to create an expense when
  the body has no posted payment, purchase, bill, card, or other expense action.
- Do not change income or transfer counterparty extraction.

## Validation

- `:ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`
- `:ingestion:sms-parser:test :ingestion:notification-listener:test`
- `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Bounded runtime QA was attempted against the built `storeSafeDebug` APK on
  `AtharPixelQaApi35`. The local emulator still cannot reach an online ADB
  state because `emulator -accel-check` reports that the Android Emulator
  hypervisor driver is not installed; the software launch stayed
  `emulator-5554 offline`, so install, screenshot, UI dump, and logcat capture
  could not run. Evidence is under
  `build/qa/notification-recipient-label-hints-emulator/`, and post-inspection
  cleanup verification finished with no adb/emulator/qemu/netsim process and no
  AVD lock files.
