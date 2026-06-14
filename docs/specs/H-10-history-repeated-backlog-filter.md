# H-10 - History Repeated Backlog Filter

## Problem

H-08 and H-09 make same-merchant cleanup and exact local rule learning faster,
but users still need to find repeated uncategorized merchants inside long History
lists. The private audit shows parser health is no longer the main issue: the
remaining cleanup work is concentrated in uncategorized parsed expenses, including
repeated merchant groups.

## Decision

Add a `Repeated backlog` category-state filter to History. It is deliberately a
cleanup filter, not a new transaction status:

- It runs after the current status, type, source, and search filters.
- It includes only uncategorized, non-transfer rows.
- It groups by normalized merchant plus transaction category kind, so expense and
  income rows do not make a mixed group look bulk-actionable.
- It ignores generic or blank merchant keys.
- It preserves the existing row order and reuses the existing selection mode,
  `Same merchant`, category picker, exact-rule learning, and filter-change
  selection clearing.

## Acceptance

- History exposes a localized `Repeated backlog` category chip.
- Selecting it shows only actionable repeated uncategorized merchant groups in
  the currently filtered result set.
- Categorized rows, transfers, singletons, generic merchants, and mixed
  expense/income-only pairs do not appear as repeated cleanup groups.
- Existing `All`, `Uncategorized`, and `Categorized` behavior remains unchanged.

## Non-goals

- Do not add a new database query or schema field.
- Do not expose raw private merchant names in diagnostics or documentation.
- Do not train rules from merely viewing the repeated backlog; learning still
  happens only after an explicit compatible category assignment.

## Validation

- 2026-06-15: Focused `HistoryFilterTest` and `HistoryViewModelTest` passed with
  JDK 17, `--no-daemon`, and `--max-workers=1`.
- 2026-06-15: Full `:feature:today:testDebugUnitTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-15: `git diff --check` passed.
- 2026-06-15: Full JVM/debug-APK/lint stack passed with JDK 17:
  `test`, `:app:assemblePersonalFullSmsDebug`, `:app:assembleStoreSafeDebug`,
  `:app:lintPersonalFullSmsDebug`, and `:app:lintStoreSafeDebug`.
- 2026-06-15: Runtime `personalFullSmsDebug` install was attempted with the
  built APK. The local AVD exists, but `emulator -accel-check` reports the
  Android Emulator hypervisor driver is not installed, and a bounded hidden
  `AtharPixelQaApi35` software launch stayed `emulator-5554 offline` without an
  online ADB device or `boot_completed=1`. Install, screenshot, UI dump, and
  logcat capture could not run. Evidence was written to
  `build/qa/history-repeated-backlog-filter-emulator/`; cleanup stopped the
  headless qemu process, removed stale AVD locks, killed ADB, and finished with
  no emulator/qemu/adb/netsim process and no AVD lock files.
