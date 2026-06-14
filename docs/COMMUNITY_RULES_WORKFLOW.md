# Community rules workflow (shipped in beta.20)

Athar is local-first by design — the Master Brief §2.2 commits the app to **never sell, share, or transmit financial data without explicit user action**. That commitment isn't compatible with a centralized "we collect your transactions to power AI" backend, even if every data point is anonymized.

But the *value* of that idea — first-time users getting auto-categorization for merchants they've never seen — is real. So we ship the same end result through a different shape: **the community curates the seed; you choose what to contribute, when, via a public review process**.

This is exactly how the 507 AI-extracted rules that landed in beta.17 got there. Now any user can extend the seed the same way.

## The three-step loop

1. **Tag a merchant** in Athar via "Always categorize X as Y" (the dialog that pops when you change a transaction's category). The rule lands in your local DB as a shareable substring rule with `learnedFromUser=true, priority=200`. The new `applyCategoryToMatching` from beta.18 also backfills every existing dismissed/pending row for that merchant.
2. **Export your learned rules** via *Settings → "Help others · share your rules" → Export my rules*. Athar writes a JSON file (default name `athar-shared-rules.json`) like:
   ```json
   {
     "athar_version": "0.1.0-beta.20",
     "submitted_at": "2026-05-27T12:00:00Z",
     "rules": [
       { "pattern": "hemmah", "categoryId": "cat-home-maintenance", "confidence": 0.9 },
       { "pattern": "yaqoot", "categoryId": "cat-utilities", "confidence": 0.9 }
     ]
   }
   ```
   **What's in the file:** explicit substring pattern (lowercase merchant name), categoryId, confidence. **What's NOT:** transaction amounts, dates, raw SMS bodies, accountId references, your name, your phone number, exact bulk-import rules, repeated-history local rules, or anything that could de-anonymize you. The exporter is at `core/data/csv/CommunityRulesShareExporter.kt` — read it to verify.
3. **Open the GitHub issue.** After export, tap *"Open GitHub issue"*. Athar launches the device browser to the public Athar repo with a pre-filled issue title and body. **Attach the JSON file** (GitHub doesn't accept attachments via URL params, so you'll do this as a comment after creating the issue).

## What happens next (maintainer side)

The maintainer reviews each submission against a small checklist:

- [ ] Every `pattern` field looks like a real merchant name (no `ALRAJHIBANK→cat-junk`-style vandalism).
- [ ] Every `categoryId` is one of the supported IDs from `core/data/src/main/assets/seed_categories.json`.
- [ ] `confidence ≥ 0.7` (lower confidences stay in the user's local DB).
- [ ] No duplicate of an existing rule (against the curated seed) — if it duplicates, skip; if it overrides, the curated rule wins.
- [ ] The submission has no PII anywhere in the comment thread.

Accepted rules get appended to `core/data/src/main/assets/seed_rules.json` with `priority: 60` or `priority: 80` (depending on the maintainer's confidence). On the next app release, every user's `RuleSeed.seedIfEmpty()` detects the new rule count and refreshes the system rules. Existing `learnedFromUser=true` rules (the user's own picks) win on collision because they're at `priority: 200`.

## Why this isn't a backend

| Aspect | Centralized server | This approach |
|---|---|---|
| Privacy guarantee | Conditional on you trusting the host | Absolute — file never leaves your device until you tap *Open GitHub issue* |
| Abuse vector | Any user can spam the API | PRs are reviewed by a human |
| Cost | $/month forever | $0 |
| Audit trail | Server logs only the maintainer sees | Public git history — everyone can see every rule's origin |
| Failure mode if the host disappears | Rules stop updating | Nothing — every release ships a snapshot |

## When to submit

After a month or two of use, when you've tagged 20+ merchants. Submitting one or two rules is fine but lower-throughput for the maintainer; a batch of 20–100 is the sweet spot.

## When NOT to submit

- If the rule is highly regional or specific to your household ("My Uncle's Restaurant" → cat-restaurant): keep it local. It won't hurt other users but adds noise to the seed.
- If you're unsure about the category yourself. Better an UNKNOWN than a wrong-class default.
- If the rule came from a bulk CSV import or repeated local history. Those exact rules are intentionally omitted from the export; tap "Always categorize X" only when you want a broader substring pattern to be reviewed for community seed use.

## Verification

In a clean install of a newer beta that includes a merged submission, the new rule should auto-classify the merchant on first SMS arrival. Check `Settings → Activity log` to see the categorization source — it should read `RULE_SUBSTRING` with the new pattern.
