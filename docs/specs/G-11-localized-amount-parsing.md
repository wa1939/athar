# G-11 — Localized Amount Parsing for Notifications and Fallback SMS

## Problem

Store-safe notification ingestion and the universal SMS fallback must work outside the Saudi and US-style decimal formats. Many European and global banks write amounts as `18,50 EUR`, `€18,50`, or `1.234,56 EUR`. The existing generic parsers treated commas only as thousands separators, so a notification could be truncated or inflated before it reached the pending tray.

## Decision

Add one shared parser helper in `Normalize` for localized money strings. It supports:

- US grouping: `1,234.56`
- European grouping: `1.234,56`
- space grouping with comma decimals: `12 345,67`
- plain comma decimals: `18,50`

Wire that helper into the generic structured bank SMS template, the universal amount fallback, and the generic bank notification template. The universal fallback now also uses the detected currency token instead of defaulting every parsed fallback amount to SAR.

The change intentionally stays in generic/fallback paths. Bank-specific templates that already prefer SAR equivalents in parentheses keep their existing behavior.

## Acceptance

- Existing comma-thousands amounts continue to parse correctly.
- Generic notification parsing reads comma-decimal and European-grouped amounts without truncation.
- Generic structured SMS parsing reads comma-decimal amounts.
- Universal fallback messages from known bank senders preserve non-SAR currency codes.
- Unknown senders and ignorable notices remain ignored.

## Validation

- `:ingestion:sms-parser:test`
- Full JVM test/build/lint stack
- `git diff --check`
- Runtime E2E remains under QA-01 until a physical Android device or accelerated emulator is available.
