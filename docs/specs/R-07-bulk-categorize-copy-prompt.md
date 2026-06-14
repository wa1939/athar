# R-07 - Bulk Categorize Copy Prompt

## Problem

The bulk-categorize workflow depends on an external AI prompt, but the in-app
Settings card only told users to run the exported CSV through ChatGPT or Claude.
That leaves the exact prompt buried in documentation and makes the workflow feel
developer-oriented: export is available in the app, import is available in the
app, but the bridge between them still requires finding and copying a markdown
section manually.

## Decision

- Add a provider-neutral bulk-categorize prompt value in domain code.
- Add a **Copy AI prompt** action to the Settings bulk-categorize card.
- Copy the prompt to the Android clipboard with a localized clipboard label and
  an in-card copied confirmation.
- Keep the prompt strict about preserving CSV headers, row order, matching
  columns, type-compatible category IDs, blank uncertain rows, and repeated
  merchant group behavior.

## Acceptance

- Settings exposes the prompt action next to the existing export/import flow.
- The copied prompt includes the current CSV columns, including
  `merchant_group_count`, `category_options`, and `category_id`.
- The prompt tells the AI to avoid guesses, leave transfer or uncertain rows
  blank, and preserve every non-category column.
- English and Arabic UI strings are present.
- Tests guard the prompt contract that the importer relies on.

## Non-goals

- Do not add inline AI API calls, API-key storage, or network access.
- Do not change the CSV import/export format.
- Do not localize the prompt body itself; the prompt is written for external
  model reliability and already includes Arabic categorization keywords.

## Validation

- `:core:domain:test` covers the copied prompt contract.
- `:feature:settings:testDebugUnitTest` covers Settings compilation and
  ViewModel contracts with the new UI call site.
- `git diff --check` passed.
- Full validation passed with:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime install/launch was attempted with the built `personalFullSmsDebug`
  APK. `emulator -accel-check` still reports that the Android Emulator
  hypervisor driver is not installed, and the bounded hidden
  `AtharPixelQaApi35` software launch stayed `emulator-5554 offline`, so APK
  install, screenshot capture, UI dump, and logcat capture could not run.
  Cleanup finished with no attached ADB device, no emulator/qemu/netsim process,
  and no AVD lock files.
