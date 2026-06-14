# G-12 - TMOAP Budget Target Import

## Problem

Athar can import TMOAP transaction sheets directly from `.xlsx`, and the bundled
category catalog now matches the concrete TMOAP category setup. A remaining
migration gap is monthly budget targets: users still have to re-enter the
Budget Targets sheet by hand in Plan.

## Decision

Add a separate Settings card for importing TMOAP budget targets. This flow is
explicitly separate from statement exchange so selecting a workbook for
transactions never silently changes category targets.

The importer reads the OpenXML `Budget Targets` worksheet without adding Apache
POI. It treats column B as the category label and column G as the manually
entered monthly target, with separate expense and income sections. Blank target
cells are ignored. Rows with target values are matched against active Athar
categories using the same TMOAP-aware category resolver used by statement
imports, including aliases such as `Public transportation`, `Wife`, `Job`, and
income `Other`.

The Settings flow previews target count, changed count, skipped count, monthly
expense and income target totals, sample matched rows, and the first skipped
row. Confirmation then updates only matched active categories.

## Acceptance

- Settings exposes a distinct "Budget targets" import card.
- Selecting a TMOAP `.xlsx` workbook previews targets before any category is
  updated.
- Expense targets from the Budget Targets sheet resolve to expense categories.
- Income targets resolve to income categories, including income `Other`.
- Blank target cells do not clear existing Athar targets.
- Unknown labels or invalid target amounts are skipped and surfaced in preview.
- Statement import remains transaction-only and does not mutate category
  targets as a side effect.

## Validation

- Focused tests:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :core:data:testDebugUnitTest --tests "com.athar.core.data.csv.BudgetTargetImporterTest" --tests "com.athar.core.data.csv.CsvImporterTest" :feature:settings:testDebugUnitTest --tests "com.athar.feature.settings.SettingsViewModelTest"`
- Full Gradle test/build/lint stack before push:
  `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime Settings tap-through remains under QA-01 and depends on an online
  Android device or working emulator.

2026-06-14 validation on `dev/tmoap-budget-target-import`:

- Focused budget-target/XLSX/Settings tests passed.
- `git diff --check` passed.
- Full `test`, `:app:assemblePersonalFullSmsDebug`,
  `:app:assembleStoreSafeDebug`, `:app:lintPersonalFullSmsDebug`, and
  `:app:lintStoreSafeDebug` passed with JDK 21.
- Runtime install/tap-through could not complete because
  `AtharPixelQaApi35` stayed offline under software emulation after
  `emulator -accel-check` reported that the Android Emulator hypervisor driver
  is not installed. Evidence is under
  `build/qa/tmoap-budget-target-import-emulator/`.
