# G-12 - TMOAP Family Investments Import

## Problem

Athar already has a Plan -> Investments tab for the TMOAP family-investment
pool, but public users still had to recreate the pool and contributors by hand.
After statement, Budget Targets, and Wishlist imports, this was the remaining
named TMOAP workbook migration gap.

## Decision

Add a separate Settings card for importing the TMOAP `استثمارات العائلة`
(`Family Investments`) worksheet. This stays outside statement import so
selecting a transaction workbook never silently changes Plan data.

The importer reuses the dependency-light OpenXML reader. It finds the sheet by
name or by an amount/owner header pair, then reads the first raw contribution
table:

- amount from `المبلغ` / `Amount`
- owner from `المالك` / `Owner`
- total return from `العائد من الاستثمار` / `Return`
- period from `الفترة` / `Period`

The parser stops at the first blank gap after contribution rows, so the lower
TMOAP summary table is not imported as duplicate contributors. If both the
first return row and a later total-return row contain the same value, Athar uses
the first non-total return value and does not double-count it.

Rows preview before write. Invalid contribution amounts or blank owners are
skipped and surfaced in preview. If the workbook matches an existing family
investment pool, confirmation updates the pool return/period and replaces that
pool's contributor rows so re-importing a workbook does not append duplicates.

## Acceptance

- Settings exposes a distinct "Family investments import" card.
- Selecting a TMOAP `.xlsx` workbook previews the investment pool before any
  investment row is inserted, updated, or deleted.
- Preview shows pool name, period, contribution count, skipped count, corpus,
  total return, sample rows, first skipped row, and replacement count when an
  existing pool matches.
- Confirmation creates a new pool when none exists.
- Confirmation updates a matched family-investment pool and replaces its old
  contributor rows instead of appending duplicates.
- The lower TMOAP summary table is ignored.
- Statement import remains transaction-only and does not mutate investment data
  as a side effect.

## Validation

- Focused tests:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :core:data:testDebugUnitTest --tests "com.athar.core.data.csv.InvestmentImporterTest" --tests "com.athar.core.data.csv.WishlistImporterTest" :feature:settings:testDebugUnitTest --tests "com.athar.feature.settings.SettingsViewModelTest"`
- Full Gradle test/build/lint stack before push:
  `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime Settings tap-through remains under QA-01 and depends on an online
  Android device or working emulator.

2026-06-14 validation on `dev/tmoap-investments-import`:

- Focused Investment/Wishlist/Settings tests passed.
- `git diff --check` passed.
- Full `test`, `:app:assemblePersonalFullSmsDebug`,
  `:app:assembleStoreSafeDebug`, `:app:lintPersonalFullSmsDebug`, and
  `:app:lintStoreSafeDebug` passed with JDK 21.
- Runtime install/tap-through could not complete because
  `AtharPixelQaApi35` exposed only `emulator-5554 offline` under bounded
  software emulation after `emulator -accel-check` reported that the Android
  Emulator hypervisor driver is not installed. Evidence is under
  `build/qa/tmoap-investments-import-emulator/`; cleanup stopped qemu/netsim,
  removed stale AVD locks, and left no attached ADB device, no qemu/emulator/
  netsim/adb process, and no AVD lock files.
