# G-08 — Manual Entry Quick Add

## Problem

Users outside strong SMS coverage still need to enter transactions manually. The existing add sheet
worked, but it treated every transaction as brand new: the user retyped the same merchant, amount,
and category even when Athar had seen that pattern many times before.

## Decision

Add lightweight, local-only suggestions to the Add Transaction sheet:

- Build the top 20 suggestions from confirmed, non-reconciliation transactions.
- Rank by merchant frequency, separately for expense and income.
- Use the latest transaction in each merchant/type group as the preset for merchant, amount, type,
  and category.
- Hide the preset amount when the latest transaction is in a different currency from the user's
  current display currency.
- Filter the same chips as the user types in the merchant field, so the row acts as both quick-add
  and autocomplete.

This uses existing ledger data only. No backend, no shared merchant database, no cloud prediction.

## Acceptance Criteria

- Empty manual-entry sheet shows recent merchant chips for the selected type.
- Tapping a chip fills merchant, amount when currency-safe, type, and category.
- Typing in the merchant field narrows the chips to matching recent merchants.
- Pending, dismissed, transfer, and reconciliation rows do not produce suggestions.
- Suggestions survive after saving a transaction and resetting the form.
- Voice entry and receipt attachment remain out of scope for this slice.

## Implementation

- `ManualEntrySuggestionBuilder` is pure Kotlin and covered by unit tests.
- `AddTransactionViewModel` combines `TransactionRepository.observeAll()` with display currency to
  keep suggestions current.
- `AddTransactionSheet` renders the visible suggestions as compact horizontal chips below the
  merchant field.

## Validation

- `:feature:today:testDebugUnitTest`
- `:feature:today:compileDebugKotlin`
- `:app:assemblePersonalFullSmsDebug`
- `:app:assembleStoreSafeDebug`
