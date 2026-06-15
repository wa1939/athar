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
 * Idempotent: only seeds when no system rules exist. User-learned rules
 * (priority > 100, learnedFromUser=true) are never overwritten.
 */
internal class RuleSeed @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CategoryRuleDao,
    private val clock: Clock,
) {

    suspend fun seedIfEmpty() {
        val raw = context.assets.open("seed_rules.json").bufferedReader().use { it.readText() }
        val payload = SeedJson.decodeFromString<SeedPayload>(raw)
        val expected = payload.rules.size
        val current = dao.countSystemRules()
        if (current == expected) {
            Timber.d("System rules up-to-date ($current) — skipping seed.")
            return
        }
        if (current > 0) {
            Timber.i("Refreshing system rules: $current → $expected")
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

    private companion object {
        val SeedJson = Json { ignoreUnknownKeys = true }
    }
}
