# W-09 Wishlist Planning Summary

## Problem

The Wishlist tab already projects each item with TMOAP-style capacity math, but the screen did not answer the portfolio-level planning questions:

- How much wishlist backlog remains?
- How many wishes are safe to buy now?
- What is the next reachable month?
- How much monthly pressure do target-dated wishes create?
- How many wishes need more savings capacity?

Without this summary, users had to scan every row and do the planning math mentally.

## Scope

- Add a pure `WishlistCalc.Summary` derived from the same projections used by the item rows.
- Show the summary inside the existing monthly-capacity card in Plan -> Wishlist.
- Keep all math local-first and deterministic.
- Add domain tests for mixed ready/wait/infeasible wishes and empty-state currency.
- Do not change the Wishlist schema, repository contract, or item editor.

## UX Contract

The capacity card remains the first element on the Wishlist tab. Under the monthly capacity number it shows:

- remaining wishlist amount
- ready-now count
- next reachable month, or none
- target monthly pressure
- infeasible count only when at least one wish needs more capacity

Labels are short in Arabic and English so the card stays scannable on small screens.

## Acceptance

- Summary counts match the per-item statuses returned by `WishlistCalc.project`.
- Total remaining uses the user's display currency projection, matching existing Wishlist aggregation behavior.
- Target pressure is the sum of per-item monthly required amounts for wishes with target horizons.
- Existing item sorting and add/edit/delete flows are unchanged.
- Focused Wishlist tests and Plan compilation pass.
