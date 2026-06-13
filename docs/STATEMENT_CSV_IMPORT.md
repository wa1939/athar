# Statement CSV Import

Athar's Settings -> CSV exchange importer now accepts both the original
Athar/TMOAP-style CSV and common bank-statement CSV exports.

## Supported layouts

- Athar/TMOAP export columns: `date`, `vendor` or `merchant`, `amount`, optional
  `currency`, `category`, `type`, `notes`.
- Bank statement columns: `date` or `transaction date`, `description`,
  `details`, `narrative`, `payee`, or Arabic equivalents.
- Amount columns: a single signed `amount`, or split `debit` / `credit`
  columns. Debit rows import as expenses; credit rows import as income.
- Currency: optional ISO-4217 code such as `SAR`, `USD`, `GBP`, or `AED`.
  Missing currency defaults to `SAR`.

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

The G-12 slices now include OFX/QFX/MT940 parsing, safe preview/confirm, account
selection, duplicate-safe stable references, manual CSV column mapping, and preview-row
exclusion. Preview also summarizes importable rows by currency with expense, income, and
transfer totals. Field-level row editing remains planned for the full import wizard.
