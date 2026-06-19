# G-11 - Global Notification Phrase Follow-Up

## Problem

The store-safe notification path already handles common spend/income/push copy, but several global finance apps still use phrasing that can lose merchant context or create false positives. Examples include merchant-first charge copy (`Netflix charged your card`), compact card transaction copy (`Card transaction Starbucks SGD 6.40`), and scheduled/bill-due notices that contain an amount but are not posted ledger movement.

Global users also receive currencies beyond the first display-currency set, especially in Wise/Revolut-style flows and travel banking.

## Decision

Extend the existing generic notification parser and package matcher, without adding app-specific parsers:

- Parse merchant-first charge copy such as `Netflix charged your card USD 8.99`.
- Parse compact card-transaction copy where the merchant appears before the amount.
- Preserve broader ISO codes in notification and known-bank universal fallback parsing: `CNY`, `HKD`, `SGD`, `SEK`, `NOK`, `DKK`, `ZAR`, `BRL`, `MXN`, `THB`, `IDR`, `MYR`, `PHP`, `VND`, and `KRW`.
- Ignore scheduled payment/transfer/autopay and bill/invoice due notices even when they contain amounts.
- Allow more finance package names to reach the conservative parser path: TransferWise, Payoneer, Remitly, Western Union, Bunq, Nubank, BBVA, Scotiabank, TD Bank, and RBC-style package ids.

## Acceptance

- `Netflix charged your card USD 8.99` parses as an expense with merchant `Netflix`.
- `Card transaction Starbucks SGD 6.40` parses as an expense with merchant `Starbucks`.
- `You spent SEK 129,00 at IKEA` parses as an SEK expense.
- `Your scheduled payment of USD 25.00 to Netflix is tomorrow` is ignored.
- New global finance packages match, while unrelated shopping/social/wallet packages remain excluded.

## Validation

- `:ingestion:sms-parser:test`
- `:ingestion:notification-listener:test`
- Full JVM test/build/lint stack
- `git diff --check`
- Runtime notification tap-through remains under QA-01 until a physical Android device or accelerated emulator is available.
