# R-07 — Stable bulk-categorize CSV import

## Goal

Make the external-AI bulk categorization roundtrip resilient when a user exports uncategorized rows, then rescans/backfills SMS before importing the filled CSV. The import should not rely only on Room transaction UUIDs.

## Requirements

- Export a deterministic `stable_key` for every row.
- Export `source_ref_id` when available so SMS/notification rows can be matched after re-ingestion.
- Import should match by current `id` first for exactness.
- If `id` no longer exists, import should match by exported `stable_key`.
- If an older CSV lacks `stable_key`, import should fall back to a content fingerprint based on merchant, amount, currency, type, and date.
- Quoted multiline `raw_body` fields must parse correctly.
- The AI should still edit only `category_id`; all matching columns must be treated as read-only context.

## Non-goals

- No cloud categorization API.
- No persistent "export session" state.
- No schema migration.
