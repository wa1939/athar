# S-21 SMS Audit Sender Health

## Goal

Make the SMS audit log useful when a new user's inbox behaves badly. The user, tester, or maintainer should be able to see which sender labels are failing, which are being ignored as noise, and whether the parser is broadly healthy after a backfill.

## Inputs

- Existing `SmsAuditEntry` rows from the local audit repository.
- Parse status: `PARSED`, `FAILED`, or `IGNORED`.
- Sender label exactly as Android received it.

## Behavior

The SMS audit screen shows the existing parsed, failed, and ignored counts plus a successful parse-rate percentage. It also shows a compact "Sender health" card sorted by highest failed count, then ignored count, then total volume. Each row shows the sender and counts for parsed, failed, and ignored messages.

This is diagnostic only. It does not upload SMS data, mutate parsing rules, or change ingestion behavior.

## Acceptance

- Counts and parse rate are derived from the full audit list, not just the visible 200 rows.
- The sender-health card lists at most five senders.
- Failed senders rank above ignored-only senders.
- The SMS log still supports the existing All / Parsed / Failed / Ignored filters.
