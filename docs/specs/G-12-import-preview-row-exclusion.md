# G-12 - Import Preview Row Exclusion

## Problem

Statement import preview showed detected columns, counts, and sample rows, but the user could not
remove a bad parsed row before confirmation. That left a risky all-or-nothing choice: cancel the
whole statement or import rows the user already noticed were wrong.

## Decision

Add an explicit `CsvImportRowDecision` contract keyed by original row number. Settings stores row
decisions alongside the pending import bytes and mapping, re-runs preview whenever a row is
included/excluded, and passes the same decisions into confirmation.

Excluded rows:

- remain visible in the preview sample with the include checkbox off
- count as skipped with the reason `Excluded from import`
- are never inserted on confirmation
- do not affect duplicate detection for other rows in the same file

This is a conservative first row-review slice. It does not add field-level editing of merchant,
amount, date, category, or account per row.

## Acceptance

- Preview and confirm use the same row decisions.
- Excluding a parsed row reduces importable count and increases skipped count.
- Excluded rows remain visible in preview so the user can include them again.
- Excluded rows are not upserted.
- Existing duplicate and parse-error skip behavior remains unchanged.

## Validation

- 2026-06-13: `:core:data:testDebugUnitTest :feature:settings:testDebugUnitTest` passed
  with JDK 17, `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: Full JVM test/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- 2026-06-13: Runtime Settings import tap-through remains blocked because
  `adb devices -l` returned no attached devices.

## Non-goals

- No field-level row editing.
- No per-row account assignment.
- No cloud parsing or external AI processing.
