# G-12 - TMOAP Wishlist Import

## Problem

Athar already models the TMOAP Wishlist math in Plan, and statement import can
bring transaction history across from TMOAP-style workbooks. Budget Targets also
have their own explicit Settings import flow. The remaining setup gap was
Wishlist rows: users still had to recreate item names, cost, current saved,
desired horizon, and start month manually.

## Decision

Add a separate Settings card for importing the TMOAP `Wishlist` worksheet. This
stays outside statement import so selecting a workbook for transactions never
silently changes Plan data.

The importer reuses the dependency-light OpenXML reader. It finds the Wishlist
header row by `Item` and `Cost`, reads:

- `Item`
- `Cost`
- `Current Saved`
- `Desired Months (optional)`
- `Start Month`
- optional `Notes` if a customized workbook includes it

Rows preview before write. Invalid costs, negative saved amounts, non-whole
desired-month values, and unreadable start months are skipped and surfaced in
the preview. Existing wishes are matched by normalized item name and updated in
place, preserving their IDs and notes unless the workbook supplies notes.
Unmatched rows become new wishlist items. Excel serial start months and
`YYYY-MM` / `YYYY-MM-DD` text start months are accepted; blank start months use
the current device month.

## Acceptance

- Settings exposes a distinct "Wishlist import" card.
- Selecting a TMOAP `.xlsx` workbook previews wishlist item rows before any
  wishlist item is inserted or updated.
- Preview shows new/update/skipped counts, total cost, total saved, sample rows,
  and the first skipped row.
- Confirmation upserts only parsed item rows.
- Re-importing the same workbook updates existing wishes by name instead of
  duplicating them.
- Statement import remains transaction-only and does not mutate Wishlist data as
  a side effect.

## Validation

- Focused tests:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :core:data:testDebugUnitTest --tests "com.athar.core.data.csv.WishlistImporterTest" --tests "com.athar.core.data.csv.BudgetTargetImporterTest" :feature:settings:testDebugUnitTest --tests "com.athar.feature.settings.SettingsViewModelTest"`
- Full Gradle test/build/lint stack before push:
  `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime Settings tap-through remains under QA-01 and depends on an online
  Android device or working emulator.

2026-06-14 validation on `dev/tmoap-wishlist-import`:

- Focused Wishlist/Budget Target/Settings tests passed.
- `git diff --check` passed.
- Full `test`, `:app:assemblePersonalFullSmsDebug`,
  `:app:assembleStoreSafeDebug`, `:app:lintPersonalFullSmsDebug`, and
  `:app:lintStoreSafeDebug` passed with JDK 21.
- Runtime install/tap-through could not complete because
  `AtharPixelQaApi35` exposed only `emulator-5554 offline` under bounded
  software emulation after `emulator -accel-check` reported that the Android
  Emulator hypervisor driver is not installed. Evidence is under
  `build/qa/tmoap-wishlist-import-emulator/`; cleanup stopped qemu/netsim,
  removed stale AVD locks, and left no attached ADB device, no qemu/emulator/
  netsim/adb process, and no AVD lock files.
