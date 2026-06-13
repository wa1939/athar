# G-12 - CSV Manual Column Mapping

## Goal

Make statement import useful when a bank exports CSV/TSV files with header names Athar does not
recognize. Earlier G-12 slices made import safe through preview/confirm, delimiter detection,
debit/credit markers, OFX/QFX, MT940, destination-account selection, and duplicate-safe source
references. The remaining CSV gap is manual header mapping.

## Decision

Keep one conservative preview-confirm pipeline. When CSV auto-detection cannot find a date,
merchant/description, and money column, `CsvImportTrigger.preview` now returns a mapping-required
result with the parsed header list. Settings keeps the selected file bytes locally, shows the
headers, lets the user choose columns for date, merchant, single amount or debit/credit, currency,
category, type, and notes, then reruns preview with the selected mapping. Confirmation imports only
after the mapped preview succeeds.

Manual mappings are overrides on top of existing auto-detection. OFX/QFX and MT940 keep their
specialized parser paths and do not expose CSV mapping controls.

## Acceptance

- Unknown CSV headers no longer dead-end as an import failure; the user can map columns and preview.
- Mapped debit/credit columns import as expense/income through the same duplicate-safe source-ref
  builder.
- Mapped category, currency, type, and notes columns are preserved when present.
- Existing CSV/TSV, OFX/QFX, MT940, account-selection, and duplicate-safety behavior still works.
- No rows are inserted until the mapped preview is confirmed.

## Validation

- 2026-06-13: `:core:data:testDebugUnitTest :feature:settings:testDebugUnitTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: Full JVM test/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- 2026-06-13: Runtime Settings import tap-through was blocked because `adb devices -l` returned
  no attached devices.

## Non-goals

- Per-row editing in the preview table remains a future G-12 follow-up.
- At the time of this CSV mapping slice, XLSX direct import was deferred; post-beta.24
  G-12 now supports transaction-grid `.xlsx` files through the same preview path.
- Do not infer categories with cloud services or upload statement contents.
