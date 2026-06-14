# S-21 - Support Diagnostics Category Backlog

## Problem

Support diagnostics helped parser triage by exporting SMS audit counts, sender
hashes, body-shape fingerprints, and redacted parser-error groups. After the
private-corpus parser cleanup, the next support question is often different:
how much manual cleanup remains because transactions parsed but did not receive a
category?

The previous diagnostics file could not answer that without asking users for
raw merchant names, raw SMS bodies, or the bulk-categorization CSV. That is too
much data for first-line triage and conflicts with the local-first privacy
promise.

## Decision

Extend `athar-support-diagnostics.json` with:

- `transaction_summary`: aggregate counts for total transactions, categorized
  transactions, uncategorized transactions, non-transfer category-backlog rows,
  status/source/type counts, and transfer rows excluded from the backlog.
- `uncategorized_merchant_groups`: top repeated uncategorized non-transfer
  merchant groups, represented only by a 12-character merchant hash plus coarse
  length/script, status/source/type/currency/confidence buckets, and first/last
  dates.

The report still omits raw merchant names, raw transaction rows, notes, amounts,
balances, senders, SMS bodies, card numbers, and account numbers.

## Acceptance

- Support diagnostics include aggregate categorization-backlog counts.
- Repeated uncategorized merchants are visible only as hashes and coarse buckets.
- Transfer rows with no category are counted separately and excluded from the
  category-backlog total because transfers are normally uncategorized by design.
- Unit tests prove raw merchant names, amount values, and private notes are not
  serialized.
- Existing parser diagnostics remain present.

## Non-goals

- Do not export raw merchant names or transaction rows.
- Do not replace the bulk-categorize CSV workflow, which remains the explicit
  user-approved path for AI-assisted categorization.
- Do not infer categories from diagnostics alone.

## Validation

- 2026-06-14: `:core:data:testDebugUnitTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: Full JVM/build/lint stack passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`: `test`,
  `:app:assemblePersonalFullSmsDebug`, `:app:assembleStoreSafeDebug`,
  `:app:lintPersonalFullSmsDebug`, and `:app:lintStoreSafeDebug`.
- 2026-06-14: Runtime install/launch was attempted against
  `AtharPixelQaApi35` using the built `personalFullSmsDebug` APK. Both bounded
  software-mode launches (`-gpu swiftshader_indirect` and `-gpu off`, each with
  `-accel off`) exited before exposing ADB with Windows access-violation code
  `-1073741819`, so APK install, launch, and screenshot capture could not run
  on this host. AVD lock files were cleaned afterward.
