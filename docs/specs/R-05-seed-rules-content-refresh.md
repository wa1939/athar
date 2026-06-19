# R-05 Seed Rules Content Refresh

## Problem

`RuleSeed.seedIfEmpty()` refreshed bundled rules only when the count in Room differed from `seed_rules.json`.

That misses metadata-only seed updates. The `patternType` slice keeps the active seed at 729 rows while changing some rows from `SUBSTRING` to `EXACT`, so upgraded installs with 729 existing system rules would skip startup seeding and keep stale broad rules.

## Decision

Compare the bundled seed rule signatures before deciding to skip startup seeding. A signature is:

- `pattern`
- `patternType`
- `categoryId`
- `priority`

If the bundled seed count or any signature differs, clear only bundled seed rules and insert the current asset payload.

The comparison deliberately ignores:

- user-learned rules (`learnedFromUser = true`)
- local auto-learned exact rules (`id` starts with `auto-local-`)

Those rules remain device-local user state and are not part of the shipped public catalog.

## Acceptance

- Matching bundled seed signatures skip seeding.
- Equal-count seed metadata changes trigger a refresh.
- User-learned rules do not force refresh and are not deleted by the seed refresh path.
- `auto-local-*` exact rules do not force refresh and are not deleted by the seed refresh path.
- The active seed still loads with the existing default of `SUBSTRING` when `patternType` is absent.
