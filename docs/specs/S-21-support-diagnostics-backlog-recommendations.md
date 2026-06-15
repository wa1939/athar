# S-21 - Support Diagnostics Backlog Recommendations

## Problem

Support diagnostics already separate parser health from categorization backlog, but
the exported JSON still requires a maintainer to mentally translate coverage
metrics into the next user action. When the parser is healthy and category
backlog remains high, Athar should make the recommended cleanup path explicit
without exposing raw merchants, amounts, SMS bodies, notes, or transaction rows.

## Decision

Add a redacted `category_backlog_recommendation` block to
`athar-support-diagnostics.json`.

The recommendation is derived only from aggregate category-coverage counts and
hashed repeated-merchant group sizes:

- `none` when there is no category backlog.
- `history_repeated_backlog` when at least one repeated uncategorized merchant
  group has enough rows to justify History repeated-backlog cleanup.
- `bulk_categorize_export` when the total category backlog is large enough that
  the Settings bulk-categorize CSV workflow is more efficient than row-by-row
  cleanup.
- `manual_cleanup` when the remaining backlog is small.

The block also includes stable reason codes, so support can understand why an
action appears without needing private data.

## Acceptance

- Support diagnostics serialize `category_backlog_recommendation`.
- The recommendation uses only aggregate counts and hashed group sizes.
- Existing privacy assertions still prove raw sender, body, merchant, amount,
  notes, and transaction row text are omitted.
- Tests cover no backlog, repeated-group backlog, large long-tail backlog, and
  small manual-cleanup backlog.
- Documentation explains how support should interpret the new block.

## Non-goals

- Do not auto-run cleanup or import anything.
- Do not expose raw merchant names or sample rows.
- Do not change the Settings UI copy in this slice.
- Do not weaken the local-only privacy model.
