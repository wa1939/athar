package com.athar.core.data.csv

import com.athar.core.data.db.dao.CategoryRuleDao
import com.athar.core.domain.model.PatternType
import com.athar.core.domain.repo.CommunityRulesShareResult
import com.athar.core.domain.repo.CommunityRulesShareTrigger
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the user's explicit `learnedFromUser = true` substring `CategoryRule`
 * rows to a JSON file intended for submission to the public Athar GitHub
 * repository as a community-rules proposal (Issue #5a follow-on, ships in
 * beta.20).
 *
 * Privacy contract: the JSON contains ONLY (pattern, categoryId, confidence) tuples.
 * No transaction data, no amounts, no merchant raw SMS bodies, no account IDs, no PII.
 * Each tuple is a substring pattern the user *explicitly created* by tapping
 * "Always categorize X as Y" — these are user-authored rules, not exact local
 * bulk-import or history-derived rules.
 */
@Singleton
internal class CommunityRulesShareExporter @Inject constructor(
    private val ruleDao: CategoryRuleDao,
    private val clock: Clock,
) : CommunityRulesShareTrigger {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    override suspend fun exportLearnedRules(out: OutputStream): CommunityRulesShareResult =
        runCatching {
            val learned = ruleDao.observeAll().let { flow ->
                // Use the suspend `all()` instead to avoid hanging on the Flow.
                ruleDao.all()
            }.filter {
                it.learnedFromUser && it.patternType == PatternType.SUBSTRING.name
            }
            if (learned.isEmpty()) return CommunityRulesShareResult.Empty

            val payload = CommunityRulesPayload(
                atharVersion = BuildVersion,
                submittedAt = clock.now().toString(),
                rules = learned.map { entity ->
                    SharedRule(
                        pattern = entity.pattern.lowercase().trim(),
                        categoryId = entity.categoryId,
                        confidence = 0.9,
                    )
                }.distinctBy { it.pattern to it.categoryId },
            )
            OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                writer.write(json.encodeToString(payload))
                writer.flush()
            }
            payload.rules.size
        }.fold(
            onSuccess = {
                Timber.i("Community-rules export: %d rules written", it)
                CommunityRulesShareResult.Done(it)
            },
            onFailure = {
                Timber.e(it, "Community-rules export failed")
                CommunityRulesShareResult.Failed(it.message ?: it::class.simpleName.orEmpty())
            },
        )

    private companion object {
        // The maintainer reads this when reviewing — lets them see which release the
        // rules came from. Hard-coding is fine; this exporter only ships in beta.20+.
        const val BuildVersion = "0.1.0-beta.20"
    }
}

@Serializable
private data class CommunityRulesPayload(
    @SerialName("athar_version") val atharVersion: String,
    @SerialName("submitted_at") val submittedAt: String,
    val rules: List<SharedRule>,
)

@Serializable
private data class SharedRule(
    val pattern: String,
    @SerialName("categoryId") val categoryId: String,
    val confidence: Double,
)
