# G-10 — Savings Goals

## Problem

Athar shows budget targets, wishlist capacity, and net worth, but it does not yet answer two
questions that serious TMOAP users ask every month:

- Am I saving the percentage of income I intended to save?
- Do I have enough liquid cash to absorb an emergency?

Without that layer, the user sees many useful numbers but still has to do the planning math outside
the app.

## Decision

Add a `Goals` section under Plan with two local-only targets:

- **Savings-rate goal**: default 20%. Computed from the last 3 months of confirmed income and
  expenses, excluding reconciliation adjustments. Actual savings rate is `(income - expense) /
  income`.
- **Emergency-fund goal**: default 6 months. Computed as liquid account balance divided by average
  monthly expenses. Liquid accounts are `CHECKING`, `SAVINGS`, and `CASH`; investments and credit
  cards are not counted as emergency cash.

Targets are stored in DataStore through `UserPreferencesRepository`. No Room migration and no
backend are required.

## Acceptance Criteria

- Plan has a `Goals` tab next to Budget, Wishlist, and Investments.
- Savings-rate card shows actual savings rate, target, average monthly income, average monthly
  expenses, and average monthly savings.
- Emergency-fund card shows months covered, target months, liquid balance, average monthly expense,
  and target amount.
- Tapping either card lets the user edit and persist that target.
- If income is zero, savings-rate progress is shown as not available instead of dividing by zero.
- If expense is zero, emergency-fund coverage is shown as complete because no monthly burn exists.
- Reconciliation adjustment transactions never affect either goal.

## Non-Goals

- No FIRE projection, retirement math, or investment-price data.
- No cloud sync or shared family targets.
- No notifications or shaming nudges in this slice.

## Validation

- `:core:domain:test`
- `:feature:plan:compileDebugKotlin`
- `:core:data:compileDebugKotlin`
- `:app:assemblePersonalFullSmsDebug`
- `:app:assembleStoreSafeDebug`
- `git diff --check`

Runtime UI tap-through was not run because `adb devices` returned no connected devices.
