# H-07 - History Select Visible Cleanup

## Problem

H-06 made History bulk category cleanup possible, but long filtered backlogs still
required tapping each visible row before applying a category. That is unnecessary
friction when the user has already narrowed History to a focused set, such as
uncategorized SMS expenses for one merchant search.

## Decision

Add a `Select shown` action to History selection mode. It selects every row in
the currently filtered History result set, then reuses the existing type-safe
bulk category flow:

- Expense rows still receive only expense categories.
- Income rows still receive only income categories.
- Transfers remain selectable but are skipped by category assignment.
- Mixed expense/income result sets still cannot apply one category.

The action is disabled when all currently shown rows are already selected.

## Acceptance

- History selection mode can select the full current filtered result set in one
  action.
- Changing any filter or search query still clears selection, so the action does
  not create hidden cross-filter edits.
- Existing tap-to-select, clear, and category assignment behavior remains
  unchanged.

## Non-goals

- Do not add cross-filter saved selections.
- Do not train category rules from History bulk assignment.
- Do not promote private/local merchant labels into public seed rules.

## Validation

- 2026-06-14: Focused `HistoryFilterTest` and `HistoryViewModelTest` passed with
  JDK 17, `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: Full `:feature:today:testDebugUnitTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: `git diff --check` passed.
- 2026-06-14: Full JVM/debug-APK/lint stack passed with JDK 17:
  `test`, `:app:assemblePersonalFullSmsDebug`, `:app:assembleStoreSafeDebug`,
  `:app:lintPersonalFullSmsDebug`, and `:app:lintStoreSafeDebug`.
- 2026-06-14: Runtime `personalFullSmsDebug` install was attempted with the
  built APK. The local AVD exists, but `emulator -accel-check` reports the
  Android Emulator hypervisor driver is not installed, and a bounded hidden
  `AtharPixelQaApi35` software launch stayed `emulator-5554 offline` through
  the wait. Install, screenshot, UI dump, and logcat capture could not run.
  Evidence was written to
  `build/qa/history-select-visible-cleanup-emulator/`; cleanup stopped the
  orphaned qemu process and finished with no emulator/qemu/adb/netsim process
  and no AVD lock files.
