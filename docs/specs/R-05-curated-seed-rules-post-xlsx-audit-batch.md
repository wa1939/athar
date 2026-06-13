# R-05 - Curated Seed Rules Post-XLSX Audit Batch

## Problem

After G-12 XLSX import support, the next lowest-friction improvement is still
auto-categorization coverage: users should not have to manually categorize
common merchant rows after SMS backfill or statement import. The post-R13
private aggregate audit still had 1,138 parsed expense rows with merchant labels
that did not match the public seed rules.

Most remaining labels are private people, account tails, truncated local
fragments, or broad labels that are not safe for a shared seed. A smaller set are
recognizable public/common merchant or platform names.

## Decision

Promote 11 concrete priority-90 patterns:

- Groceries: `marjane`, `migros`, `nova coop`, `seoudi`, `marks & spencer`
- Gas: `knpc`
- Clothing: `cottonil`
- Coffee: `kunest cafe`
- Medical: `okaz phar`
- Telecom: `orange al`
- Other shopping: `aliexpress`

The batch intentionally skips private-person strings, account/card fragments,
unclear local abbreviations, and broad words such as `market`, `station`,
`company`, `pharmacy`, or `restaurant`.

## Acceptance

- `seed_rules.json` increases from 667 to 678 active rules.
- Every new pattern references an existing category id.
- No duplicate normalized seed patterns are introduced.
- The asset test pins every new rule at priority 90.
- The raw SMS export and temporary audit helper are not committed.

## Private Audit Result

Temporary local audit over `All Conversations 2026-05-27 175517.txt`, using the
app-equivalent template order and current seed rules:

- Records: 8,136.
- Parser totals stayed stable: 3,878 successes and 2,393 parsed expenses.
- Missing merchant parsed expenses stayed at 17.
- Categorized parsed expenses increased from 1,238 to 1,265.
- Uncategorized parsed expenses decreased from 1,138 to 1,111.

## Validation

- 2026-06-13: temporary aggregate audit passed locally and was deleted before
  commit.
- 2026-06-13: `:core:data:testDebugUnitTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: full JVM/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug` with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: runtime tap-through was not applicable to this seed-only slice.
  `adb devices -l` returned no attached devices, and `emulator -accel-check`
  reports that the Android Emulator hypervisor driver is not installed.
