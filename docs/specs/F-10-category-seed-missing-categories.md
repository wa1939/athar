# F-10 - Category Seed Missing Categories

## Problem

`CategorySeed` originally seeded `seed_categories.json` only when the category
table was empty. That is safe for first install, but it blocks future built-in
category additions from reaching existing users. As the roadmap adds more
notification labels, import workflows, and global category coverage, existing
installs need missing bundled categories without losing local customization.

## Decision

On app data initialization, load the bundled category asset every time:

- if the category table is empty, insert the full bundled list;
- if categories already exist, insert only bundled category IDs that are
  missing locally;
- append missing bundled categories after the highest existing `sortOrder`;
- never overwrite existing category rows.

This preserves user-edited names, Arabic labels, archive state, monthly targets,
currency, custom categories, and manual ordering. It also keeps private/custom
categories local; bundled additions are additive only.

The Android library convention now enables JUnit Platform for `Test` tasks so
JUnit 5 tests in Android library modules actually run. The newly active suite
also fixed narrow pre-existing failures in statement CSV header normalization,
XLSX XML padding, support-diagnostics decimal redaction, and the side-income
seed guard.

## Acceptance

- Fresh installs still seed the full bundled category list.
- Existing installs receive only missing bundled category IDs.
- Existing category rows are not overwritten.
- Missing bundled categories are appended after the current max sort order.
- Android library JUnit 5 tests run under the shared convention plugin.
- Full JVM tests, debug APK builds, and debug lint variants pass.

## Non-goals

- Do not rename, unarchive, reorder, or retarget existing user categories.
- Do not infer category migrations for old transaction rows.
- Do not add new public seed rules or new bundled categories in this slice.
- Do not change local custom category behavior.

## Validation

- 2026-06-14: focused core-data tests passed with JDK 21:
  `:core:data:testDebugUnitTest --tests CategorySeedPlannerTest --tests
  StatementCsvMapperTest --tests CsvImporterTest --tests SeedRulesAssetTest
  --tests SupportDiagnosticsExporterTest`.
- 2026-06-14: full JVM suite passed with JDK 21: `test`.
- 2026-06-14: debug APK build and lint stack passed with JDK 21:
  `:app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- 2026-06-14: runtime `personalFullSmsDebug` install was attempted. The built
  APK was ready, but `emulator -accel-check` reported that the Android Emulator
  hypervisor driver is not installed, and a bounded hidden `AtharPixelQaApi35`
  launch stayed `emulator-5554 offline` for 120 seconds. Install, launch,
  screenshot, UI dump, and logcat capture could not run. Evidence was written
  under `build/qa/category-seed-missing-categories-emulator/`, and cleanup left
  no attached ADB device or emulator/qemu/netsim process.
