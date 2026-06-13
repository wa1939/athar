# R-10 — SMS corpus reliability for family-device imports

## Goal

Improve first-run SMS backfill reliability on a second user's device by covering high-volume senders observed in `All Conversations 2026-05-27 175517.txt`: `SNB-AlAhli`, `AlJaziraSMS` / `Jazira Bank`, and `urpay`.

## Inputs

- SMS Exporter text blocks shaped as `Received from <sender> on <yyyy-MM-dd HH:mm>` followed by the SMS body.
- Existing local-first parser pipeline: known-sender allow-list, global ignore template, bank-specific templates, shared structured-bank fallback, and universal known-bank fallback.

## Acceptance

- `SNB-AlAhli` Arabic outgoing transfer confirmations parse as `TRANSFER`, not `EXPENSE`.
- `SNB-AlAhli` OTP/pre-authorization messages that include an amount are ignored.
- `AlJaziraSMS` incoming transfer confirmations parse as `INCOME`.
- `Jazira Bank` outgoing transfer confirmations parse as `TRANSFER`.
- `Jazira Bank` one-time-password messages that include an amount are ignored.
- `urpay` Arabic purchase confirmations parse as `EXPENSE` and preserve the merchant from `من:` or `لدى:`.
- Non-transaction notices from supported wallet/bank senders, such as terms updates and device-link notices, are ignored rather than becoming failed parse rows or pending transactions.

## Non-goals

- Do not ingest utility bill notices from non-bank service senders as paid transactions unless a bank/payment sender confirms money movement.
- Do not add a backend or shared user-data collection path.
- Do not commit the private SMS export corpus; pin only anonymized structural samples in unit tests.
