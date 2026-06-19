# H-04 History Category State Filter

## Goal

Make History a faster cleanup surface after SMS backfills, notification ingestion, and statement imports. Users should be able to isolate rows that still need a category without mixing them with already-reviewed transactions.

## Inputs

- Existing `Transaction.categoryId`.
- Existing History filters for status, type, source, and search.
- Existing edit sheet and "Always categorize..." learning flow.

## Behavior

History adds a fourth filter row:

- All categories
- Uncategorized
- Categorized

The filter composes with status, type, source, and search. `null`, empty, and blank category IDs count as uncategorized. Categorized rows require a non-blank category ID.

This does not change transaction data or category assignment. It only narrows the visible list so users can quickly find rows that need manual review, bulk-AI export/import follow-up, or one-tap "Always categorize..." learning.

## Acceptance

- History exposes a category-state chip row in Arabic and English.
- "All categories" preserves existing History behavior.
- "Uncategorized" returns only rows whose `categoryId` is null, empty, or blank.
- "Categorized" returns only rows with a non-blank `categoryId`.
- Category-state filtering composes with status, type, source, and search.
- Unit tests cover uncategorized, categorized, and all-category behavior.

## Validation

- `:feature:today:testDebugUnitTest`
- Full JVM/build/lint gate: `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime install/launch remains blocked on this PC: no ADB device is attached, and `AtharPixelQaApi35` exits because x86_64 emulator images require hardware acceleration.
