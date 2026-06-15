# W-10 - Wishlist Reconciliation-Safe Capacity

## Problem

Wishlist capacity is supposed to answer a TMOAP-style planning question:
"How much real monthly surplus can I safely direct toward future wishes?"

Athar already excludes account reconciliation adjustments from Today, Trends,
Plan budget actuals, widgets, quick-add suggestions, local learning, and tax
exports because a balance correction is not real income or spending. Wishlist
capacity still summed every confirmed income and expense transaction in the
last-three-month window, so a manual reconciliation gap could make the Wishlist
tab think the user had no capacity or unrealistic capacity.

## Decision

Move the transaction-backed capacity calculation into `WishlistCalc` and filter
out `Transaction.isReconciliation()` before summing income and expense rows.
Transfers still do not affect capacity. The ViewModel keeps the same
three-month confirmed transaction window and display-currency projection, but
the math now uses only operating transactions.

## Acceptance

- Reconciliation adjustment income/expense rows do not change Wishlist monthly
  capacity.
- Normal confirmed income and expense rows still drive capacity.
- Transfers still do not change capacity.
- Existing per-item projection, summary, add/edit/delete, and import behavior
  stay unchanged.

## Non-goals

- Do not change the Wishlist schema or repository contract.
- Do not change the three-month lookback window.
- Do not hide reconciliation rows from History or account ledgers.

## Validation

- Focused domain test passed:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :core:domain:test`.
- Plan feature compilation passed:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :feature:plan:compileDebugKotlin`.
- `git diff --check` passed.
- Full validation passed:
  `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime install/launch was attempted with the Android emulator QA workflow. The
  built `personalFullSmsDebug` APK was ready, but no online ADB target appeared:
  `emulator -accel-check` reported the Android Emulator hypervisor driver is not
  installed, `AtharPixelQaApi35` stayed without an ADB transport during the
  bounded launch and opened emulator crash-consent logging, and
  `AtharPixelQaApi35Arm` exited because arm64 system images are unsupported on
  this x86_64 host. Evidence is in
  `build/qa/wishlist-reconciliation-capacity-emulator/`.
