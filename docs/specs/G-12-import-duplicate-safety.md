# G-12 - Statement Import Duplicate Safety

## Problem

Statement imports used generic source references such as `csv-row-2` for CSV rows and raw OFX/MT940 references without the selected account. Because `transactions.sourceRefId` is unique in the database, a later import could replace an older imported transaction that happened to use the same row reference. Re-importing the same statement could also create avoidable cleanup work or overwrite edits.

## Decision

Make statement import references stable and account-scoped for CSV, OFX/QFX, and MT940. Each imported row now receives an `import:<format>:<hash>` reference derived from the selected account plus either the bank-provided row id (`FITID`, MT940 reference) or a normalized content fingerprint with an occurrence counter for repeated identical rows.

Preview and confirm now both build the plan for the selected destination account. Rows whose stable reference already exists for that account are counted as skipped with the reason `Already imported` and are not written again. The Settings account picker re-previews the selected file when the destination account changes, so duplicate counts stay aligned with the account the user is about to confirm.

This slice intentionally does not add manual column mapping or row editing. It protects the existing preview-confirm workflow from duplicate/replacement mistakes.

## Acceptance

- Re-importing the same CSV statement into the same account imports zero duplicate rows.
- Importing the same statement into a different account still works and produces different account-scoped references.
- OFX/QFX and MT940 rows use stable account-scoped import references.
- Preview and confirm use the same selected account for duplicate detection.
- Parse failures are still skipped without aborting the batch.

## Validation

- `:core:data:testDebugUnitTest`
- `:core:data:compileDebugKotlin`
- `:feature:settings:testDebugUnitTest`
- `:feature:settings:compileDebugKotlin`
- Full JVM test/build/lint stack
- `git diff --check`
- Runtime import tap-through remains under QA-01 until a physical Android device or accelerated emulator is available.
