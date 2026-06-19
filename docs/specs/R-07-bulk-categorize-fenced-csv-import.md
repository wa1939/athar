# R-07 - Bulk Categorize Fenced CSV Import

## Problem

The in-app prompt asks ChatGPT or Claude to return the completed CSV in a
single `csv` code block. That is the safest way to keep the response readable,
but it creates a practical import trap: a user may save or paste the whole AI
response, including prose and Markdown fences, instead of manually extracting
only the raw CSV body. The previous importer treated the first prose line as the
header and failed with a missing-column error.

## Decision

- Keep plain CSV import behavior unchanged.
- Before parsing, look for a Markdown fenced block whose language is empty or
  starts with `csv`.
- Use the first fenced block that already looks like a bulk-categorize CSV:
  it must include a category column plus either `id` or `stable_key`.
- Ignore unrelated fenced blocks and fall back to the original whole-file CSV
  parsing when no valid fenced CSV block is present.

## Acceptance

- Raw exported CSV still imports exactly as before.
- A saved AI response with prose plus a fenced `csv` block imports correctly.
- The importer still rejects prose-only or malformed content through the
  existing missing-column failure path.
- The importer does not infer category data from prose outside the CSV block.

## Non-goals

- Do not add an import preview/editor for bulk categorize.
- Do not parse arbitrary Markdown tables.
- Do not change category matching, skip counting, group propagation, or exact
  rule learning behavior.

## Validation

- `:core:data:testDebugUnitTest`
- `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- `git diff --check`
- Runtime install/launch was attempted with `personalFullSmsDebug`, but
  `AtharPixelQaApi35` exited before exposing an online ADB device with Windows
  access-violation code `-1073741819`; evidence is under
  `build/qa/bulk-categorize-fenced-csv-import-emulator/`.
