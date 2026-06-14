# R-18 - Private Audit Uncategorized Groups

## Problem

After R-17, the private SMS audit is clean on parser safety: 0 parser failures,
0 failed known-bank messages, and 0 parsed expense rows with missing merchants.
The remaining measurable cleanup is categorization coverage: 1,112 parsed
expense rows still do not match active seed rules.

The previous audit only reported the total uncategorized count plus inactive
catalog matches. Once inactive catalog matches reached zero, the report no
longer showed whether the remaining backlog was concentrated in a few repeated
merchants or scattered across hundreds of one-off labels.

## Decision

Add a redacted `uncategorizedMerchantGroups` section to the gated private audit
report. Each group includes:

- `merchantHash` using the same `merchant:`-prefixed SHA-256/12 style as support
  diagnostics;
- merchant length bucket and script class;
- sample count;
- parser template count breakdown.

The report still writes no raw SMS body, sender, merchant label, amount, account
or card number, transaction row, or note.

## Acceptance

- The private audit report includes the top hashed uncategorized merchant groups
  sorted by sample count.
- The synthetic audit test proves raw merchant labels are not emitted in JSON.
- The existing private audit counts remain stable: 0 parser failures, 0 failed
  known-bank messages, and 0 missing-merchant parsed expenses.
- Raw private SMS content remains untracked and uncommitted.

## Non-goals

- Do not promote ambiguous private/local merchant fragments into shared seed
  rules.
- Do not expose raw merchant names in reports or docs.
- Do not change parser classification or category seed rules in this slice.

## Validation

- `:ingestion:sms-parser:test` passes, including synthetic redaction coverage.
- Gated private SMS export audit passes. Latest module-scoped report shows 7,887
  records, 3,864 parser successes, 4,023 ignored messages, 0 parser failures,
  0 failed known-bank messages, 2,374 parsed expenses, 1,262 categorized
  expenses, 1,112 uncategorized expenses, 0 missing-merchant parsed expenses,
  0 raw bodies written, no inactive catalog matches, no missing-merchant groups,
  and top hashed uncategorized merchant groups with sample counts 55, 27, 12,
  11, and 11.
- Full validation passes with JDK 21:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime install/launch was attempted with the built `personalFullSmsDebug` APK,
  but `AtharPixelQaApi35` stayed `emulator-5554 offline` for the bounded
  software launch (`-no-window -no-snapshot -no-audio -no-boot-anim -gpu
  swiftshader_indirect -accel off`). Cleanup finished with no attached ADB
  device, no emulator/qemu/netsim process, and no AVD locks.
