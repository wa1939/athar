# G-12 — CSV/TSV Delimiter Detection

## Problem

G-12 statement import handled common CSV headers, but the parser only split rows on commas. Many banks export semicolon-delimited CSVs, especially when amounts use comma decimals, and some export tab-separated text. Those users would have to open the file in a spreadsheet and re-save it before Athar could preview it.

## Decision

Detect delimited statement format by trying comma, semicolon, and tab against the header row, then selecting the delimiter whose parsed header maps to known statement columns. The existing quoted-field parser now accepts a delimiter argument, so quoted delimiters and `""` escapes keep working.

The change is intentionally scoped to CSV-style text. OFX/QFX and MT940 detection still runs first, then delimited header detection runs before row mapping. Import and preview continue to use the same parse path.

## Acceptance

- Existing comma CSV behavior remains unchanged.
- Semicolon-delimited statements preview/import when their headers match supported aliases.
- Tab-delimited statements preview/import when their headers match supported aliases.
- European-style comma decimals in semicolon statements continue to parse through the existing amount mapper.
- Invalid delimited files still fail before any rows are inserted.

## Validation

- `:core:data:test`
- `:feature:settings:test`
- Full JVM test/build/lint stack
- `git diff --check`
- Runtime E2E remains under QA-01 until a physical Android device or accelerated emulator is available.
