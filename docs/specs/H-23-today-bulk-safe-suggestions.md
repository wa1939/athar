# H-23 Today Bulk Safe Suggestions

## Status

Shipped in `dev/today-bulk-safe-suggestions`.

## Problem

H-22 removed edit-sheet friction for one pending SMS row at a time, but backfills and busy days can still create several safe same-merchant suggestions. Clearing those one by one is unnecessary manual work.

## Behavior

- Today shows **Apply safe suggestions (N)** when the pending tray has one or more H-22-safe category suggestions.
- The bulk action reuses the same suggestion map but revalidates every row before writing:
  - transaction still exists,
  - transaction is still pending,
  - transaction still has no category,
  - suggested category is still active,
  - transaction type and category kind still match.
- Each valid row is confirmed with the suggested category.
- Rows without a current valid suggestion are left untouched.
- Bulk safe apply does not create learned rules. Broad future categorization still requires explicit "Always categorize..." or the existing repeated-history local exact-rule learner.
- A localized feedback card reports how many rows were applied.

## Validation

- Focused tests cover:
  - bulk-applying only safe pending suggestions while leaving conflicting merchants untouched,
  - emitting and clearing the applied-count feedback event,
  - avoiding learned-rule creation.
- Passed on 2026-06-15 with JDK 17:
  - `:feature:today:testDebugUnitTest --tests "com.athar.feature.today.TodayViewModelTest"`.
  - forced gated private SMS audit with the local export file,
  - `git diff --check`,
  - full `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime install/screenshot/UI-dump/logcat capture still needs an online Android device or working emulator; the local `AtharPixelQaApi35` AVD exited before ADB came online because the Android Emulator hypervisor driver is not installed.
