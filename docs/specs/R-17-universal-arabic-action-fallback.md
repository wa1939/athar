# R-17 - Universal Arabic Action Fallback

## Problem

The module-scoped private SMS audit on `dev/bulk-categorize-category-options`
showed 193 parsed expense rows with no merchant. The new redacted
`missingMerchantGroups` audit output showed those rows were dominated by
`universal-amount` fallback parses from Al Rajhi Arabic transfer copy.

The root cause was the universal fallback's action regexes: Arabic alternatives
were inside `\b...\b` word-boundary expressions. Those boundaries are reliable
for Latin words but not for Arabic text, so `حوالة` rows fell through to the
default `EXPENSE` type. That polluted the category backlog with transfer rows
that should not need categories.

## Decision

- Split universal fallback action detection into Latin word-boundary regexes and
  Arabic phrase regexes without `\b`.
- Keep fallback confidence low, but classify Arabic `حوالة` / `تحويل` copy as
  `TRANSFER`, Arabic purchase/debit copy as `EXPENSE`, and Arabic income words as
  `INCOME`.
- Route the extracted party into `counterparty` for non-expense fallback rows;
  only expense rows keep it as `merchant`.
- Extend the gated `PrivateSmsExportAuditTest` report with top redacted
  `missingMerchantGroups`: template id, sender hash/kind, body-shape hash,
  shape preview, body features, and count. The report still writes no raw SMS
  bodies, senders, merchants, amounts, account/card numbers, or notes.

## Acceptance

- Synthetic Arabic fallback transfer copy with `حوالة` parses as `TRANSFER`, not
  `EXPENSE`.
- Synthetic Arabic fallback purchase copy still parses as `EXPENSE` and keeps
  the party as `merchant`.
- Private audit report includes `missingMerchantGroups` when missing-merchant
  expense rows exist.
- Latest private audit report has 0 parsed expense rows with missing merchants.
- No raw private SMS text is committed or written by the audit.

## Non-goals

- Do not promote private person or account-tail strings into shared seed rules.
- Do not make universal fallback high confidence.
- Do not parse unknown senders; the universal fallback remains restricted to
  known bank/wallet senders.

## Validation

- `:ingestion:sms-parser:test` passes.
- Full stack passes: `test`, `:app:assemblePersonalFullSmsDebug`,
  `:app:assembleStoreSafeDebug`, `:app:lintPersonalFullSmsDebug`, and
  `:app:lintStoreSafeDebug`.
- Gated private SMS export audit passes. Latest module-scoped report shows 7,887
  records, 3,864 parser successes, 4,023 ignored messages, 0 parser failures,
  0 failed known-bank messages, 2,374 parsed expenses, 1,262 categorized
  expenses, 1,112 uncategorized expenses, 0 missing-merchant parsed expenses,
  0 raw bodies written, and no inactive catalog matches.
- Runtime install/launch was attempted on `AtharPixelQaApi35`, but the x86_64
  software launch stayed `emulator-5554 offline` for the bounded wait, so APK
  install and screenshot capture could not run. Cleanup finished with no
  attached ADB device, no emulator/qemu/netsim process, and no AVD lock files.
