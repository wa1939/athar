package com.athar.core.domain.repo

import java.io.OutputStream

/**
 * Exports the user's `learnedFromUser = true` `CategoryRule` rows as a JSON document
 * intended for submission to the public Athar GitHub repository as a community-rules
 * proposal. The maintainer reviews the proposed rules and, if accepted, merges them
 * into the bundled `seed_rules.json` so every user picks them up via [RuleSeed] on
 * the next app release.
 *
 * Privacy contract (Master Brief §2.2): the exported JSON contains ONLY merchant
 * pattern + categoryId + confidence. No amounts, no dates, no raw SMS bodies, no
 * accountId references. The user is the one who explicitly created each of these
 * patterns via "Always categorize X as Y" — they're not raw transaction data.
 */
interface CommunityRulesShareTrigger {
    suspend fun exportLearnedRules(out: OutputStream): CommunityRulesShareResult
}

sealed interface CommunityRulesShareResult {
    /** Wrote [rows] rule entries. */
    data class Done(val rows: Int) : CommunityRulesShareResult

    /** User hasn't picked "Always" on anything yet — nothing to share. */
    data object Empty : CommunityRulesShareResult

    data class Failed(val reason: String) : CommunityRulesShareResult
}
