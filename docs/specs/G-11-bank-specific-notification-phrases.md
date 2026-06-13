# G-11 - Bank-Specific Notification Phrase Coverage

## Problem

The store-safe flavor already parses common bank-app notifications, but several real global-bank
phrases still lose merchant context even when the notification clearly represents money movement.
Examples include card-charge copy such as "Your card was charged ... by Netflix", US-bank debit-card
alerts that say "transaction from Trader Joe's", and wallet copy that appends "with Apple Pay" to the
merchant. If these parse without a merchant, users still have to clean up the row manually and local
category learning cannot create a useful merchant rule.

## Decision

Extend the existing conservative generic notification parser rather than adding app-specific
templates:

- For expenses, allow merchant extraction from `by ...` and `from ...` only after the notification has
  already been classified as an expense.
- Recover merchants that appear between the action verb and the amount, such as
  `You paid Apple Services $9.99` and `Debit card purchase Starbucks USD 4.85`.
- Strip wallet/card transport suffixes like `with Apple Pay`, `with Google Pay`, or `using card ...`
  from merchant names.
- Add package allow-list keywords for more common bank apps that emit these phrase shapes, including
  HSBC, Barclays, Lloyds, NatWest, Santander, Halifax, U.S. Bank, PNC, and SoFi.

## Acceptance Criteria

- `Your card was charged $8.99 by Netflix` parses as an expense with merchant `Netflix`.
- `Debit card transaction from Trader Joe's for $23.10` parses as an expense with merchant
  `Trader Joe's`.
- `You paid Apple Services $9.99` parses as an expense with merchant `Apple Services`.
- `You paid $9.99 to Apple Services with Apple Pay` strips the wallet suffix from the merchant.
- New package keywords reach the parser, while arbitrary `wallet`/shopping packages remain excluded.

## Validation

- `:ingestion:sms-parser:test`
- `:ingestion:notification-listener:test`
- Full static app validation before branch handoff:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime notification tap-through remains under QA-01 until an Android device is attached or
  emulator acceleration is available.
