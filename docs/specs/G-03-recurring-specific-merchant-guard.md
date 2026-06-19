# G-03 Recurring Specific-Merchant Guard

## Problem

Recurring suggestions are meant to save the user from re-entering true repeated obligations such as rent, salary, gym memberships, utilities, and subscriptions. The existing detector grouped confirmed history by `merchantNormalized + amount`, so generic parser labels such as `payment`, `cash`, `bank`, `كاش`, or `دفع` could look recurring when several unrelated rows shared the same amount and day window.

## Decision

Auto-detected recurring suggestions must pass the shared domain `specificMerchantKey(...)` guard before grouping. Generic English/Arabic labels are ignored for suggestion generation, while specific merchant history still groups by the normalized merchant key, amount, and currency.

## Acceptance

- A repeated specific merchant with the same amount in at least three distinct months still emits a `RecurringSuggestion`.
- Repeated generic labels do not emit recurring suggestions, even when amount and day-of-month match.
- Existing manual recurring rules and confirm-before-create behavior stay unchanged.
- Documentation and recovery notes record the validation and remaining runtime QA blocker.
