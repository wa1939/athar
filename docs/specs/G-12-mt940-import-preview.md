# G-12 — MT940 Import Preview

## Problem

G-12 now covers spreadsheet CSV and OFX/QFX, but many banks still export SWIFT MT940 statements. Without MT940 support, users outside the original SMS-first path may still need manual transaction entry even when their bank offers a machine-readable export.

## Decision

Extend the existing statement importer to detect MT940 text and parse it before CSV header detection. The Settings flow remains the same preview-confirm path used by CSV and OFX/QFX, so selecting an MT940 file never writes transactions immediately.

Mapped fields:

- `:61:` value date becomes the transaction date.
- `:61:` debit/credit mark maps debit to expense and credit to income.
- `:61:` amount is parsed with comma decimal support and stored as an absolute `Money` amount.
- `:61:` bank reference after `//` becomes the import source reference when present.
- Following `:86:` details become the merchant/counterparty text.
- `:60F:`, `:60M:`, `:62F:`, or `:62M:` balance records provide statement currency when present.

Malformed `:61:` records are counted as skipped transactions instead of aborting the entire file. Records without usable merchant details are skipped because guessing from bank references would create misleading ledger entries.

## Acceptance

- A valid MT940 file previews importable/skipped counts, detected field labels, sample rows, currency, and income/expense direction.
- Confirming imports the same selected bytes as confirmed transactions with `source = IMPORT`.
- Missing date, amount, or merchant skips only that MT940 transaction.
- Existing CSV and OFX/QFX imports remain unchanged.
- Settings copy and file picker advertise the broader statement import support.
- Import confirmation can target a selected active account in the follow-up G-12 account-selection slice.

## Validation

- `:core:data:test`
- `:feature:settings:test`
- Full JVM test/build/lint stack
- `git diff --check`
- Runtime E2E remains under QA-01 until a physical Android device or accelerated emulator is available.
