# H-13 - History Repeated Backlog Top Group

## Problem

H-12 puts the largest repeated-backlog merchant groups first, but the user still
needed to tap a representative row and then tap `Same merchant` before applying
a category. The highest-impact group should be selectable directly from the bulk
selection controls.

## Decision

Add a `Top group` bulk-selection chip while History is in `Repeated backlog`
mode and the visible result contains an actionable repeated merchant group.

- The chip selects the largest visible same-merchant group in one action.
- The group is computed after status, type, source, search, and repeated-backlog
  filters.
- Grouping reuses the H-10/H-12 key: normalized merchant plus expense/income
  kind.
- Equal-size groups use the first visible group, matching H-12 priority order.
- Selecting the top group replaces the current selection, so accidental mixed
  selections do not carry into the category picker.
- Existing `Select shown`, `Same merchant`, category assignment, and exact-rule
  learning behavior remains unchanged.

## Acceptance

- The bulk card shows a localized `Top group (n)` action only when a top
  repeated-backlog group exists.
- Tapping it selects every row in the current top group and enters selection
  mode.
- The action disables once exactly that top group is selected.
- Other category filters do not expose a top repeated group.
- The selected rows remain eligible for the existing type-safe category picker
  and exact local rule learning.

## Non-goals

- Do not add a new persistent grouping model or database query.
- Do not change row sorting beyond H-12.
- Do not auto-apply a category or learn a rule without explicit user
  confirmation.

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
  written to `build/qa/history-repeated-backlog-top-group-emulator/`; final
  cleanup left no emulator/qemu/adb/netsim process and no AVD lock files.
