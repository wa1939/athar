# G-11 - Notification Labeled Fields

## Problem

Some bank-app notifications use structured big-text fields instead of natural
sentences:

- `Merchant: Carrefour`
- `Sender: ACME Payroll`
- `Recipient: Ahmed`
- `From: Consulting Client`

The existing generic notification parser handled many sentence shapes, but
labeled fields could leave otherwise valid notification transactions without a
merchant or counterparty. That weakens categorization, local rule learning, and
the review experience for store-safe users.

## Decision

Extend the generic notification parser after the known-finance-package gate:

- Accept `At:`, `To:`, `From:`, `For:`, and `By:` style separators in existing
  hints.
- Extract expense merchants from `Merchant:`, `Store:`, `Payee:`, Arabic
  `التاجر`, and Arabic `المتجر`.
- Extract income counterparties from `Sender:` and Arabic `المرسل`.
- Extract transfer counterparties from `Recipient:`, `Beneficiary:`, Arabic
  `المستفيد`, and Arabic `المستلم`.

No new notification package identifiers are added. The existing declined,
security, marketing, scheduled-payment, statement, request, and limit guards
still run before amount extraction.

## Acceptance

- A known finance notification with `Merchant: Carrefour` parses as an expense
  with merchant `Carrefour`.
- A known finance notification with `Sender: ACME Payroll` parses as income with
  counterparty `ACME Payroll`.
- A known finance notification with `Recipient: Ahmed` parses as a transfer with
  counterparty `Ahmed`.
- Existing notification ignore tests and random-package guards continue to pass.

## Validation

- 2026-06-13: `:ingestion:sms-parser:test` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: Full gate passed with `test`,
  `:app:assemblePersonalFullSmsDebug`, `:app:assembleStoreSafeDebug`,
  `:app:lintPersonalFullSmsDebug`, and `:app:lintStoreSafeDebug`.
- 2026-06-13: Runtime notification tap-through could not complete locally.
  `adb devices -l` initially returned no attached devices.
  `AtharPixelQaApi35Arm` exited because its arm64 system image is not supported
  by the x86_64 host emulator, and a bounded headless `AtharPixelQaApi35`
  x86_64 launch with `-accel off` stayed `offline` in ADB after reconnect, so
  the debug APK could not be installed or launched.
