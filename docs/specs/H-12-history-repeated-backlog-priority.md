# H-12 - History Repeated Backlog Priority

## Problem

H-10 finds repeated uncategorized merchant groups and H-11 shows their visible
group sizes, but the user still had to scan the list to find the largest cleanup
opportunities. When a backlog has many repeated merchants, the highest-impact
groups should be first so one category decision can remove the most manual work.

## Decision

Sort the `Repeated backlog` History result by visible same-merchant group size,
largest first.

- The ordering applies only when the `Repeated backlog` category-state filter is
  active.
- Group size is computed after status, type, source, and search filters.
- Groups reuse the H-10/H-11 key: normalized merchant plus expense/income kind.
- Rows inside the same group keep their existing relative order.
- Equal-size groups keep the order of the first visible row from each group.
- Other History category filters keep their existing ordering.

## Acceptance

- Repeated-backlog groups with more visible rows appear before smaller repeated
  groups.
- Rows in a merchant group remain adjacent and stable.
- Equal-size groups remain deterministic by first visible occurrence.
- `All`, `Uncategorized`, and `Categorized` filters do not inherit priority
  ordering.
- Existing repeated-backlog membership rules remain unchanged: categorized rows,
  transfers, singletons, generic merchants, and mixed expense/income-only pairs
  are still excluded from actionable repeated groups.

## Non-goals

- Do not add a new sort toggle or user preference.
- Do not add a database query, schema field, or persisted counter.
- Do not change selection, category assignment, or exact-rule learning behavior.

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
  bounded hidden `AtharPixelQaApi35` software launch exited before exposing an
  online ADB device or `boot_completed=1` with exit code `-1073741819`.
  Install, screenshot, UI dump, and logcat capture could not run. Evidence was
  written to `build/qa/history-repeated-backlog-priority-emulator/`; final
  cleanup left no emulator/qemu/adb/netsim process and no AVD lock files.
