# G-12 - Import Row Field Overrides

## Problem

Statement import preview could exclude rows, but it could not correct a row that was mostly right.
Users still had to cancel, edit the source file, and import again when a bank exported a vague
merchant, wrong category, missing note, or amount/currency/type that needed manual review.

## Decision

Add `CsvImportRowEdit`, keyed by original row number, to the import preview and confirmation
contract. Settings keeps row edits beside the pending import bytes, mapping, account, and row
decisions, then re-runs preview whenever an edit is saved.

Editable fields:

- date
- merchant
- amount
- currency
- type
- category
- notes

The importer applies edits before stable source-reference generation and duplicate checks. That
means the preview, per-currency summaries, duplicate-skip behavior, and final inserted transaction
all use the same reviewed row values. Invalid edits are skipped conservatively with row-level
reasons instead of committing guessed data.

## Acceptance

- Preview rows expose notes and an edited marker.
- Settings can edit date, merchant, amount, currency, type, category, and notes from the preview.
- Saving an edit re-runs preview immediately.
- Confirmation receives and applies the same row edits.
- Stable import references are generated from the edited transaction content.
- Invalid edited required fields skip the row instead of inserting partial data.
- CSV, OFX/QFX, and MT940 share the same edit application path.

## Validation

- 2026-06-13: `:core:data:testDebugUnitTest :feature:settings:testDebugUnitTest` passed
  with JDK 17, `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: Full JVM test/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- 2026-06-13: Runtime Settings import tap-through could not complete. `adb devices -l`
  returned no attached devices. Launching the only installed AVD, `AtharPixelQaApi35`,
  exited with `x86_64 emulation currently requires hardware acceleration` because the
  Android Emulator hypervisor driver is not installed.

## Non-goals

- Per-row account assignment was left to the follow-up G-12 row account overrides slice.
- No FX conversion.
- No spreadsheet-style editing of every row in the file.
