# G-11 - Notification App Handlers Follow-Up

## Problem

The store-safe flavor now has a generic bank-app notification parser, but real finance apps still
use copy that falls outside the first baseline. Peer-payment apps often say "John sent you $25"
instead of "paid you", payroll and business banks can say "you got paid ... by ACME", and global
users may receive CAD/AUD/CHF notifications. Google Pay India and Samsung Pay package names also
need to reach the same conservative parser path.

## Decision

Extend the existing generic notification parser and package matcher rather than creating a separate
app-specific parser stack:

- Treat `sent you`, `got paid`, `was paid`, and `were paid` as income phrases.
- Extract income counterparties from `by ...` and leading `Name sent you` / `Name paid you` forms.
- Support `CAD`, `AUD`, `CHF`, `CA$`, `C$`, `AU$`, and `A$` in notification amount parsing.
- Ignore peer-payment request notifications that contain amounts but are not money movement.
- Allow Google Pay India (`com.google.android.apps.nbu.paisa.user`) and Samsung Pay package variants
  without allowing arbitrary `wallet` packages.

## Acceptance Criteria

- `John Appleseed sent you $25.00` from Cash App parses as INCOME.
- `You got paid USD 250.00 by ACME Payroll` parses as INCOME with `ACME Payroll` as counterparty.
- `You spent CAD 12.34 at Tim Hortons` parses as a CAD expense.
- Google Pay India package notifications reach the parser.
- `John requested $25.00` from Venmo is ignored, not recorded or failed as a transaction.
- Random packages such as `com.random.wallet` remain excluded.

## Validation

- `:ingestion:sms-parser:test`
- `:ingestion:notification-listener:test`
- Full static app validation before branch handoff:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime notification tap-through remains under QA-01 until an Android device is attached or
  emulator acceleration is available.
