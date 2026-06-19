# H-22 Today Pending Category Suggestions

## Status

Shipped in `dev/today-pending-safe-suggestions`.

## Problem

History can already clean repeated uncategorized merchants in bulk, but daily SMS review still made users open the edit sheet for a pending row even when the same merchant had one clear category in confirmed local history.

## Behavior

- Today computes pending-row suggestions from confirmed local transaction history only.
- A suggestion appears only when:
  - the pending row is still uncategorized and pending,
  - the normalized merchant is specific, not generic,
  - the pending row and history share the same expense/income kind,
  - matching confirmed history has exactly one active category.
- Suggestions are hidden for conflicting history, archived/inactive categories, generic merchants, transfers, and already categorized pending rows.
- Tapping **Apply** confirms only that pending row with the suggested category.
- A single suggestion tap does not create a learned rule. Broad future categorization remains behind explicit "Always categorize..." and existing local exact-rule learning.

## Validation

- Focused tests cover:
  - exposing an unambiguous same-merchant suggestion,
  - hiding conflicting, generic, and inactive-category history,
  - applying one pending-row suggestion without learning a rule.
- Passed on 2026-06-15 with JDK 17:
  - `:feature:today:testDebugUnitTest --tests "com.athar.feature.today.TodayViewModelTest"`
  - forced gated private SMS audit with the local export file,
  - `git diff --check`,
  - full `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime install/screenshot/UI-dump/logcat capture still needs an online Android device or working emulator; the local AVD did not expose ADB because the Android Emulator hypervisor driver is not installed.
