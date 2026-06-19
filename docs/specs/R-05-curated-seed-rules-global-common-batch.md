# R-05 - Curated Seed Rules Global Common Batch

## Problem

Athar's seed catalog has strong Saudi and private-audit coverage, but first-run
users in broader markets still get avoidable uncategorized rows for concrete
global public labels that commonly appear in bank notifications and card
statements. These are not private merchant names and do not require committing
raw SMS contents.

## Decision

Promote 15 priority-90 patterns:

- Subscriptions/software: `chatgpt`, `notion`, `canva`, `figma`, `dropbox`,
  `hulu`, `disney+`, `disneyplus`, `paramount+`, `prime video`, `audible`
- Restaurant/delivery: `deliveroo`
- Groceries: `instacart`, `costco`, `whole foods`

This batch intentionally keeps the shared seed conservative. Broad words such
as `market`, `target`, `hotel`, `pharmacy`, `station`, `delivery`, or `store`
remain excluded because they can mean different things across users, countries,
and statement formats.

## Acceptance

- `seed_rules.json` increases from 690 to 705 active rules.
- Every new pattern references an existing category id.
- No duplicate normalized seed patterns are introduced.
- The asset test pins every new rule at priority 90.
- The raw SMS export remains untracked and uncommitted.

## Non-goals

- Do not add private-person, account-tail, or unclear local-fragment patterns.
- Do not add broad category words or ambiguous retailer terms.
- Do not add cloud/user-data collection for categorization.

## Validation

- 2026-06-14: `:core:data:testDebugUnitTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: full JVM test/build/lint stack passed with JDK 17 and
  Android SDK paths set:
  ```powershell
  .\gradlew.bat --console=plain test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon --max-workers=1
  ```
- 2026-06-14: StoreSafe runtime install/launch was attempted on
  `AtharPixelQaApi35`. The `-accel off` boot emitted startup logs but never
  exposed an online ADB device, so install could not run. A default x86_64 boot
  exited with `x86_64 emulation currently requires hardware acceleration`.
  Stale AVD lock cleanup completed afterward.
