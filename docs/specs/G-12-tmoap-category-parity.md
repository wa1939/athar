# G-12 - TMOAP Category Parity

## Problem

The bundled category catalog did not fully cover the concrete Category Setup
sheet in the TMOAP workbook being used as the replacement target. Statement
imports could also resolve category names without considering the transaction
type, so an income row labeled `Other` could resolve to the expense category.

## Decision

Add the missing TMOAP category setup defaults to `seed_categories.json`:

- Expense: `cat-condo-fees`, `cat-work-expense`
- Income: `cat-tax-refund`, `cat-reimbursements`, `cat-bonus`,
  `cat-other-income`

Statement CSV/XLSX category resolution is now type-aware. It resolves only
categories whose kind matches the row type, treats transfers as uncategorizable,
and includes TMOAP label aliases for:

- `Public transportation` -> `cat-public-transport`
- `Wife` -> `cat-wife-allowance`
- `Job` -> `cat-salary`
- `Side project` -> `cat-side-income`
- income `Other` -> `cat-other-income`

The bulk AI prompt and SMS-triage documentation were updated so fallback
category lists match the bundled category catalog.

## Acceptance

- Existing installs receive the newly bundled categories through the
  upgrade-safe category seeding path.
- Statement import maps the TMOAP expense categories without requiring manual
  category edits.
- Statement import maps TMOAP income labels to income categories, including
  income `Other`.
- Category name/id/Arabic lookup does not cross expense and income kinds.
- Transfer rows do not receive categories through category lookup.
- Documentation and copied AI prompt fallback lists include the new category
  IDs.

## Validation

- 2026-06-14: focused validation passed with JDK 21:
  `:core:data:testDebugUnitTest --tests "com.athar.core.data.csv.CsvImporterTest"
  --tests "com.athar.core.data.seed.SeedRulesAssetTest"
  --tests "com.athar.core.data.seed.CategorySeedPlannerTest"
  :core:domain:test --tests "com.athar.core.domain.repo.MerchantBulkAiPromptTest"`.
- 2026-06-14: full validation passed with JDK 21:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- 2026-06-14: runtime `personalFullSmsDebug` install was attempted with the built
  APK, but no emulator reached online ADB. `emulator -accel-check` reported the
  Android Emulator hypervisor driver is not installed; both bounded hidden
  `AtharPixelQaApi35` software launches (`-gpu swiftshader_indirect -accel off`
  and `-gpu off -accel off`) exited after 4 seconds with Windows
  access-violation code `-1073741819`. Evidence was written to
  `build/qa/tmoap-category-parity-emulator/` and
  `build/qa/tmoap-category-parity-emulator-gpu-off/`, and cleanup left no
  attached ADB device, no emulator/qemu/netsim process, and no AVD lock files.

## Non-goals

- No full spreadsheet editor.
- No FX conversion.
- No automatic reclassification of existing user transactions.
- No public seed rules are added in this slice.
