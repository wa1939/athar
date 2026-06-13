# G-12 — OFX/QFX Import Preview

## Problem

The first G-12 import slice made CSV migration safe by previewing detected fields and sample rows before commit. Many banks, especially outside the original Saudi SMS-first path, export OFX or QFX instead of statement CSV. Users need to bring those files into the same conservative import flow without a second UI or blind writes.

## Decision

Extend the existing `CsvImportTrigger` implementation to detect OFX/QFX text and parse `<STMTTRN>` blocks before falling back to CSV header detection. The Settings card becomes "Statement exchange" and accepts OFX/QFX MIME types, but the preview-confirm state machine remains unchanged.

Mapped fields:

- Date: `DTPOSTED`, falling back to `DTUSER`.
- Amount: signed `TRNAMT`, stored as an absolute `Money` amount.
- Type: `TRNTYPE` when present, otherwise signed amount direction.
- Merchant: `NAME`, `PAYEE`, then `MEMO`.
- Currency: statement `CURDEF`, transaction `CURSYM`, or SAR fallback.
- Reference: `FITID` as the import source reference when present.

Malformed statement transactions are counted as skipped rows. No OFX/QFX row is inserted during preview, and import uses the same parse plan as preview.

## Acceptance

- A valid OFX/QFX file previews importable/skipped counts, synthetic detected field labels, sample rows, currencies, and transaction types.
- Confirming imports the same selected bytes as confirmed transactions with `source = IMPORT`.
- Missing date, amount, or merchant skips only that statement transaction.
- Existing CSV import/export behavior remains unchanged.
- Settings copy and file picker no longer imply the import path is CSV-only.

## Validation

- `:core:data:test`
- `:feature:settings:test`
- Full JVM test/build/lint stack
- `git diff --check`
- Runtime E2E remains under QA-01 until a physical Android device or accelerated emulator is available.
