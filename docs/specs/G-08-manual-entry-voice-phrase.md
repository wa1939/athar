# G-08 - Manual Entry Voice and Phrase Fill

## Problem

Recent-merchant chips reduce repeated manual entry, but unsupported-bank users still have to tap
amount, merchant, type, and category fields for one-off transactions. The roadmap calls out voice
entry because the fastest manual path is saying or pasting a short phrase such as "spent 50 at
Starbucks" and letting Athar prefill the sheet.

## Decision

Add a small local-only phrase parser to the Add Transaction sheet:

- A quick-entry field accepts typed, pasted, or speech-recognizer text.
- The Android speech recognizer button fills the same parser path; no audio leaves Athar through an
  Athar backend, and the app stores only the resulting transaction after the user saves.
- The parser extracts amount, transaction type, and merchant/counterparty from common English and
  Arabic phrases.
- If the parsed merchant matches an existing manual-entry suggestion, the sheet also reuses that
  suggestion's category, so routine voice entries can be saved with minimal extra taps.
- Failed parses leave existing fields untouched and show an inline error rather than inserting an
  uncertain transaction.

## Acceptance Criteria

- `spent 50 at Starbucks` fills expense, amount `50`, and merchant `Starbucks`.
- `income 2500 from Acme Payroll` fills income, amount `2500`, and merchant `Acme Payroll`.
- Arabic phrases such as `دفعت 35 في كارفور` and `استلمت 1000 من الراتب` parse amount and merchant.
- If the parsed merchant matches a recent suggestion, category and currency-safe amount behavior
  are reused from that suggestion.
- The speech-recognizer result uses the same parser path as typed text.

## Validation

- `:feature:today:testDebugUnitTest`
- `:feature:today:compileDebugKotlin`
- Full static app validation before branch handoff:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime voice-recognizer tap-through remains under QA-01 until an Android device is attached or
  emulator acceleration is available.
