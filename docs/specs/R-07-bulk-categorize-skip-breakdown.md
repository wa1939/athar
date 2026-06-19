# R-07 - Bulk Categorize Skip Breakdown

## Problem

Bulk category import returned only three totals: updated rows, learned rules, and
skipped rows. After adding grouped import, exact rule learning, and type-safe
category checks, a skipped total alone is not actionable. A user or maintainer
cannot tell whether the filled CSV had unknown category IDs, wrong expense/income
category kinds, deleted transactions, conflicting repeated-merchant groups, or
rows left blank because the AI was uncertain.

That makes the external-AI workflow feel opaque exactly when the user needs to
repair the file and retry.

## Decision

- Keep the existing `updated`, `rulesAdded`, and `skipped` totals.
- Add `MerchantBulkImportSkipSummary` to the import result with separate counts
  for malformed rows, unknown categories, incompatible categories, missing
  transactions, conflicting repeated-merchant groups, and blank rows without a
  group category choice.
- Keep detailed row numbers in logcat, but surface aggregate skip reasons in the
  Settings bulk-categorize card after import.
- Show only non-zero reason groups in the Settings status line so the card stays
  compact.

## Acceptance

- Unknown category IDs increment the unknown-category skip count.
- Filled rows that no longer match any transaction increment the missing
  transaction skip count.
- Incompatible category/type rows increment the incompatible-category skip count.
- Blank rows in conflicting repeated-merchant groups increment the conflict
  count.
- Blank rows with no representative category increment the left-blank count.
- Malformed rows increment the bad-row count.
- Existing import callers can still read the original totals.

## Non-goals

- Do not expose raw merchant names, SMS bodies, amounts, or row contents in the
  UI summary.
- Do not add an import preview/editor for the bulk categorize CSV in this slice.
- Do not change the CSV format.
- Do not change category matching, propagation, or exact-rule learning behavior
  beyond reporting why rows were skipped.

## Validation

- `:core:data:testDebugUnitTest` covers the importer reason counters.
- `:feature:settings:testDebugUnitTest` covers Settings compilation and ViewModel
  contracts with the new result shape.
- `git diff --check` passed.
- Full validation passed with:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime install/launch was attempted with the built `personalFullSmsDebug`
  APK. `emulator -accel-check` still reports that the Android Emulator
  hypervisor driver is not installed, and the bounded hidden
  `AtharPixelQaApi35` software launch stayed `emulator-5554 offline`, so APK
  install, screenshot capture, UI dump, and logcat capture could not run.
  Cleanup finished with no attached ADB device, no emulator/qemu process, and
  no AVD lock files.
