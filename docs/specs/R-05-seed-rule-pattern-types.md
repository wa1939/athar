# R-05 Seed Rule Pattern Types

## Problem

The active bundled seed catalog in `seed_rules.json` previously loaded every row as a substring rule. That is right for concrete merchants such as `starbucks`, but it is too broad for normalized parser labels that begin with generic words, such as `cash deposit` or `bank fees`.

Those labels are useful because parser output can use them directly, but as substring rules they can also match unrelated merchant strings and they fail the shared specific-merchant guard.

## Decision

Allow each seed row to set an optional `patternType`. Missing values keep the existing behavior and load as `SUBSTRING`; rows marked `"patternType": "EXACT"` load as exact matches.

This slice marks the current generic-prefix label seeds as exact:

- `cash deposit`
- `bank fees`
- `كاش باك بطاقة ائتمانية`

`SeedRulesAssetTest` now rejects any future `SUBSTRING` seed whose pattern does not pass the shared `isSpecificMerchantKey()` guard. The AI seed merge script preserves existing `patternType` fields so rerunning it does not silently broaden exact labels.

## Acceptance

- Existing seed files without `patternType` still load as substring rules.
- Exact seed rows load into `CategoryRuleEntity.patternType = EXACT`.
- The active seed still has 729 rows and all category references remain valid.
- Generic English/Arabic labels cannot be added as substring seed rows without failing the asset test.
- Future AI seed merges preserve existing `patternType` values.
