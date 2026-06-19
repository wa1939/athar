# R-07 - Bulk Categorize Import Impact Summary

## Problem

The bulk-categorize import preview showed the total number of transactions that
would update, exact local rules that would be added, and skipped rows. That
proved the import was bounded, but it still left the user guessing where the
updates would land before tapping **Apply import**.

For large AI- or spreadsheet-filled CSVs, the most important safety question is
not only "how many rows change?" but also "which categories will receive those
rows?"

## Decision

Extend the dry-run bulk importer with a category-impact summary. The summary is
computed only after the same row matching, category resolution, type-safety, and
safe same-merchant propagation checks that the real import uses. Settings then
shows a compact localized line such as:

`Category impact: Coffee 12 - Groceries 4 - Home maintenance 2.`

The result stays aggregate-only: no merchant names, row ids, amounts, notes, SMS
bodies, or raw CSV cells are shown in the Settings card.

## Acceptance

- Bulk import preview returns category id, English name, Arabic name, and update
  count for each category that would receive updates.
- Impact counts include safe propagated blank peers in the imported merchant
  group.
- Preview still does not mutate transactions or learned rules.
- Settings renders the category-impact line during preview before **Apply
  import**.
- Import write semantics, skip semantics, exact-rule upsert behavior, and CSV
  header compatibility remain unchanged.

## Non-goals

- Do not add row-level preview or editing.
- Do not infer categories automatically inside Athar.
- Do not add inline AI APIs, network calls, or API-key storage.
- Do not expose merchant names or raw SMS bodies in the preview summary.

## Validation

- 2026-06-15: Focused importer and Settings ViewModel tests passed with JDK 17:
  `:core:data:testDebugUnitTest --tests "com.athar.core.data.csv.MerchantBulkCsvTest"`
  and `:feature:settings:testDebugUnitTest --tests "com.athar.feature.settings.SettingsViewModelTest"`.
- 2026-06-15: Full JVM/debug-APK/lint validation passed with JDK 17 and
  `ATHAR_PRIVATE_SMS_EXPORT` set:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- 2026-06-15: Runtime `personalFullSmsDebug` install/launch was attempted with
  the built APK. `AtharPixelQaApi35` exited before exposing an online ADB device
  with exit code `1` because x86_64 emulation requires hardware acceleration and
  the Android Emulator hypervisor driver is not installed. `AtharPixelQaApi35Arm`
  also exited because arm64 system images are unsupported on this x86_64 host.
  Evidence is in `build/qa/bulk-categorize-import-impact-summary-emulator/`.
