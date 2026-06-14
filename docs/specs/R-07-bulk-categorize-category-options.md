# R-07 - Bulk Categorize Category Options

## Problem

The bulk-categorize CSV now focuses the backlog well: transfers are excluded,
repeated merchants are grouped first, and `merchant_group_count` shows where one
decision can clean many rows. The remaining workflow friction is that the CSV is
not self-contained. A user or AI assistant still has to read the docs or the
seed category asset to know which `category_id` values are valid, and that can
miss user-edited or custom active categories.

## Decision

Add a read-only `category_options` column to the export. Each row lists the
active categories that match that row's transaction type:

- expense rows list active expense categories;
- income rows list active income categories;
- transfer rows remain excluded from this workflow.

The format is compact and stable: `categoryId=English name / Arabic name`,
separated by ` | `. `category_id` remains blank and remains the only editable
column. The importer already resolves columns by header name and ignores
unknown columns, so older files and the stable-key matching path remain
compatible.

## Acceptance

- Exported rows include `category_options` before `amount`.
- Expense rows do not include income-only categories.
- Income rows do not include expense-only categories.
- Archived categories are omitted.
- The stable bulk importer still accepts both new exports and old CSVs without
  this column.
- Documentation and Settings copy explain that `category_options` is context,
  not an editable import field.

## Non-goals

- Do not add an inline AI API or API-key storage.
- Do not infer categories automatically from `category_options`.
- Do not change the import contract that only `category_id` should be edited.

## Validation

- `:core:data:testDebugUnitTest` passes, including category-option export
  coverage for expense, income, and archived category filtering.
- Full stack passes: `test`, `:app:assemblePersonalFullSmsDebug`,
  `:app:assembleStoreSafeDebug`, `:app:lintPersonalFullSmsDebug`, and
  `:app:lintStoreSafeDebug`.
- Gated private SMS export audit passes. The latest module-scoped report for
  the current local export shows 7,887 records, 3,864 parser successes, 4,023
  ignored messages, 0 parser failures, 0 failed known-bank messages, 2,648
  parsed expenses, 1,263 categorized expenses, 1,385 uncategorized expenses,
  193 missing-merchant parsed expenses, 0 raw bodies written, and no inactive
  catalog matches.
- Runtime install/launch was attempted. `AtharPixelQaApi35` started under
  `-no-window -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect
  -accel off` but stayed `emulator-5554 offline` for the bounded wait, so APK
  install and screenshot capture could not run. `AtharPixelQaApi35Arm` exited
  because arm64 system images are unsupported by this x86_64 host. Cleanup
  finished with no attached ADB device, no emulator/qemu/netsim process, and no
  AVD lock files.
