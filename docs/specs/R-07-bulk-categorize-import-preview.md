# R-07 - Bulk Categorize Import Preview

## Problem

The bulk-categorize workflow lets a filled AI/human CSV update many
transactions and exact local merchant rules in one pass. Before this slice,
choosing **Import categorized** applied the file immediately. That was fast, but
too sharp for an external-AI artifact: a bad category column, stale file, or
unexpected skip pattern could write changes before the user saw the impact.

## Goals

- Run the filled bulk-categorize CSV through the same parser/import logic in a
  dry-run mode before writing anything.
- Show aggregate update, exact-rule, and skip counts in the Settings card.
- Keep the selected CSV bytes only in ViewModel memory until the user confirms
  or cancels.
- Preserve old and new CSV schema compatibility; this slice does not add or
  remove columns.

## Implementation

- `MerchantBulkImportTrigger` now exposes `previewCategorizations(input)` in
  addition to `importCategorizations(input)`.
- `MerchantBulkImporter` routes both methods through one shared
  `processCategorizations` path. Preview records the would-update transaction
  ids and would-upsert exact local rules without calling repository write APIs.
- Settings now treats file selection as preview. The card shows
  `Ready to update X · add Y rules · skip Z`, then offers **Apply import** and
  **Cancel preview**.
- Confirmation reuses the same in-memory bytes and then clears them after a
  success or failure. Export, cancel, and clear also discard any pending preview.

## Non-goals

- No row-level preview/editor in this slice.
- No inline AI provider or API-key flow.
- No change to category choice, group propagation, type-safety, skip-summary, or
  exact-rule upsert semantics.

## Validation

- Focused importer and Settings ViewModel tests passed:
  `:core:data:testDebugUnitTest --tests "com.athar.core.data.csv.MerchantBulkCsvTest"`
  and `:feature:settings:testDebugUnitTest --tests "com.athar.feature.settings.SettingsViewModelTest"`.
- Full JVM/debug-APK/lint stack passed with JDK 17:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime install/launch was attempted with the built `personalFullSmsDebug`
  APK. `emulator -accel-check` reported that the Android Emulator hypervisor
  driver is not installed, and the bounded hidden `AtharPixelQaApi35` launch
  exited after 5 seconds with exit code `1` because x86_64 emulation requires
  hardware acceleration. Evidence is under
  `build/qa/bulk-categorize-import-preview-emulator/`.
