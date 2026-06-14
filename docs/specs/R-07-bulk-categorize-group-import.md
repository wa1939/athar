# R-07 - Bulk Categorize Group Import

## Problem

After S-21 and R-18, the categorization backlog is visible as repeated merchant
groups. The CSV export already sorts repeated merchants together and includes
`merchant_group_count`, but import still required a category on every row. That
keeps the workflow noisy when 20, 50, or more rows clearly belong to the same
category.

## Decision

Teach the importer to propagate one unambiguous category choice within the
imported CSV:

- filled rows still update their matched transaction directly;
- when a repeated merchant group has exactly one filled category, blank rows in
  that same imported group inherit it;
- when a merchant group has conflicting filled categories, only explicit rows
  update and blank peers stay unchanged;
- learned `CategoryRule` rows are created only for unambiguous merchant groups.

This preserves the existing contract that `category_id` is the only editable
column. It also keeps propagation scoped to rows present in the user-approved
CSV, avoiding broad updates to unrelated transfers or transactions outside the
review file.

## Acceptance

- A CSV with one filled row and one blank peer for the same `merchant_normalized`
  updates both transactions.
- A CSV with conflicting categories for the same merchant updates only the
  explicitly filled rows.
- Conflicting merchant groups do not train a learned category rule.
- Older CSVs without `merchant_group_count` or `category_options` still import
  through id/stable-key/content matching.
- Documentation explains that blank repeated-group peers may inherit a category.

## Non-goals

- Do not add inline AI APIs or API-key storage.
- Do not infer categories from `category_options` alone.
- Do not update transactions outside the imported CSV group.
- Do not promote private/local merchant labels into shared seed rules.

## Validation

- `:core:data:testDebugUnitTest` passes, including group propagation, conflict
  safety, and imported-CSV scoping coverage.
- The full JVM test/build/lint stack passes with JDK 21:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime install/launch was attempted with the built `personalFullSmsDebug` APK.
  `AtharPixelQaApi35` exited before exposing ADB with Windows access-violation
  code `-1073741819`, so install and launch could not run; cleanup finished with
  no attached ADB device, no emulator/qemu/netsim process, and no AVD locks.
