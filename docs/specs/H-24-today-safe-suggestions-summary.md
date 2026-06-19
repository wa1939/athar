# H-24 Today Safe Suggestions Summary

## Status

Shipped in `dev/today-safe-suggestions-summary`.

## Problem

H-23 lets Today apply all safe pending category suggestions in one tap, but the
button only showed a total row count. That is fast, but not very transparent
when the pending tray contains several categories. Users should know the impact
before applying the action.

## Behavior

- Today derives a read-only impact summary from the same H-22 suggestion map.
- The summary groups safe pending rows by suggested category and sorts by row
  count descending, then category id for deterministic ties.
- The bulk-safe action shows the first few category/count pairs under the
  button label, with a compact overflow count if more categories exist.
- Rows remain eligible only through the existing H-22/H-23 safety contract:
  same specific normalized merchant, same expense/income kind, exactly one
  active compatible category in confirmed local history, and revalidation
  before write.
- The summary does not broaden matching, auto-confirm rows, or create learned
  rules.

## Validation

- Focused tests cover grouped safe-suggestion summary counts and deterministic
  sorting.
- The H-23 bulk-apply test continues to prove only currently safe rows are
  written and no broad rules are learned.
- Passed on 2026-06-15 with JDK 17:
  - `:feature:today:testDebugUnitTest --tests "com.athar.feature.today.TodayViewModelTest"`.
  - forced gated private SMS audit with the local export file,
  - `git diff --check`,
  - full `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime install/screenshot/UI-dump/logcat capture still needs an online
  Android device or working emulator; the local `AtharPixelQaApi35` AVD exited
  before ADB came online because the Android Emulator hypervisor driver is not
  installed. Evidence was written to
  `build/qa/today-safe-suggestions-summary-emulator/`.
