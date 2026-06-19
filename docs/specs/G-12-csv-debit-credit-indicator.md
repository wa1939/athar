# G-12 - CSV Debit/Credit Indicator Support

## Problem

Some bank statement CSVs export one positive `Amount` column plus a separate debit/credit marker such as `D/C`, `Debit Credit Indicator`, `DBIT`/`CRDT`, or Arabic `مدين`/`دائن`. Athar already handled signed amount columns and split debit/credit columns, but these positive-amount statement files could import debits as income unless the user preprocessed the file in a spreadsheet.

## Decision

Extend statement header detection to recognize debit/credit indicator columns, including compact aliases (`D/C`, `DC`, `DR/CR`), descriptive aliases (`direction`, `entry type`, `movement type`), and Arabic aliases (`مدين/دائن`, `اشاره`). Row mapping now treats debit markers (`D`, `DBIT`, `debit`, `مدين`) as expenses and credit markers (`C`, `CRDT`, `credit`, `دائن`) as income while preserving the original positive amount.

This remains an auto-detection slice. It does not add manual column mapping, per-row editing, or richer multi-currency statement review.

## Acceptance

- Positive `Amount` + `D/C = D` previews/imports as an expense.
- Positive `Amount` + `D/C = C` previews/imports as income.
- Arabic `مدين/دائن` headers and values work the same way.
- Split debit/credit columns and signed amount columns keep their existing behavior.
- Preview surfaces the detected type column before commit.

## Validation

- `:core:data:testDebugUnitTest`
- `:core:data:compileDebugKotlin`
- Full JVM test/build/lint stack
- `git diff --check`
- Runtime import tap-through remains under QA-01 until a physical Android device or accelerated emulator is available.
