# Support Diagnostics Export

Athar can export an opt-in redacted JSON report from Settings -> Support diagnostics.
The report is meant for parser-support triage when a user says a bank SMS parsed
wrongly or did not parse at all.

## Privacy Contract

The diagnostics file never includes:

- raw SMS body text
- raw sender names or phone numbers
- balances, card numbers, account numbers, IBANs, OTPs, or amounts
- transaction rows or notes
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

These fields let maintainers answer questions like:

- Are failures concentrated on one pseudonymous sender?
- Are messages failing before any amount is found?
- Are template attempts reaching the expected bank-family parser?
- Are OTP/promotional messages being classified correctly as ignored?

## Support Workflow

1. Ask the user to open Settings -> Support diagnostics.
2. Ask them to tap Export diagnostics and save `athar-support-diagnostics.json`.
3. Review status counts, sender groups, and error groups first.
4. Only ask for a raw SMS sample if the diagnostics file proves the failure cannot be
   understood from shape, counts, redacted reason, and template IDs.

This replaces the old idea of an unencrypted full support snapshot. A full snapshot
would be easier to inspect, but it would conflict with Athar's local-first privacy
promise. The diagnostics file is intentionally narrower.
