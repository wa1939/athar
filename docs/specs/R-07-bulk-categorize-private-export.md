# R-07 - Bulk Categorize Private Export

## Problem

The full bulk-categorize CSV includes `raw_body` because SMS text often carries
the best categorization clue. That is useful for accuracy, but it also means a
user who sends the file to ChatGPT or Claude may disclose raw bank-message text.
The workflow is explicit and local-first, but users need a lower-disclosure
option when merchant, amount, date, type, status, group count, and category
options are enough.

## Decision

- Keep the existing full-context export as the primary path for best AI quality.
- Add a private export mode that writes the same headers and rows but leaves
  `raw_body` blank.
- Keep stable matching columns, category options, grouping, ordering, and
  `category_id` unchanged so the private file imports through the same parser.
- Expose both export actions in Settings: full context and private CSV.

## Acceptance

- Full-context export still includes raw message text when notes are present.
- Private export does not include raw message text in the file body.
- Private export keeps `id`, `stable_key`, `source_ref_id`, merchant fields,
  amount, currency, type, status, date, category options, and blank category id.
- Import behavior is unchanged for both full and private exports.
- Documentation tells users when to choose the private export.

## Non-goals

- Do not redact, summarize, or transform raw SMS text in the full export.
- Do not add a second importer or a different CSV schema.
- Do not call an AI API or store any external service credentials.

## Validation

- `:core:domain:test`
- `:core:data:testDebugUnitTest`
- `:feature:settings:testDebugUnitTest`
- `:feature:settings:compileDebugKotlin`
- `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- `git diff --check`
- Runtime install/launch was attempted with `personalFullSmsDebug`, but
  `AtharPixelQaApi35` exited before exposing an online ADB device with Windows
  access-violation code `-1073741819`; evidence is under
  `build/qa/bulk-categorize-private-export-emulator/`.
