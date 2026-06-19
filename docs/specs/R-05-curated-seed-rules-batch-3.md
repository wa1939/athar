# R-05 - Curated Seed Rules Batch 3

## Problem

The active runtime catalog is `core/data/src/main/assets/seed_rules.json`, not
`seed_merchant_catalog.json`. Two previous reviewed batches promoted 82 safe entries, but the
catalog still had a small set of concrete public merchants that could reduce first-run
uncategorized transactions without relying on private user data.

## Decision

Promote 11 more reviewed priority-90 patterns:

- Subscriptions/software: `github`, `cursor`
- Gym: `fit time`
- Electronics: `xtra`
- Travel/public transport: `duty free`, `riyadh parking`
- Coffee/restaurant: `camel step`, `section b`
- Groceries/clothing/medical: `manuel`, `splash`, `boots`

The batch intentionally skips broad or risky catalog candidates such as `hospital`, `pharmacy`,
`gym`, `pizza`, `airport`, `metro`, and private-person patterns. These rules remain below
hand-curated priority-100 rules and below explicit user-learned rules.

## Acceptance

- `seed_rules.json` increases from 633 to 644 active rules.
- Every new pattern references an existing category id.
- No duplicate normalized seed patterns are introduced.
- The asset test pins every new rule at priority 90.
- Broader low-confidence and private unknown-merchant review remains open.

## Validation

- 2026-06-13: `:core:data:testDebugUnitTest` passed with JDK 17, `--no-daemon`, and
  `--max-workers=1`.
- 2026-06-13: Full JVM test/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- 2026-06-13: Runtime device validation remains blocked because `adb devices -l` returned no
  attached devices.

## Non-goals

- Do not add private-person merchant patterns.
- Do not add broad category words such as `hospital`, `pharmacy`, `gym`, or `pizza`.
- Do not add cloud/user-data collection for categorization.
