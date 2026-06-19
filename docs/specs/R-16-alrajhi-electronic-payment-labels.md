# R-16 - Al Rajhi Electronic Payment Labels

## Problem

After R-15, the private received-message audit had no failed known-bank messages,
but 5 parsed expense rows still had no merchant. All 5 came from Al Rajhi generic
fallback shapes where the useful label was a first-line public-service header
instead of a labeled `Biller`, `Service`, `At`, or `Merchant` field.

Missing merchants weaken categorization, local rule learning, recurring detection,
and user review because the pending row cannot explain what the amount represents.

## Decision

Keep the fallback conservative and sender-scoped:

- Preserve the first-line `اليكترون` electronic-payment header as the merchant.
- Preserve `مدفوعات وزارة الداخلية...` public-service headers as the merchant,
  including traffic-violation suffixes.
- Do not promote arbitrary first lines to merchants.
- Do not add private names or account identifiers to the shared seed/catalog.

## Acceptance

- Sanitized corpus tests cover both Al Rajhi header-only shapes.
- Existing SMS parser tests stay green.
- Unknown senders remain ignored.
- Temporary private-audit helpers and raw export contents are not committed.

## Private Audit Result

Temporary local received-message audit over `All Conversations 2026-05-27 175517.txt`:

- Records inspected: 7,887.
- Failed known-bank messages: 0.
- Parsed expense rows with missing merchants: reduced from 5 to 0.

## Validation

- 2026-06-14: `:ingestion:sms-parser:test` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: full JVM test/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon
  --max-workers=1`.
- 2026-06-14: runtime install/launch was attempted on `AtharPixelQaApi35`.
  `emulator -accel-check` reported the Android Emulator hypervisor driver is
  not installed. A bounded `-accel off` boot emitted startup logs but never
  exposed an online ADB device, so APK install, launch, and screenshot capture
  could not run. Cleanup ended with no attached ADB device, no emulator/qemu
  process, and no AVD lock files.
