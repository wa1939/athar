# G-12 - Import Row Account Overrides

## Problem

Statement import could choose one destination account for the whole file, but real exports can mix
accounts: card repayments, transfers, shared wallet rows, and bank-side correction rows may belong
somewhere else. Without per-row account review, users still had to import into one account and clean
up the ledger after confirmation.

## Decision

Extend the existing `CsvImportRowEdit` contract with an optional `accountId`, and expose the final
row account on `CsvImportPreviewRow`. Settings shows each preview row's target account when more
than one active account exists, and the row edit dialog lets the user pick another active account.

The importer applies account overrides before stable source-reference generation and duplicate
checks. Duplicate state and stable reference builders are now cached per target account, per import
format, so a row moved from Cash to Checking is compared against Checking's prior imports and gets a
Checking-scoped import reference.

## Acceptance

- Preview rows expose the target account id.
- Settings shows row account labels when multiple active accounts are available.
- Settings can save a row-level account override from the edit dialog.
- Preview and confirmation receive the same row account override.
- Imported transactions use the edited row account.
- Duplicate detection uses the edited row account, not the default file account.
- CSV, OFX/QFX, and MT940 share the same account override path.

## Validation

- 2026-06-13: `:core:data:testDebugUnitTest :feature:settings:testDebugUnitTest` passed
  with JDK 17, `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: Full JVM test/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- 2026-06-13: Runtime Settings import tap-through could not complete. `adb devices -l`
  returned no attached devices, and `emulator -accel-check` reports that the Android
  Emulator hypervisor driver is not installed.

## Non-goals

- No full-file spreadsheet grid editor; transaction-grid `.xlsx` import shipped later
  in the G-12 XLSX preview slice.
- No FX conversion.
- No full-file spreadsheet grid editor.
