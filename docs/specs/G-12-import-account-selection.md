# G-12 — Import Account Selection

## Problem

Statement imports previously wrote every CSV, OFX/QFX, and MT940 row to `MANUAL_ACCOUNT_ID`. That was safe for the earliest single-account model, but it is wrong for multi-account users: importing a checking-account statement into Cash forces cleanup and makes net-worth/account balances misleading until every row is edited.

## Decision

Keep preview account-neutral, then let Settings choose the destination account before confirmation. `CsvImportTrigger.import` now accepts an `accountId` with `MANUAL_ACCOUNT_ID` as its default so onboarding and older call sites keep working. Settings observes active accounts, shows an account picker in the Statement exchange card when more than one account is available, and passes the selected id into confirmation.

This slice intentionally does not add per-row account splitting. A single statement file is treated as one account's export, which matches CSV/OFX/QFX/MT940 bank-statement workflows and keeps the preview-confirm contract simple.

## Acceptance

- Existing onboarding import still works without account-selection UI.
- Settings import preview still does not insert rows.
- Confirming Settings import writes CSV/OFX/QFX/MT940 rows to the selected active account.
- If the selected account disappears, Settings falls back to the first active account.
- Existing statement format detection and row skipping behavior remain unchanged.

## Validation

- `:core:data:test`
- `:feature:settings:test`
- Full JVM test/build/lint stack
- `git diff --check`
- Runtime E2E remains under QA-01 until a physical Android device or accelerated emulator is available.
