# H-08 - History Select Same Merchant

## Problem

H-07 made it possible to select the currently shown History result set, but a
large cleanup backlog can still contain repeated merchants that are mixed with
other visible rows. When the user sees one representative uncategorized merchant,
they need a faster way to select its visible peers without typing a search or
tapping each repeated row.

## Decision

Add a `Same merchant` action to History selection mode. After the user selects
one or more visible rows, the action expands the selection to all currently
visible rows whose normalized merchant key matches the selected rows.

The action stays inside the existing bulk category flow:

- Matching is scoped to the current filtered History result set.
- Search or filter changes still clear selection.
- Expense rows still receive only expense categories.
- Income rows still receive only income categories.
- Transfers remain selectable but are skipped by category assignment.
- Mixed expense/income selections still cannot apply one category.

The action is disabled when there are no additional visible rows matching the
selected merchant keys.

## Acceptance

- History selection mode can expand a representative selected row to visible rows
  with the same normalized merchant.
- The selection expansion never reaches outside the current visible result set.
- Existing tap-to-select, select-shown, clear, and category assignment behavior
  remains unchanged.

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
  `AtharPixelQaApi35` software launch did not reach an online ADB device or
  `boot_completed=1`. Install, screenshot, UI dump, and logcat capture could not
  run. Evidence was written to
  `build/qa/history-select-same-merchant-emulator/`; cleanup finished with no
  emulator/qemu/adb/netsim process and no AVD lock files.
