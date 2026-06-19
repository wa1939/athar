package com.athar.core.data.seed

import android.content.Context
import com.athar.core.data.db.dao.CategoryRuleDao
import com.athar.core.data.db.entity.CategoryRuleEntity
import com.athar.core.domain.model.PatternType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Seeds the merchant categorization rules from `assets/seed_rules.json` on first run.
 * Master Brief §4.7 / Backlog S-09.
 *
 * Idempotent: only seeds when bundled system rules are missing or stale.
 * User-learned rules and local auto-learned rules are never overwritten.
 */
internal class RuleSeed @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CategoryRuleDao,
    private val clock: Clock,
) {

    suspend fun seedIfEmpty() {
        val raw = context.assets.open("seed_rules.json").bufferedReader().use { it.readText() }
        val payload = SeedJson.decodeFromString<SeedPayload>(raw)
        val expectedRules = payload.rules.map { it.toSeedRuleSignature() }
        val currentRules = dao.all()
        val currentSeedRules = currentRules.filter { it.isBundledSeedRule() }
        if (!seedRulesNeedRefresh(currentRules, expectedRules)) {
            Timber.d("System rules up-to-date (${currentSeedRules.size}) — skipping seed.")
            return
        }
        if (currentSeedRules.isNotEmpty()) {
            Timber.i("Refreshing system rules: ${currentSeedRules.size} → ${expectedRules.size}")
            dao.clearSeedRules()
        }
        val now = clock.now()
        val entities = payload.rules.map { dto ->
            CategoryRuleEntity(
                id = UUID.randomUUID().toString(),
                pattern = dto.pattern,
                patternType = (dto.patternType ?: PatternType.SUBSTRING).name,
                categoryId = dto.categoryId,
                priority = dto.priority,
                learnedFromUser = false,
                createdAt = now,
            )
        }
        entities.forEach { dao.upsert(it) }
        Timber.i("Seeded ${entities.size} categorization rules.")
    }

    @Serializable private data class SeedPayload(val rules: List<SeedRule>)
    @Serializable private data class SeedRule(
        val pattern: String,
        val categoryId: String,
        val priority: Int,
        val patternType: PatternType? = null,
    )

    private fun SeedRule.toSeedRuleSignature(): SeedRuleSignature = SeedRuleSignature(
        pattern = pattern,
        patternType = (patternType ?: PatternType.SUBSTRING).name,
        categoryId = categoryId,
        priority = priority,
    )

    private companion object {
        val SeedJson = Json { ignoreUnknownKeys = true }
    }
}

internal data class SeedRuleSignature(
    val pattern: String,
    val patternType: String,
    val categoryId: String,
    val priority: Int,
)

internal fun seedRulesNeedRefresh(
    currentRules: List<CategoryRuleEntity>,
    expectedRules: List<SeedRuleSignature>,
): Boolean {
    val currentSeedRules = currentRules.filter { it.isBundledSeedRule() }
    if (currentSeedRules.size != expectedRules.size) return true

    val currentSignatures = currentSeedRules.map { it.toSeedRuleSignature() }.toSet()
    val expectedSignatures = expectedRules.toSet()
    return currentSignatures != expectedSignatures
}

internal fun CategoryRuleEntity.isBundledSeedRule(): Boolean =
    !learnedFromUser && !id.startsWith(AutoLocalRuleIdPrefix)

internal fun CategoryRuleEntity.toSeedRuleSignature(): SeedRuleSignature = SeedRuleSignature(
    pattern = pattern,
    patternType = patternType,
    categoryId = categoryId,
    priority = priority,
)

private const val AutoLocalRuleIdPrefix = "auto-local-"
