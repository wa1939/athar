# H-06 - History Bulk Category Cleanup

## Problem

After SMS backfill, notification ingestion, and statement imports, users can end
up with many reachable but uncategorized rows in History. H-04 made those rows
easy to filter, and the bulk CSV workflow helps large AI-assisted cleanup. A
smaller in-app gap remained: when several visible rows clearly belong to the
same category, the user still had to open each transaction one by one.

That slows down the exact review loop Athar is designed to make calm.

## Decision

Add a History selection mode for visible rows:

- The user can enter selection mode from History and tap rows to select them.
- A compact bulk card shows selected count, whether the selection is compatible,
  and how many transfers would be skipped.
- A category picker sheet lists only active categories compatible with the
  selected non-transfer transaction type.
- Applying a category updates selected compatible expense or income rows,
  confirms them, and leaves transfers or incompatible rows unchanged.

This deliberately does not train shared or substring category rules. Bulk History
cleanup is for fixing the selected rows in front of the user. Rule growth remains
with the explicit "Always categorize..." and bulk CSV exact-local-rule flows.

## Acceptance

- History exposes selection mode and visible selected-row state.
- Category assignment is type-safe: expense rows can only receive expense
  categories, income rows can only receive income categories, and transfers are
  skipped.
- Mixed expense/income selections cannot open the category-apply path.
- Applying a category confirms updated rows and reports applied/skipped counts.
- Existing History filters, search, row editing, and delete behavior continue to
  work.

## Non-goals

- Do not add rule learning from History bulk assignment.
- Do not add cross-filter hidden selection.
- Do not add a full spreadsheet-style multi-edit grid.

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
  `AtharPixelQaApi35` software launch did not expose a boot-complete online ADB
  device. Install, screenshot, UI dump, and logcat capture could not run.
  Evidence was written to
  `build/qa/history-bulk-category-cleanup-emulator/`; cleanup finished with no
  emulator/qemu/adb/netsim process and no AVD lock files.
