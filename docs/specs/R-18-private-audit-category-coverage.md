# R-18 - Private Audit Category Coverage

## Problem

The private SMS audit already proves parser safety without exposing private data:
0 parser failures, 0 failed known-bank messages, and 0 parsed expense rows with
missing merchants. The remaining cleanup question is categorization coverage.
The previous report listed top hashed uncategorized merchant groups, but it did
not quantify how much of the backlog those groups represented.

That made future triage slower. A repeated group with 50 rows matters very
differently when it is half the backlog versus only five percent of it.

## Decision

Extend the gated private audit report with redacted coverage math:

- `categoryCoverage` summarizes categorized expense coverage, uncategorized
  backlog share, top-group count, top-group sample coverage, remaining
  uncategorized rows outside the top groups, and largest-group coverage.
- Each `uncategorizedMerchantGroups` entry now includes its share of the
  uncategorized backlog and cumulative share, reported as permille integers.

The report continues to omit raw SMS bodies, senders, merchant labels, amounts,
balances, card/account numbers, transaction rows, and notes.

## Acceptance

- The gated audit JSON contains `categoryCoverage`.
- Top uncategorized merchant groups include per-group and cumulative backlog
  share without raw labels.
- Synthetic redaction tests prove the raw merchant label is not serialized.
- Private SMS content remains untracked and uncommitted.

## Non-goals

- Do not promote private/local merchant labels into shared seed rules.
- Do not export raw merchant labels or transaction rows.
- Do not change parser behavior or production app behavior in this slice.

## Validation

- 2026-06-14: Focused `PrivateSmsExportAuditTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: The forced gated private audit wrote repository-root
  `build/private-corpus-audit/latest.json` with 7,887 records, 3,864 parser
  successes, 4,023 ignored messages, 0 parser failures, 0 failed known-bank
  messages, 2,374 parsed expenses, 1,287 categorized expenses, 1,087
  uncategorized expenses, 0 missing-merchant parsed expenses, and
  `rawBodiesWritten = 0`. The new redacted coverage summary reports 542/1000
  categorized-expense coverage, 457/1000 uncategorized backlog, top-20 hashed
  groups covering 184 rows (169/1000 of uncategorized expenses), 903 rows
  outside the top groups, and the largest hashed group covering 55 rows
  (50/1000 of uncategorized expenses).
- 2026-06-14: `:ingestion:sms-parser:test`, `git diff --check`, and the full
  JVM/build/lint stack passed with JDK 17, `--no-daemon`, and
  `--max-workers=1`: `test`, `:app:assemblePersonalFullSmsDebug`,
  `:app:assembleStoreSafeDebug`, `:app:lintPersonalFullSmsDebug`, and
  `:app:lintStoreSafeDebug`.
- 2026-06-14: Runtime install/launch was attempted with the built
  `personalFullSmsDebug` APK. `emulator -accel-check` reported the Android
  Emulator hypervisor driver is not installed, and a bounded hidden
  `AtharPixelQaApi35` software launch exposed only `emulator-5554 offline`, so
  APK install, screenshot capture, UI dump, and logcat capture could not run.
  Evidence was written to `build/qa/private-audit-coverage-summary-emulator/`,
  and final cleanup left no emulator/qemu/adb/netsim process and no AVD lock
  entries.
