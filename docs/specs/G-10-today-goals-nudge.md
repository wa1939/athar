# G-10 — Today Goals Nudge

## Problem

The Plan Goals tab lets users set savings-rate and emergency-fund targets, but the home screen still
does not answer whether the current month is broadly on track. A TMOAP replacement should surface
that planning context where the user already checks daily money movement, without turning goals into
gamification or nagging.

## Decision

Add a compact "Goals check" cue to the Today header. It reuses the same goal math and persisted
targets as Plan:

- Savings-rate target: compare the last 3 months of confirmed income/expense against the stored
  savings-rate percentage.
- Emergency fund target: compare liquid balance from `CHECKING`, `SAVINGS`, and `CASH` accounts
  against average monthly expense and the stored target month count.
- Reconciliation adjustments are excluded from both calculations.

The cue is read-only and local-only. Users still edit targets in Plan, which keeps Today focused on
daily review rather than configuration.

## Acceptance Criteria

- Today shows a compact Goals check when app data is loaded.
- The savings line shows actual savings rate versus the persisted target.
- The emergency line shows covered months versus the persisted target.
- On-track values use the positive/olive accent; below-target values use the ember accent.
- If income is zero, the savings line says more income data is needed instead of dividing by zero.
- If expense is zero, the emergency line says more expense data is needed instead of inventing
  coverage.
- The cue is localized in Arabic and English.
- The implementation reuses `GoalCalc` and does not duplicate incompatible goal formulas.

## Non-Goals

- No notifications, badges, streaks, or shaming copy.
- No target editing from Today.
- No new persistence schema.

## Validation

- `:feature:today:test`
- Full JVM test/build/lint stack before branch handoff:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`

Runtime UI tap-through still depends on QA-01: connect a physical Android device or enable hardware
virtualization so the AVD can boot.
