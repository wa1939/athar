# R-07 - Bulk Categorize Backlog Context

## Problem

The bulk-categorize CSV roundtrip already lets a user export every row that
needs category cleanup, fill `category_id` with ChatGPT/Claude, and import the
file back into Athar. After larger SMS backfills, though, the export can contain
hundreds of rows where the real work is not hundreds of separate decisions: it
is the same merchant repeated many times.

The old export also included transfer rows. Transfers normally do not have
categories, so sending them into an AI category workflow adds noise without
reducing the category backlog.

## Decision

Make `athar-uncategorized.csv` more action-oriented while preserving import
compatibility:

- Export only non-transfer transactions that need a category decision.
- Keep PENDING, DISMISSED, and confirmed-without-category expense/income rows.
- Add `merchant_group_count`, the count of export rows sharing the same
  normalized merchant key.
- Sort repeated merchant groups first, then by merchant key and newest date.
- Follow-up: [`R-07 bulk categorize category options`](R-07-bulk-categorize-category-options.md)
  makes the CSV self-contained with row-level active category options.

The importer already ignores unknown columns, so older imports and manually
edited CSV files remain supported.

## Acceptance

- Transfer rows are excluded from the bulk category export.
- Repeated merchants appear together and before singletons.
- Each exported row includes a `merchant_group_count` value.
- Existing import matching by `id`, `stable_key`, and content fingerprint still
  passes.
- Documentation and Settings copy describe the category-backlog scope.

## Non-goals

- Do not add an inline AI API or API-key storage.
- Do not change the import contract that only `category_id` should be edited.
- Do not infer categories automatically from group counts.

## Validation

- `:core:data:testDebugUnitTest` passes, including export ordering/transfer-skip coverage.
- Full stack passes: `test`, `:app:assemblePersonalFullSmsDebug`,
  `:app:assembleStoreSafeDebug`, `:app:lintPersonalFullSmsDebug`, and
  `:app:lintStoreSafeDebug`.
- Gated private SMS export audit passes with 7,887 records, 3,864 parser
  successes, 0 failed known-bank messages, 0 missing-merchant parsed expenses,
  and 1,101 uncategorized parsed expenses remaining for the bulk-categorize
  workflow.
- Runtime install/launch was attempted on `AtharPixelQaApi35`, but the local
  emulator exited before ADB came online with Windows access-violation code
  `-1073741819`; cleanup finished with no attached ADB device, no emulator/qemu/
  netsim process, and no AVD lock files.
