# R-05 - Curated Seed Rules Batch

## Problem

Athar's active auto-categorization seed is `core/data/src/main/assets/seed_rules.json`.
The repo also has `seed_merchant_catalog.json`, but that catalog is documentation/input data only;
code search shows the runtime seed loader does not read it. That meant known, high-confidence
merchants such as Burger King, Tim Hortons, Netflix, Spotify, Agoda, and National Water could
remain unknown for first-time users even though they already existed in the catalog file.

## Decision

Move a conservative reviewed subset from `seed_merchant_catalog.json` into the active
`seed_rules.json` file:

- Add 67 recognizable merchant patterns across restaurants, coffee, groceries, subscriptions,
  travel, telecom, utilities, education, clothing, gas, medical, gym, and public transport.
- Use priority 90, below hand-curated priority-100 rules and above broad AI-seeded priority-60/80
  rules.
- Avoid broad or ambiguous substring patterns such as `metro`, `pizza`, `bolt`, `hospital`, and
  merchant names already effectively covered by an existing same-category substring.
- Keep the app local-first: no backend, no telemetry, no user data collection.

## Acceptance Criteria

- `seed_rules.json` increases from 551 to 618 active rules.
- New rules point only to category IDs present in `seed_categories.json`.
- Normalized seed patterns stay unique after trimming/lowercasing.
- The curated batch is covered by a JVM unit test so future seed rewrites cannot silently drop it.
- R-05 remains open for future human review of private/unknown merchant exports; this is a first
  safe batch, not the full unknown-merchant review.

## Validation

- `:core:data:test`
- Full static app validation before branch handoff:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime E2E remains under QA-01 until an Android device is attached or emulator acceleration is
  available.
