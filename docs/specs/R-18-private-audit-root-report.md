# R-18 - Private Audit Root Report

## Problem

The gated private SMS audit is the main local evidence source for parser health,
missing merchant cleanup, and repeated uncategorized merchant groups. Gradle runs
module tests with a module working directory, so `Path.of("build/...")` wrote the
rich audit report under `ingestion/sms-parser/build/...`. Meanwhile, the
repository-root `build/private-corpus-audit/latest.json` could remain stale,
making future R-05/R-07/R-18 work easy to misread.

## Decision

- Resolve the repository root by walking upward to `settings.gradle.kts`.
- Write the private audit report to root
  `build/private-corpus-audit/latest.json` for every invocation.
- Keep the report redacted and aggregate-only: counts, hashes, buckets, and
  template counts only.
- Add a unit assertion that pins the output path to the repository build
  directory.

## Acceptance

- Running the gated private audit updates root
  `build/private-corpus-audit/latest.json`.
- The root report contains the rich redacted fields, including
  `parsedExpenses`, `categorizedExpenses`, `uncategorizedExpenses`,
  `missingMerchantGroups`, `uncategorizedMerchantGroups`, and
  `rawBodiesWritten`.
- Raw SMS bodies, merchant samples, sender values, account numbers, and row-level
  amounts are still not written.
- Existing parser-health counts remain stable.

## Non-goals

- Do not change parser behavior or seed rules.
- Do not commit private export data or generated audit output.
- Do not add cloud storage, telemetry, or automatic sharing.

## Validation

- 2026-06-14: focused private audit path validation passed with JDK 21:
  `:ingestion:sms-parser:test --tests
  "com.athar.ingestion.smsparser.PrivateSmsExportAuditTest" --rerun-tasks
  --no-daemon --max-workers=1`.
- 2026-06-14: forced private audit wrote the rich redacted report to root
  `build/private-corpus-audit/latest.json`: 7,887 records, 3,864 parser
  successes, 4,023 ignored, 0 parser failures, 0 failed known-bank messages,
  2,374 parsed expenses, 1,276 categorized parsed expenses, 1,098
  uncategorized parsed expenses, 0 missing-merchant parsed expenses, 20
  hashed uncategorized merchant groups, and `rawBodiesWritten = 0`.
- 2026-06-14: redaction spot-check found no raw-body smoke-test strings in the
  root report (`Local Test Merchant`, `Received from`, `Amount:`).
- 2026-06-14: full JVM test/build/lint stack passed with JDK 21:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon
  --max-workers=1`.
- 2026-06-14: `personalFullSmsDebug` runtime install/launch was attempted with
  the built APK. `AtharPixelQaApi35` exited before exposing an online ADB device
  because x86_64 emulation requires hardware acceleration and the Android
  Emulator hypervisor driver is not installed. `AtharPixelQaApi35Arm` also
  exited because arm64 system images are unsupported on this x86_64 host.
  Evidence was written to `build/qa/private-audit-root-report-emulator/`, and
  cleanup removed the stale AVD lock after confirming no emulator/qemu process
  was running.
