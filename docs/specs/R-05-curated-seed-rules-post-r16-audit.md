# R-05 - Curated Seed Rules Post-R16 Audit

## Problem

R-16 reduced the private received-message audit to zero failed known-bank
messages and zero parsed expenses with missing merchants. The remaining cleanup
work moved back to categorization coverage: parsed expenses can still land in
Pending when the merchant is real but no seed rule matches it.

Previous private-corpus follow-ups used temporary scratch tests that were
deleted before commit. That protected raw SMS privacy, but it also made each
next audit slower and easier to drift from production parser and seed behavior.

## Decision

- Add a gated local-only `PrivateSmsExportAuditTest` that reuses
  `BuiltInSmsTemplateRegistry` and the active `seed_rules.json`.
- Read the export from `-Dathar.privateSmsExport=...`,
  `ATHAR_PRIVATE_SMS_EXPORT`, or the maintainer's local default path when it
  exists.
- Write only redacted aggregate JSON to the repository-root
  `build/private-corpus-audit/latest.json`, regardless of the Gradle module
  working directory.
- Record `rawBodiesWritten = 0` and avoid writing raw SMS bodies, amounts by
  row, senders, or merchant samples.
- Promote the one post-R16 public catalog hit, `buffet`, as a priority-90
  restaurant rule.

## Acceptance

- `seed_rules.json` increases from 705 to 706 active rules.
- Every new pattern references an existing category id.
- No duplicate normalized seed patterns are introduced.
- The asset test pins `buffet` at priority 90.
- The private audit test passes locally when the export exists and skips when
  the export is unavailable.
- The private audit report contains aggregate counts and public inactive-catalog
  candidates only; raw SMS contents are not committed or written to the report.

## Private Audit Result

Temporary post-R16 audit before the seed change found one inactive public catalog
hit: `buffet` -> `cat-restaurant`.

After adding the rule, the redacted local report over
`All Conversations 2026-05-27 175517.txt` showed:

- Records inspected: 7,887.
- Parser successes: 3,864.
- Ignored messages: 4,023.
- Failed messages: 0.
- Failed known-bank messages: 0.
- Parsed expenses: 2,375.
- Categorized parsed expenses: increased from 1,273 to 1,274.
- Uncategorized parsed expenses: reduced from 1,102 to 1,101.
- Parsed expense rows with missing merchants: 0.
- Raw bodies written to audit output: 0.
- Remaining inactive public-catalog matches: 0.

## Non-goals

- Do not add private-person, account-tail, sender, or raw SMS-body values to
  public seed files.
- Do not promote broad inactive catalog words such as `pizza`, `hospital`,
  `clinic`, `pharmacy`, `gym`, `airport`, `metro`, or `university`.
- Do not add cloud/user-data collection for categorization.

## Validation

- 2026-06-14: `:ingestion:sms-parser:test` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: `:core:data:testDebugUnitTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: full JVM test/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon
  --max-workers=1`.
- 2026-06-14: runtime install/launch was attempted on `AtharPixelQaApi35`.
  `emulator -accel-check` reported the Android Emulator hypervisor driver is
  not installed. A bounded `-accel off` boot emitted startup logs but stayed
  `emulator-5554 offline`, so APK install, launch, and screenshot capture could
  not run. The stuck headless qemu process and AVD lock files were cleaned.
