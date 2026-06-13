# G-12 — CSV Import Preview and Confirm

## Problem

Athar can already import TMOAP/Athar CSVs and common bank statement CSVs, but the Settings action
previously committed rows immediately after file selection. That is risky for a spreadsheet
migration workflow: a user needs to see whether Athar detected the right date, description, amount,
debit, credit, currency, and category columns before any transaction is inserted.

## Decision

Add a conservative preview step before CSV import commits:

- Selecting a CSV builds a preview from the same mapper used by the real importer.
- Preview shows importable row count, skipped row count, detected columns, a small sample of parsed
  rows, and the first skipped row reason when present.
- Confirm imports the same selected CSV bytes the preview was built from.
- Cancel clears the pending bytes and returns the card to idle.
- Existing export behavior stays unchanged.

This is not the full G-12 import wizard. Manual column remapping, OFX/QFX/MT940,
and richer per-row editing were left as follow-ups.

## Acceptance Criteria

- CSV file selection does not insert transactions immediately.
- Preview and import share the same parsing path.
- Confirm imports only after preview is ready.
- Cancel discards the pending file bytes.
- Invalid CSVs still fail with a clear reason.
- The Settings UI remains localized in Arabic and English.

## Validation

- 2026-06-13: `:core:data:test` and `:feature:settings:test` passed with
  `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`.
- 2026-06-13: `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug` passed with `--no-daemon --max-workers=1`.
- 2026-06-13: `adb devices -l` returned no attached devices, so runtime import tap-through and screenshot UI audit remain blocked under QA-01.
