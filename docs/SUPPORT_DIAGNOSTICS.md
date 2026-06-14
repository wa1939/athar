# Support Diagnostics Export

Athar can export an opt-in redacted JSON report from Settings -> Support diagnostics.
The report is meant for parser-support triage when a user says a bank SMS parsed
wrongly or did not parse at all.

## Privacy Contract

The diagnostics file never includes:

- raw SMS body text
- raw sender names or phone numbers
- raw merchant names
- transaction rows or notes
- balances, card numbers, account numbers, IBANs, OTPs, or amounts
- automatic upload behavior

The user explicitly chooses a save location through Android's document picker. Athar
does not transmit the file anywhere.

## What The JSON Contains

- schema and generated-at timestamp
- total SMS audit counts by parse status
- a bounded recent sample, currently 500 audit rows
- pseudonymous SHA-256 prefixes for row IDs and senders
- SHA-256 prefixes for body-shape signatures, not raw SMS bodies
- sender kind and sender/body length buckets
- body-shape flags such as language script, currency marker presence, OTP marker
  presence, masked-card marker presence, action hints, token count, and line count
- failed parser-error groups with sensitive numbers redacted
- parser template IDs tried for failed rows
- aggregate transaction categorization-backlog counts
- top repeated uncategorized merchant groups represented only by pseudonymous
  merchant hashes, coarse length/script buckets, status/source/type/currency
  counts, confidence buckets, and first/last dates

These fields let maintainers answer questions like:

- Are failures concentrated on one pseudonymous sender?
- Are messages failing before any amount is found?
- Are template attempts reaching the expected bank-family parser?
- Are OTP/promotional messages being classified correctly as ignored?
- Is cleanup friction now parser failure, repeated uncategorized merchants, or
  transfer rows that intentionally have no category?
- Are uncategorized rows concentrated in SMS, notification, import, or manual
  sources?

## Support Workflow

1. Ask the user to open Settings -> Support diagnostics.
2. Ask them to tap Export diagnostics and save `athar-support-diagnostics.json`.
3. Review status counts, sender groups, error groups, and transaction summary first.
4. If parsing is healthy but `category_backlog_transactions` is high, inspect the
   repeated hashed merchant groups before asking the user to run the bulk-categorize
   workflow.
5. Only ask for a raw SMS sample if the diagnostics file proves the failure cannot be
   understood from shape, counts, redacted reason, template IDs, and aggregate
   category-backlog data.

This replaces the old idea of an unencrypted full support snapshot. A full snapshot
would be easier to inspect, but it would conflict with Athar's local-first privacy
promise. The diagnostics file is intentionally narrower.
