# G-12 - XLSX Import Preview

## Problem

Athar can import TMOAP/Athar CSVs and common bank statement CSV/TSV, OFX/QFX, and MT940 files, but a user with the original Excel workbook or a bank-exported `.xlsx` still has to open another app and save a CSV first. That extra spreadsheet-preprocessing step is exactly the kind of friction Athar is supposed to remove.

## Decision

Add direct `.xlsx` support to the existing statement exchange path. The importer should detect OpenXML workbooks, read visible worksheet rows, find a statement-like header row, and reuse `StatementCsvMapper` so dates, merchants, amounts, debit/credit columns, currencies, categories, notes, manual mapping, row exclusions, row edits, account overrides, currency summaries, and duplicate-safe import references all behave like the CSV path.

The implementation stays dependency-light: parse the zipped OpenXML parts with JDK XML/ZIP APIs rather than adding Apache POI to the APK. This slice supports transaction-grid workbooks, including TMOAP-style Expenses and Income sheets. If a workbook has formulas with cached values, the cached cell values are used; if a sheet has no recognizable transaction header, it is skipped or returned for manual column mapping.

## Acceptance

- Settings statement exchange and onboarding document pickers accept `.xlsx` MIME types.
- XLSX preview never inserts rows before confirmation.
- A workbook sheet with `Date`, `Description`, `Debit`, `Credit`, `Currency`, and `Category` previews and imports through the same row/sample/currency-summary path as CSV.
- TMOAP-style separate `Expenses` and `Income` sheets with positive Amount columns preserve expense vs income direction from the sheet name when no explicit type column exists.
- Duplicate detection uses `import:xlsx:` source references and remains account-scoped.
- Unknown workbook headers return the existing manual mapping state rather than guessing.

## Non-Goals

- Legacy `.xls` binary parsing.
- Full Excel formula evaluation beyond cached workbook values.
- Importing non-transaction TMOAP sheets through statement exchange. Budget Targets and Wishlist now have separate explicit Settings import flows; Investments remains covered by existing app surfaces and the private seeded build pipeline.

## Validation

- Focused unit tests for XLSX preview, import, mapping-required behavior, TMOAP-style Expenses/Income sheet direction, and account-scoped duplicate references:
  `.\gradlew.bat --console=plain :core:data:testDebugUnitTest --no-daemon --max-workers=1`
- Full Gradle test/build/lint command before push:
  `.\gradlew.bat --console=plain test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon --max-workers=1`
- Runtime Settings/onboarding picker tap-through remains under QA-01. On 2026-06-13 no Android device was attached, `emulator -accel-check` reported that the Android Emulator hypervisor driver is not installed, and a bounded software boot of `AtharPixelQaApi35` stayed `offline` in ADB for 3 minutes, so the APK could not be installed or launched locally.
