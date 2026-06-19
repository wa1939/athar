# R-15 - Private Corpus Admin-State Ignores

## Problem

After the parser registry was shared, a local aggregate audit over the private
family-device export showed that the remaining poor rows were no longer failed
known-bank parses. They were mostly bank administration or account-state notices
that contained numbers and currency-like values, so the universal fallback or
structured fallback treated them as low-confidence expenses without merchants.

Those rows are worse than failed audit entries: they can enter the pending tray
as plausible expenses and require manual cleanup.

## Decision

Keep the fix narrow and sender-gated:

- Ignore compact Arabic beneficiary activation/status notices.
- Ignore mobile-number-to-card linking, digital-card issuance, username, and
  daily-transfer-limit messages.
- Ignore reward-point balance notices.
- Ignore Arabic card-terms updates for credit and prepaid cards.
- Treat structured Arabic refund wording (`استرداد مبلغ`) as income, not expense.

The raw SMS export remains private. Only sanitized structural tests and aggregate
counts are committed.

## Acceptance

- Sanitized corpus tests pin every new ignored notice shape.
- Structured wallet refunds parse as `INCOME`.
- Existing parser tests stay green.
- Unknown senders remain ignored and the universal fallback remains restricted to
  known bank/wallet senders.
- Temporary private-audit helpers and raw export contents are not committed.

## Private Audit Result

Temporary local received-message audit over `All Conversations 2026-05-27 175517.txt`:

- Records inspected: 7,887.
- Failed known-bank messages: 0 before and after this slice.
- Parsed expense rows with missing merchants: reduced from 18 to 5.

The remaining 5 rows are a small Al Rajhi electronic-payment/public-service
label cluster and should be handled in a separate parser-labeling slice rather
than broadened inside admin ignores.

## Validation

- 2026-06-14: `:ingestion:sms-parser:test` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: full JVM test/build/lint stack passed:
  ```powershell
  .\gradlew.bat --console=plain test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon --max-workers=1
  ```
- 2026-06-14: StoreSafe runtime install/launch was attempted on
  `AtharPixelQaApi35`. `emulator -accel-check` still reported that the Android
  Emulator hypervisor driver is not installed. A bounded `-accel off` boot
  emitted startup logs but never exposed an online ADB device, so install could
  not run. Cleanup completed with no attached ADB device, no emulator/qemu
  process, and no AVD lock files.
