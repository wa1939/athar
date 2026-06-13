# G-07 — Bills Calendar

## Problem

Recurring rules already create pending transactions when due, but the user still has to remember
what is coming next. TMOAP users plan around rent, salary, subscriptions, utilities, and card
payments; Athar should collapse that mental calendar into one glance.

## Decision

Add a `Bills` section under Plan for the first G-7 slice:

- Project active recurring rules across the next 60 days.
- Include one overdue occurrence when a rule's `nextRunDate` is before today, so missed
  materialization is visible instead of silent.
- Show uncategorized pending transactions in the same list because they often represent bills that
  have arrived but still need review.
- Add a compact 14-day calendar strip with dots on due days.
- Show a due-now plus next-30-days outgoing total for recurring expense rules.

No notifications are added in this slice. Push reminders remain the G-7 follow-up because they need
notification scheduling, permission copy, and anti-nag rules.

## Acceptance Criteria

- Plan has a `Bills` tab next to Budget, Wishlist, and Investments.
- Active monthly, weekly, and yearly rules appear in date order through the next 60 days.
- Month-end rules clamp correctly, e.g. a 31st rule lands on February 28/29.
- Overdue active rules appear once with overdue styling.
- Inactive recurring rules do not appear.
- Uncategorized pending transactions appear as review items.
- Reconciliation rows are irrelevant because the view reads recurring rules and pending items only.

## Validation

- Pure JVM tests for recurrence projection in `core:domain`.
- Feature compile for `feature:plan`.
- Both debug app flavors build before publishing.
