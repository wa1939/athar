# Statement Import

Athar's Settings -> Statement exchange importer accepts the original
Athar/TMOAP-style CSV, common bank-statement CSV/TSV exports, transaction-grid
XLSX workbooks, OFX/QFX, and MT940 files.

## Supported layouts

- Athar/TMOAP export columns: `date`, `vendor` or `merchant`, `amount`, optional
  `currency`, `category`, `type`, `notes`.
- Bank statement columns: `date` or `transaction date`, `description`,
  `details`, `narrative`, `payee`, or Arabic equivalents.
- Amount columns: a single signed `amount`, or split `debit` / `credit`
  columns. Debit rows import as expenses; credit rows import as income.
- Currency: optional ISO-4217 code such as `SAR`, `USD`, `GBP`, or `AED`.
  Missing currency defaults to `SAR`.
- XLSX: OpenXML `.xlsx` workbooks are scanned for transaction-like worksheet
  headers and use the same mapper as CSV. TMOAP-style separate `Expenses` and
  `Income` sheets preserve positive Amount columns as expenses/income from the
  sheet name when no explicit type column exists.

## Conservative rules

- Rows with missing date, missing merchant/description, unparseable dates, or
  ambiguous debit+credit values are skipped and counted in the import summary.
- Positive amounts in the original `date,vendor,amount` shape remain expenses
  unless a `type` column says otherwise, preserving the old manual import
  behavior.
- Positive signed amounts in statement-like layouts, such as
  `Posted Date,Narrative,Amount,Currency`, import as income; negative values
  import as expenses.
- Category names still resolve against Athar's English or Arabic category names.

The G-12 slices now include XLSX/CSV/TSV/OFX/QFX/MT940 parsing, safe preview/confirm,
account selection, duplicate-safe stable references, manual column mapping, preview-row
exclusion, per-currency totals, field-level preview row editing, and per-row account
review. Users can correct date, merchant, amount, currency, type, category, notes, and
the target account before confirmation; the same edited plan is used for duplicate
checks, stable import references, and final inserts.
