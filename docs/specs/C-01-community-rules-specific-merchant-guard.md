# C-01 Community Rules Specific-Merchant Guard

## Problem

Community-rule sharing is intentionally user-driven: only rules created through "Always categorize X as Y" should be exported for maintainer review. After the edit-sheet guard, generic labels such as `payment`, `cash`, `bank`, `كاش`, and `دفع` no longer create new explicit substring rules. Older local databases, however, can still contain legacy learned substring rules for those generic labels.

If those legacy rows are exported, they can become noisy public seed proposals and make the maintainer workflow less safe.

## Decision

Reuse the shared `isSpecificMerchantKey()` guard inside `CommunityRulesShareExporter` after normalizing each rule pattern to lowercase/trimmed text. The exporter now includes a row only when it is:

- `learnedFromUser=true`
- `patternType=SUBSTRING`
- A specific merchant key according to the shared domain guard

Exact local rules from bulk import or repeated-history learning remain excluded as before.

## Acceptance

- A real explicit merchant substring rule still exports with the normalized pattern, categoryId, and confidence.
- Generic English/Arabic learned substring rules are skipped.
- If all learned substring rules are generic, the export result is `Empty` and nothing is written.
- Existing privacy guarantees remain unchanged: no amounts, dates, raw SMS bodies, account IDs, notes, phone numbers, or transaction rows leave the device.
