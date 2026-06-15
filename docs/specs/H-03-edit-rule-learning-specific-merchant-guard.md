# H-03 Edit Rule-Learning Specific-Merchant Guard

## Problem

The Today and History edit sheet offers an explicit "Always categorize X as Y" flow. That is useful for a real merchant such as Hemmah or Brew Lab, but it is too broad for generic parser labels such as `payment`, `cash`, `bank`, `كاش`, or `دفع`. Training a substring rule and backfilling those labels can recategorize unrelated rows.

## Decision

Use the shared `specificMerchantKey(...)` guard before prompting for or applying explicit edit-sheet learning:

- The edit sheet only opens the "Always categorize" prompt when the current merchant is specific.
- `TodayViewModel.updateTransaction(...)` and `HistoryViewModel.updateTransaction(...)` guard `learnFromCorrection(...)` and `applyCategoryToMatching(...)` as a fallback.
- Generic labels still save the edited transaction and selected category as a one-row correction.

## Acceptance

- Specific merchants still create the explicit learned rule and backfill matching pending/dismissed rows.
- Generic English/Arabic labels do not show the learning prompt.
- Generic labels do not create learned rules or backfill patterns even if a caller passes `learnRule = true`.
- The one edited transaction is still saved and confirmed.
