# G-07 — Bill Reminders

## Problem

The Bills tab shows upcoming recurring expenses, but a user still has to open the app to notice
that rent, subscriptions, utilities, or card payments are approaching. Athar should remove that
planning burden without becoming noisy or judgmental.

## Decision

Add opt-in bill reminders on top of the existing recurring-rule worker:

- The Bills tab shows a reminders card with clear permission copy.
- Reminders are off by default. Android notification permission alone is not treated as consent.
- When enabled, the recurring worker sends quiet reminders:
  - 2 days before an active recurring expense.
  - On the due date.
  - Once if a recurring expense transaction remains pending after its due date.
- Reminders are expense-only. Income rules such as salary do not create bill alerts.
- Sent reminder keys are stored in DataStore and pruned to the currently relevant reminder window,
  preventing duplicate daily notifications without becoming an unbounded event log.

This keeps Athar aligned with the product rule: no guilt-trip notifications, no streaks, no shaming.

## Acceptance Criteria

- Bills tab has an opt-in reminders card.
- On Android 13+, enabling reminders requests `POST_NOTIFICATIONS` only when permission is missing.
- On Android 12 and below, enabling reminders only flips the Athar preference because runtime
  notification permission does not exist.
- Worker sends reminders only when the Athar-level bill-reminders preference is enabled.
- Active recurring expense rules generate due-today and 2-days-before reminder candidates.
- Inactive rules and income rules do not generate bill reminders.
- Overdue pending recurring expenses generate one missed-review reminder until the item is resolved.
- The notification channel and notification copy are localized in Arabic and English.

## Non-Goals

- No exact-alarm scheduling. The daily WorkManager cadence is sufficient for bill reminders.
- No notification actions. Opening Athar preserves the existing pending-review and edit flows.
- No reminders for arbitrary uncategorized SMS/notification pending rows.

## Validation

- `:core:domain:test`
- `:feature:plan:compileDebugKotlin`
- `:app:compilePersonalFullSmsDebugKotlin`
- Full static stack before branch handoff:
  `--max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`

Runtime notification tap-through still depends on QA-01: connect a physical Android device or enable
hardware virtualization so the AVD can boot.
