# G-08 Manual Entry Specific-Merchant Suggestions

## Problem

Manual quick-add chips are meant to reduce typing for routine transactions when SMS, notification, or statement import coverage is incomplete. The first implementation grouped confirmed history by raw `merchantNormalized`, so generic labels such as `payment`, `cash`, `bank`, `كاش`, or `دفع` could appear as shortcuts and make the manual-entry fallback feel noisy.

## Decision

Build quick-add/autocomplete suggestions from confirmed, non-reconciliation expense/income rows only after passing each row through the shared `specificMerchantKey(...)` guard. Group by transaction type plus the guarded merchant key. If `merchantNormalized` is blank, the guard may fall back to a specific display merchant.

## Acceptance

- Specific repeated merchants still produce quick-add chips with latest merchant, category, and currency-safe amount.
- Generic English/Arabic labels do not produce quick-add chips.
- Rows with a blank normalized merchant can still produce a chip when the display merchant is specific.
- Pending, dismissed, transfer, and reconciliation rows remain excluded.
