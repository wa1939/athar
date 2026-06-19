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
- category-coverage metrics for eligible, categorized, backlog, top-group, and
  largest-group coverage
- category-backlog recommendation codes derived from aggregate backlog size and
  hashed specific repeated-group sizes (`none`, `history_repeated_backlog`,
  `bulk_categorize_export`, or `manual_cleanup`)
- top specific repeated uncategorized merchant groups represented only by pseudonymous
  merchant hashes, coarse length/script buckets, status/source/type/currency
  counts, confidence buckets, first/last dates, and per-group/cumulative backlog
  share
- generic labels such as `payment`, `cash`, `bank`, or `كاش` are still included
  in total backlog counts, but they do not appear as repeated-group evidence

These fields let maintainers answer questions like:

- Are failures concentrated on one pseudonymous sender?
- Are messages failing before any amount is found?
- Are template attempts reaching the expected bank-family parser?
- Are OTP/promotional messages being classified correctly as ignored?
- Is cleanup friction now parser failure, repeated uncategorized merchants, or
  transfer rows that intentionally have no category?
- Is the category backlog concentrated enough to recommend repeated-history or
  bulk-categorization cleanup, or scattered enough to point toward parser/seed
  review?
- Are uncategorized rows concentrated in SMS, notification, import, or manual
  sources?

## Support Workflow

1. Ask the user to open Settings -> Support diagnostics.
2. Ask them to tap Export diagnostics and save `athar-support-diagnostics.json`.
3. Review status counts, sender groups, error groups, and transaction summary first.
4. If parsing is healthy but `category_backlog_transactions` is high, inspect
   `category_backlog_recommendation`, `category_coverage`, and repeated hashed
   specific merchant groups before asking the user to run the History
   repeated-backlog or bulk-categorize workflow. Generic-heavy backlog with no
   specific repeated groups should stay manual or bulk CSV, not History repeated
   cleanup.
5. Only ask for a raw SMS sample if the diagnostics file proves the failure cannot be
   understood from shape, counts, redacted reason, template IDs, and aggregate
   category-backlog data.

This replaces the old idea of an unencrypted full support snapshot. A full snapshot
would be easier to inspect, but it would conflict with Athar's local-first privacy
promise. The diagnostics file is intentionally narrower.
