# H-02 History Source Filter

## Goal

Make the all-transactions history useful for cleanup after a backfill, import, or notification run. Users should be able to isolate transactions by where they came from: SMS, bank-app notification, manual entry, import, recurring rule, or share sheet.

## Inputs

- Existing `Transaction.source` values.
- Existing History status and type filters.
- Existing merchant/notes search query.

## Behavior

History keeps the current status and type filters, then adds a third source-chip row. Selecting a source narrows the visible transaction list without changing the underlying data. Each transaction row subtitle includes date, type marker, status, and source so the user can quickly tell whether a row came from SMS, import, recurring materialization, or manual entry.

The text search also checks `merchantNormalized` and `sourceRefId`, which helps find imported rows (`csv-row-42`) and SMS-backed rows by their reference id when debugging.

## Acceptance

- Source filter composes with status and type filters.
- "All sources" preserves existing behavior.
- Row subtitles expose the transaction source.
- Filter logic is covered by unit tests.
