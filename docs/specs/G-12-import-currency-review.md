# G-12 - Import Currency Review

## Problem

Statement imports can contain more than one currency, especially for users outside the original
Saudi SMS-first path. The preview showed row samples with currencies, but it did not summarize the
currency mix before confirmation. A user could miss that a statement contained USD and SAR rows, or
that an excluded/duplicate row changed the actual totals about to be imported.

## Decision

Add `CsvImportCurrencySummary` to the import preview. The importer computes summaries from the
same importable transaction plan used for confirmation, grouped by currency and split into expense,
income, and transfer totals.

The Settings statement exchange card now shows a compact "currency review before import" section
above the sample rows. Summaries count only rows that will actually import; parse failures,
duplicates, and excluded rows stay out of the totals.

## Acceptance

- Preview exposes per-currency row counts.
- Preview exposes expense, income, and transfer totals per currency.
- Excluded rows do not contribute to currency summaries.
- Duplicate and parse-error skipped rows do not contribute to currency summaries.
- CSV, OFX/QFX, and MT940 share the same preview summary path.

## Validation

- 2026-06-13: `:core:data:testDebugUnitTest :feature:settings:testDebugUnitTest` passed
  with JDK 17, `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: Full JVM test/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- 2026-06-13: Runtime Settings import tap-through could not complete. The only installed AVD,
  `AtharPixelQaApi35`, exited during launch with `x86_64 emulation currently requires hardware
  acceleration` because the Android Emulator hypervisor driver is not installed; `adb devices -l`
  still returned no attached devices.

## Non-goals

- No FX conversion.
- No field-level row editing.
- No per-row account assignment.
