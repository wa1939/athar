package com.athar.core.data.csv

import com.athar.core.data.db.dao.CategoryRuleDao
import com.athar.core.data.db.entity.CategoryRuleEntity
import com.athar.core.domain.model.PatternType
import com.athar.core.domain.repo.CommunityRulesShareResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class CommunityRulesShareExporterTest {

    @Test
    fun `export includes explicit substring rules and omits exact local rules`() = runTest {
        val out = ByteArrayOutputStream()
        val exporter = CommunityRulesShareExporter(
            ruleDao = FakeCategoryRuleDao(
                listOf(
                    rule(
                        id = "always-hemmah",
                        pattern = "hemmah",
                        patternType = PatternType.SUBSTRING,
                        learnedFromUser = true,
                    ),
                    rule(
                        id = "bulk-hemmah branch",
                        pattern = "hemmah branch",
                        patternType = PatternType.EXACT,
                        learnedFromUser = true,
                    ),
                    rule(
                        id = "auto-local-jarir",
                        pattern = "jarir bookstore",
                        patternType = PatternType.EXACT,
                        learnedFromUser = false,
                    ),
                ),
            ),
            clock = FixedClock,
        )

        val result = exporter.exportLearnedRules(out)

        assertThat(result).isEqualTo(CommunityRulesShareResult.Done(rows = 1))
        val rules = Json.parseToJsonElement(out.toString(Charsets.UTF_8))
            .jsonObject
            .getValue("rules")
            .jsonArray
        assertThat(rules).hasSize(1)
        assertThat(rules.single().jsonObject.getValue("pattern").jsonPrimitive.content)
            .isEqualTo("hemmah")
    }

    @Test
    fun `export is empty when only exact local rules exist`() = runTest {
        val out = ByteArrayOutputStream()
        val exporter = CommunityRulesShareExporter(
            ruleDao = FakeCategoryRuleDao(
                listOf(
                    rule(
                        id = "bulk-hemmah",
                        pattern = "hemmah",
                        patternType = PatternType.EXACT,
                        learnedFromUser = true,
                    ),
                ),
            ),
            clock = FixedClock,
        )

        val result = exporter.exportLearnedRules(out)

        assertThat(result).isEqualTo(CommunityRulesShareResult.Empty)
        assertThat(out.size()).isEqualTo(0)
    }

    private class FakeCategoryRuleDao(initial: List<CategoryRuleEntity>) : CategoryRuleDao {
        private val rows = initial.toMutableList()

        override fun observeAll(): Flow<List<CategoryRuleEntity>> = flowOf(rows)
        override suspend fun all(): List<CategoryRuleEntity> = rows.toList()
        override suspend fun upsert(rule: CategoryRuleEntity) {
            rows.removeAll { it.id == rule.id }
            rows += rule
        }
        override suspend fun delete(id: String) {
            rows.removeAll { it.id == id }
        }
        override suspend fun clear() {
            rows.clear()
        }
        override suspend fun clearSystemRules() {
            rows.removeAll { !it.learnedFromUser }
        }
        override suspend fun countSystemRules(): Int =
            rows.count { !it.learnedFromUser && !it.id.startsWith("auto-local-") }
        override suspend fun clearSeedRules() {
            rows.removeAll { !it.learnedFromUser && !it.id.startsWith("auto-local-") }
        }
        override suspend fun deleteAutoLearnedForPattern(pattern: String, patternType: String): Int {
            val before = rows.size
            rows.removeAll {
                it.id.startsWith("auto-local-") &&
                    it.pattern == pattern &&
                    it.patternType == patternType
            }
            return before - rows.size
        }
    }

    private companion object {
        val FixedInstant: Instant = Instant.parse("2026-06-14T00:00:00Z")
        val FixedClock = object : Clock {
            override fun now(): Instant = FixedInstant
        }

        fun rule(
            id: String,
            pattern: String,
            patternType: PatternType,
            learnedFromUser: Boolean,
        ): CategoryRuleEntity = CategoryRuleEntity(
            id = id,
            pattern = pattern,
            patternType = patternType.name,
            categoryId = "cat-home-maintenance",
            priority = 200,
            learnedFromUser = learnedFromUser,
            createdAt = FixedInstant,
        )
    }
}
