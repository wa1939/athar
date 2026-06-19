# R-07 - Bulk Categorize Category Value Normalization

## Problem

The bulk-categorize CSV includes `category_options` values formatted as
`cat-id=English / Arabic`. The prompt asks external AI or a human to fill only
the id into `category_id`, but real edits often copy the full option string or
paste a display label such as `Coffee / قهوة`. Before this slice, those valid
human-readable choices were treated as unknown categories, creating unnecessary
skip rows and repeat repair passes.

## Decision

- Keep the existing CSV schema and import contract.
- Resolve `category_id` values from:
  - exact category ids,
  - case-variant category ids,
  - full copied option strings such as `cat-coffee=Coffee / قهوة`,
  - bilingual display labels split from `English / Arabic`,
  - existing English or Arabic category names.
- Continue validating the resolved category against the matched transaction
  type before applying it, propagating it to blank group peers, or training an
  exact local rule.

## Acceptance

- A copied `category_options` value in `category_id` imports successfully.
- A copied bilingual display label imports successfully when it maps to one
  active category.
- Case-variant category ids import successfully.
- Unknown categories and wrong expense/income category types still skip with the
  existing reason counters.
- No raw SMS text is added to docs, seeds, or committed fixtures.

## Non-goals

- Do not change the bulk CSV columns.
- Do not infer categories that are not present in the active category table.
- Do not make broad substring rules from bulk imports.
- Do not add inline AI APIs, telemetry, or cloud categorization.

## Validation

- Focused data/domain tests passed:
  `:core:data:testDebugUnitTest :core:domain:test --tests "com.athar.core.domain.repo.MerchantBulkAiPromptTest"`.
- Forced gated private SMS audit passed with redacted aggregate output only:
  7,887 records, 3,864 parser successes, 4,023 ignored, 0 parser failures,
  0 failed known-bank messages, 2,374 parsed expenses, 1,276 categorized
  expenses, 1,098 uncategorized expenses, 0 missing-merchant parsed expenses,
  and `rawBodiesWritten = 0`.
- `git diff --check` passed.
- Full JVM test/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime `personalFullSmsDebug` install/launch was attempted with the built APK.
  `emulator -accel-check` reported that the Android Emulator hypervisor driver
  is not installed, and a bounded hidden `AtharPixelQaApi35` software launch
  stayed `emulator-5554 offline`, so install, screenshot, UI dump, and logcat
  capture could not run. Evidence was written to
  `build/qa/bulk-categorize-category-value-normalization-emulator/`; cleanup
  finished with no attached ADB device, no emulator/qemu/netsim process, and no
  AVD lock files.
