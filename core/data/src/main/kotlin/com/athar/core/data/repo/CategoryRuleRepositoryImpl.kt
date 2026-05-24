package com.athar.core.data.repo

import com.athar.core.data.db.dao.CategoryRuleDao
import com.athar.core.data.db.entity.CategoryRuleEntity
import com.athar.core.data.mapper.toDomain
import com.athar.core.domain.model.CategoryRule
import com.athar.core.domain.model.PatternType
import com.athar.core.domain.repo.CategoryRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CategoryRuleRepositoryImpl @Inject constructor(
    private val dao: CategoryRuleDao,
    private val clock: Clock,
) : CategoryRuleRepository {

    override fun observeAll(): Flow<List<CategoryRule>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findMatching(merchantNormalized: String): List<CategoryRule> {
        val all = dao.all()
        val name = merchantNormalized.lowercase().trim()
        return all
            .filter { entity ->
                when (entity.patternType) {
                    PatternType.EXACT.name -> entity.pattern.equals(name, ignoreCase = true)
                    PatternType.SUBSTRING.name -> name.contains(entity.pattern.lowercase())
                    PatternType.REGEX.name -> runCatching { Regex(entity.pattern).containsMatchIn(name) }
                        .getOrDefault(false)
                    else -> false
                }
            }
            .sortedByDescending { it.priority }
            .map { it.toDomain() }
    }

    override suspend fun upsert(rule: CategoryRule) {
        dao.upsert(
            CategoryRuleEntity(
                id = rule.id,
                pattern = rule.pattern,
                patternType = rule.patternType.name,
                categoryId = rule.categoryId,
                priority = rule.priority,
                learnedFromUser = rule.learnedFromUser,
                createdAt = rule.createdAt,
            ),
        )
    }

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun learnFromCorrection(
        merchantNormalized: String,
        categoryId: String,
        patternType: PatternType,
    ): CategoryRule {
        val rule = CategoryRule(
            id = UUID.randomUUID().toString(),
            pattern = merchantNormalized,
            patternType = patternType,
            categoryId = categoryId,
            priority = 200, // Master Brief §4.7 learned rules use priority 100; bumping to 200 so they win.
            learnedFromUser = true,
            createdAt = clock.now(),
        )
        upsert(rule)
        return rule
    }
}
