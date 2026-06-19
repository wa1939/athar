# R-05 — Curated Seed Rules Batch 2

## Problem

Athar already has a large seed catalog, but some high-confidence reviewed entries were still present only in `seed_merchant_catalog.json`, not the active `seed_rules.json` that ships to users. That means first-run users would still see avoidable uncategorized transactions for common merchants and bank-generated merchant labels.

## Decision

Promote a second conservative priority-90 batch from the reviewed catalog into the active seed:

- Restaurants/coffee: `herfy`, `luckin`, `manner coffee`
- Gas/transport: `aldrees`, `didi taxi`, `hellobike`
- Medical/utilities: `alnahdi`, `saudi electric`, `electric company`
- Clothing/travel: `zara`, `uniqlo`, `airbnb`, `booking.com`
- Debt/service labels emitted by bank parsers: `credit card payment`, `loan instalment`

The batch intentionally avoids broad ambiguous words. Patterns remain substring rules at priority 90, below hand-curated priority-100 rules and below user-learned rules.

## Acceptance

- `seed_rules.json` increases from 618 to 633 active rules.
- Every new pattern references an existing category id.
- No duplicate normalized patterns are introduced.
- The asset test pins every new rule at priority 90.
- R-05 remains open for broader low-confidence review from sanitized merchant exports.

## Validation

- `:core:data:test`
- Full JVM test/build/lint stack
- `git diff --check`
- Runtime E2E remains under QA-01 until a physical Android device or accelerated emulator is available.
