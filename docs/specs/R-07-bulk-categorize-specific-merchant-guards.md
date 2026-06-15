# R-07 - Bulk Categorize Specific-Merchant Guards

## Problem

The bulk-categorize CSV is designed to reduce manual cleanup by grouping repeated
merchants and letting one category choice apply to blank peers in the same group.
That is safe for specific merchants such as `Hemmah`, but risky for generic
merchant labels such as `payment`, `purchase`, `cash`, `merchant`, or `bank`.

If a generic label repeats across unrelated transactions, an external AI or fast
human edit could fill one representative row, then accidentally propagate that
category to unrelated rows and train an exact local rule that is too broad for
future ingestion.

## Decision

Keep explicit row updates unchanged, but make repeated-group behavior require a
specific merchant key.

- Export repeated-group counts/ranks only for specific merchant keys.
- Treat generic or blank merchant keys as singleton export groups, even when the
  raw normalized value repeats.
- During import, update explicit generic rows when their `category_id` is valid
  and type-compatible.
- Do not propagate a generic row's category to blank peers.
- Do not train exact local rules from generic merchant keys.

## Acceptance

- Specific repeated merchants still export together with shared group count/rank.
- Generic repeated merchants export as singletons.
- Importing a filled generic row updates only that explicit row.
- Blank peers for the same generic merchant stay untouched.
- Generic merchant imports add no exact learned rule.
- Existing type-safety, conflict, stable-key, and category-option tests keep
  passing.

## Non-goals

- Do not remove generic rows from the CSV; they may still be manually classified.
- Do not add inline AI/API-key categorization.
- Do not change seed rules or public category labels.
- Do not change History repeated-backlog behavior beyond sharing the same
  specific-merchant guard used by bulk categorization, Today suggestions, and
  local auto-learning.
