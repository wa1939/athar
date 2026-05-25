# ADR-008: SMS ingestion fail-safe — never auto-dismiss a parsed transaction

- **Status:** Accepted
- **Date:** 2026-05-25
- **Supersedes:** —
- **Superseded by:** —

## Context

Athar's SMS ingestion pipeline (`app/src/main/kotlin/com/athar/ingestion/SmsIngestionPipeline.kt`) takes a `RawIngestEvent`, runs it through `IgnorePatterns` (filter out OTPs / promos / KYC), then bank-specific regex templates (extract amount, merchant, type, date), then a merchant→category rule lookup (`CategoryRuleRepository.findMatching`). The output of that lookup is a `CategorySuggestion(categoryId?, confidence, source)`.

The pipeline then decides the transaction's initial status. Until beta.15 the policy was:

```kotlin
val initialStatus = when {
    isSelfTransfer -> TxStatus.PENDING
    suggestion.categoryId != null -> TxStatus.CONFIRMED
    confidence < AUTO_DISMISS_THRESHOLD -> TxStatus.DISMISSED  // ← the bug
    else -> TxStatus.PENDING
}
private const val AUTO_DISMISS_THRESHOLD = 0.50f
```

The intent of the `AUTO_DISMISS` branch was to keep the pending tray clean: parser noise (a half-recognized SMS that *might* be a transaction but the categorizer is unsure) shouldn't pollute the daily review queue.

The cost of that intent became clear in production user testing (beta.11–beta.14): when a user backfilled 3 months of AlRajhi SMS, 7,730 messages were "ignored" (parser refused) and 2,348 parsed successfully — but a meaningful chunk of those 2,348 ended up `DISMISSED` because the merchant ("Dukkan Al Wadi", "HAMAD ALS…", neighborhood vendors not in the merchant catalog) failed to match any category rule above the 0.50 threshold. The user reported:

> "why you dismiss or not record the unknown? anything that you don't know you should record it. If the user didn't categorize or delete it, record it in 'Other' or 'unknown' so they don't lose money transaction recording."

This is correct UX thinking. The user's money left their account. The fact that **Athar** is uncertain about the category does not change that. If we silently hide the transaction, the user's running totals are wrong, their Today net-flow is wrong, their net worth is wrong, and they cannot trust the app as a system of record.

This violates Master Brief §2.2 ("Three things Athar will never do"): we built an app to *replace* an Excel workbook. An Excel workbook never deletes a row because it was confused about what it was — it sits there in plaintext until the user decides.

## Decision

Auto-dismiss is removed entirely from the ingestion pipeline. The new policy:

```kotlin
val initialStatus = when {
    isSelfTransfer -> TxStatus.PENDING                            // user decides savings vs outgoing
    suggestion.categoryId != null -> TxStatus.CONFIRMED           // categorizer matched → trust it
    else -> TxStatus.PENDING                                       // unknown → user decides
}
// DISMISSED is no longer reachable from automated ingestion.
```

The `DISMISSED` status remains in the data model and continues to be set by **explicit user action only** — swipe-to-dismiss on the pending tray, the bulk-dismiss actions on Today, the Edit-Transaction sheet's delete button. Spam-shaped messages still don't reach this layer — they're filtered out earlier by `IgnorePatterns` and tagged "ignored" in the SMS audit log (a separate table from transactions). The audit log is for messages we couldn't or wouldn't parse; the transactions table is for things that successfully became transactions.

This is the **fail-safe principle**: if the pipeline is uncertain, it errs on the side of *recording the transaction and asking the user* rather than discarding. The user can always swipe-dismiss noise after the fact; the user cannot recover data that the app silently deleted.

### Recovery for existing users

Users who installed any build before beta.15 already have a population of auto-dismissed rows in their local DB. We can't tell those rows apart from rows the user explicitly dismissed (the `status` field doesn't carry provenance), and we don't want to retroactively re-show transactions the user genuinely meant to dismiss.

So we ship a one-tap **opt-in** recovery action: `Settings → "Recover dismissed transactions / استرجاع الحركات المتجاهَلة"`. Tapping it runs `UPDATE transactions SET status = 'PENDING' WHERE status = 'DISMISSED'` and logs the bulk action to the Activity Log. The user is expected to review the result in History/Today and re-dismiss any actual noise. This is acceptable because:

1. The recovery action is opt-in — users who don't tap it lose nothing.
2. Activity Log records the migration with a count, so it's auditable + reversible if needed.
3. The realistic ratio of *auto-dismissed real transactions* (lost spending) to *user-dismissed noise* (false-positive SMS) is heavily skewed toward the former, especially for the user's first big SMS backfill.

## Consequences

### Positive

- **No silent data loss.** Every SMS that parses as a transaction is reachable from the History view.
- **Trust.** The user knows that "if I spent money, I will see it." That trust is foundational for the app to replace their Excel.
- **Simpler mental model.** Three statuses with clear semantics: CONFIRMED = system trusts it, PENDING = user must decide, DISMISSED = user explicitly chose to discard. Status doesn't conflate "system noise heuristic" with "user intent."
- **The merchant catalog gets fed.** Every PENDING transaction the user category-corrects becomes a `CategoryRule.learnFromCorrection` call, which means the next same-merchant transaction auto-confirms. The pipeline *gets smarter over time* without the developer adding rules.

### Negative

- **Pending tray grows.** A user with 90 days of bank SMS and a sparse merchant catalog may see hundreds of pending transactions on first backfill. This is mitigated by:
  - The bulk-action bar on Today (Confirm-confident / Dismiss-low-confidence / Dismiss-all) — the user can clear noise in one tap if they explicitly choose to.
  - The pending banner CTA on Today links to the History view where the user can search/filter/edit in bulk.
  - Future ticket: in-app AI triage that auto-categorizes pending transactions against the user's own correction history — see `docs/AI_SMS_TRIAGE_PROMPT.md` for the external version.
- **Activity Log noise.** Recovery action emits one bulk entry per user click. Tolerable — we don't expect users to recover repeatedly.

### Residual debt

- The pending tray UX needs more sorting/grouping affordance once a user has 200+ pending. Currently sorted by createdAt DESC; useful additions:
  - Group by merchant (collapse repeats into "Starbucks × 24 — categorize all?")
  - Filter by amount range (hide micro-transactions if the user wants to focus on big-ticket review first)
- The category rule confidence numeric is now decorative — we keep it for sorting and UI hints but it no longer drives auto-classification.

## Alternatives considered

| Approach | Why rejected |
|---|---|
| Keep auto-dismiss but lower threshold to 0.30 | Same bug, smaller magnitude. The threshold is unprincipled — there's no number that's "safe" because the cost of false-positive-dismissal is permanent data loss. |
| Auto-tag unknown transactions to `cat-other-expense` then mark CONFIRMED | Looks tidy but masks the unknown. User wouldn't see a "review me" cue and would accept "Other" as their reality. Categorization quality silently degrades over time. |
| Add a 4th status `UNREVIEWED` between PENDING and DISMISSED | More states, more UI, more chances to get lost. PENDING already means "user must act"; adding gradations doesn't help the user. |
| Auto-dismiss BUT show a top-of-Today banner "%d auto-dismissed today" | We shipped a version of this in beta.14 (dust-color dismissed-today banner) — but it only catches dismissals from today, and the existing dismissed records were already invisible. The banner is now redundant for new builds; we keep it as a safety net for the recovery flow and to surface any future bulk-dismiss-low-confidence user action. |
| Inject the categorizer into a notification with "categorize?" action | Notifications are intrusive; the brief explicitly bans "guilt-trip notifications" (§2.2). PENDING tray + banner are the right surface for "you have work to do." |

## Why this matters for future work

- **Adding new banks / regions:** new parser templates will inevitably misclassify merchants Athar has never seen. The fail-safe means a poorly-tuned parser still records the transaction. The user catches the mis-categorization on review, fixes it, and `learnFromCorrection` makes the next instance auto-categorize. The system improves with use.
- **Adding new ingestion sources** (notifications, Open Banking, manual import): the same `RawIngestEvent` → pipeline → status decision flows through. The fail-safe applies uniformly.
- **Don't bring auto-dismiss back.** If a future contributor proposes "auto-dismiss low-confidence to clean the tray," send them here. The right answer is always: add a *user action* to dismiss in bulk, never an *automatic* one.
