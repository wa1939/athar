# G-11 - Notification Party Label Fields

## Problem

Store-safe users rely on bank-app push notifications instead of SMS. Many
notifications use structured party fields that are not covered by the first
labeled-field pass:

- `Biller: Saudi Electricity`
- `Service provider: Water Company`
- `Payer: ACME Payroll`
- `Remitter: Consulting Client`
- `Receiver: Ahmed`
- `Payee: Rent Account`

When those labels are not preserved, the transaction can still parse, but the
row lands with no useful merchant or counterparty. That weakens seed rules,
local category learning, recurring detection, and the user's review experience.

## Decision

Extend only the generic notification parser's structured-label hints after a
notification has already passed the known finance package matcher.

- Expenses can read merchant labels from `Biller`, `Service provider`, and
  Arabic `المفوتر` in addition to the existing merchant/store/payee labels.
- Income can read counterparties from `Payer`, `Remitter`, Arabic `الدافع`,
  and Arabic `المحول` in addition to `Sender`.
- Transfers can read counterparties from `Receiver`, `Payee`, and Arabic
  `المحول له` in addition to `Recipient` and `Beneficiary`.

No new package identifiers are added in this slice. Security, declined,
scheduled-payment, statement, limit, request, marketing, and balance guards
still run before amount extraction.

## Acceptance

- A known finance notification with `Biller:` parses as an expense and preserves
  the biller as the merchant.
- A known finance notification with `Payer:` or `Remitter:` parses as income
  and preserves the party as the counterparty.
- A known finance notification with `Receiver:` or transfer `Payee:` parses as a
  transfer and preserves the party as the counterparty.
- Existing random-package and ignore guards continue to pass.

## Validation

- 2026-06-13: `:ingestion:sms-parser:test` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: Full gate passed with `test`,
  `:app:assemblePersonalFullSmsDebug`, `:app:assembleStoreSafeDebug`,
  `:app:lintPersonalFullSmsDebug`, and `:app:lintStoreSafeDebug`.
- 2026-06-13: `git diff --check` passed.
- 2026-06-13: Runtime notification tap-through could not complete locally.
  `adb devices -l` returned no attached devices, `emulator -accel-check`
  reported that the Android Emulator hypervisor driver is not installed, and a
  bounded headless `AtharPixelQaApi35` launch with `-accel off` did not expose
  an online ADB device after 3 minutes. The launched emulator processes were
  stopped.
