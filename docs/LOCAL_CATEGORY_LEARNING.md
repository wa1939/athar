# Local Category Rule Learning

Athar reduces repeated categorization work by learning conservative local merchant
rules from the user's confirmed history.

## Rule

When a user updates an existing transaction, creates a manual transaction, or imports
categorized history, Athar checks that merchant's confirmed history. It creates an
exact local category rule only when all of these are true:

- the normalized merchant is specific enough, not a generic label such as
  `unknown`, `online purchase`, `payment`, `cash`, `bank`, `كاش`, `دفع`, or
  `تسوية`
- the transaction is not a transfer and not a reconciliation adjustment
- the merchant has at least three confirmed transactions
- every confirmed transaction for that exact merchant has the same category
- no explicit user rule already matches the merchant
- no seed rule already matches the same category

New SMS or notification rows that auto-confirm from an existing seed rule do not run
this learner on insert. That avoids extra per-SMS reads during large backfills.

The learned rule is `PatternType.EXACT`, priority `150`, and `learnedFromUser=false`.
That places it above bundled seed rules, below explicit "Always categorize X" rules,
and outside the community-rule export path.

The specific-merchant guard is shared with Today suggestions, History repeated
backlog cleanup, and the bulk-categorization CSV workflow so generic merchant
labels cannot drift into safe-suggestion or learned-rule paths in one feature but
not another.

## Why Exact Match

The first goal is to remove repeated prompts without creating false positives.
Exact rules are intentionally narrower than the explicit "Always" flow, which still
uses substring matching and priority `200` because the user directly asked for it.

Example:

- Three confirmed `jarir bookstore` transactions categorized as `education` create
  an exact `jarir bookstore -> education` local rule.
- If `amazon` has transactions in both `shopping` and `software`, Athar removes any
  existing auto-local rule for `amazon` and waits for explicit user intent.

## Privacy

Rules are stored only in the encrypted local database. They are not uploaded and are
not included in the community-rule sharing export, because the user did not explicitly
tap "Always categorize X" for them.
