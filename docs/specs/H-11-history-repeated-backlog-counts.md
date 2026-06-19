# H-11 - History Repeated Backlog Counts

## Problem

H-10 isolates repeated uncategorized merchant groups, but the rows still looked
like ordinary History transactions. A user could see the cleanup queue, but not
which merchant groups were large enough to prioritize before selecting a
representative row.

## Decision

Show the visible same-merchant group size in each History row subtitle while the
`Repeated backlog` category-state filter is active.

- The count is computed from the currently visible filtered result set.
- Groups reuse the H-10 key: normalized merchant plus expense/income kind.
- Counts are only shown for repeated groups with more than one visible row.
- Other category filters keep the existing subtitle text unchanged.
- Selection, editing, and exact rule learning behavior are unchanged.

## Acceptance

- Repeated-backlog rows show a localized same-merchant count in the subtitle.
- The count stays scoped to the visible filtered result set.
- Expense and income rows with the same merchant remain separate groups.
- `All`, `Uncategorized`, and `Categorized` rows do not show the repeated count.
- Singletons, transfers, categorized rows, and generic merchants do not receive a
  repeated count.

## Non-goals

- Do not add a new sort order.
- Do not add a new database query, schema field, or persisted counter.
- Do not train rules from viewing the count; rule learning still requires an
  explicit compatible category assignment.

## Validation

- 2026-06-15: Focused `HistoryFilterTest` and `HistoryViewModelTest` passed with
  JDK 17, `--no-daemon`, and `--max-workers=1`.
- 2026-06-15: Full `:feature:today:testDebugUnitTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-15: Full JVM/debug-APK/lint stack passed with JDK 17:
  `test`, `:app:assemblePersonalFullSmsDebug`, `:app:assembleStoreSafeDebug`,
  `:app:lintPersonalFullSmsDebug`, and `:app:lintStoreSafeDebug`.
- 2026-06-15: `git diff --check` passed.
- 2026-06-15: Runtime `personalFullSmsDebug` install was attempted with the
  built APK. The local AVD exists and ADB starts, but `emulator -accel-check`
  reports the Android Emulator hypervisor driver is not installed, and the
  bounded hidden `AtharPixelQaApi35` software launch stayed
  `emulator-5554 offline` without an online ADB device or `boot_completed=1`.
  Install, screenshot, UI dump, and logcat capture could not run. Evidence was
  written to `build/qa/history-repeated-backlog-counts-emulator/`; final cleanup
  left no emulator/qemu/adb/netsim process and no AVD lock files.
