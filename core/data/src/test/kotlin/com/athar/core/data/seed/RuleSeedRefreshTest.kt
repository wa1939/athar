package com.athar.core.data.seed

import com.athar.core.data.db.entity.CategoryRuleEntity
import com.athar.core.domain.model.PatternType
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class RuleSeedRefreshTest {

    @Test
    fun `refreshes when count matches but pattern type changed`() {
        val current = listOf(
            rule(
                pattern = "cash deposit",
                patternType = PatternType.SUBSTRING,
                categoryId = "cat-other-income",
            ),
        )
        val expected = listOf(
            signature(
                pattern = "cash deposit",
                patternType = PatternType.EXACT,
                categoryId = "cat-other-income",
            ),
        )

        assertThat(seedRulesNeedRefresh(current, expected)).isTrue()
    }

    @Test
    fun `skips when bundled seed signatures match`() {
        val current = listOf(
            rule(
                pattern = "jarir",
                patternType = PatternType.SUBSTRING,
                categoryId = "cat-education",
                priority = 90,
            ),
        )
        val expected = listOf(
            signature(
                pattern = "jarir",
                patternType = PatternType.SUBSTRING,
                categoryId = "cat-education",
                priority = 90,
            ),
        )

        assertThat(seedRulesNeedRefresh(current, expected)).isFalse()
    }

    @Test
    fun `ignores user learned and auto local rules when comparing bundled seeds`() {
        val current = listOf(
            rule(
                id = "seed-jarir",
                pattern = "jarir",
                patternType = PatternType.SUBSTRING,
                categoryId = "cat-education",
                priority = 90,
            ),
            rule(
                id = "explicit-payment",
                pattern = "payment",
                patternType = PatternType.SUBSTRING,
                categoryId = "cat-other-expense",
                learnedFromUser = true,
            ),
            rule(
                id = "auto-local-starbucks",
                pattern = "starbucks",
                patternType = PatternType.EXACT,
                categoryId = "cat-coffee",
                priority = 150,
            ),
        )
        val expected = listOf(
            signature(
                pattern = "jarir",
                patternType = PatternType.SUBSTRING,
                categoryId = "cat-education",
                priority = 90,
            ),
        )

        assertThat(seedRulesNeedRefresh(current, expected)).isFalse()
    }

    @Test
    fun `refreshes when bundled seed count differs`() {
        val current = listOf(
            rule(
                pattern = "jarir",
                patternType = PatternType.SUBSTRING,
                categoryId = "cat-education",
            ),
            rule(
                pattern = "extra seed",
                patternType = PatternType.EXACT,
                categoryId = "cat-other-expense",
            ),
        )
        val expected = listOf(
            signature(
                pattern = "jarir",
                patternType = PatternType.SUBSTRING,
                categoryId = "cat-education",
            ),
        )

        assertThat(seedRulesNeedRefresh(current, expected)).isTrue()
    }

    private fun signature(
        pattern: String,
        patternType: PatternType,
        categoryId: String,
        priority: Int = 100,
    ): SeedRuleSignature = SeedRuleSignature(
        pattern = pattern,
        patternType = patternType.name,
        categoryId = categoryId,
        priority = priority,
    )

    private fun rule(
        id: String = "seed-rule",
        pattern: String,
        patternType: PatternType,
        categoryId: String,
        priority: Int = 100,
        learnedFromUser: Boolean = false,
    ): CategoryRuleEntity = CategoryRuleEntity(
        id = id,
        pattern = pattern,
        patternType = patternType.name,
        categoryId = categoryId,
        priority = priority,
        learnedFromUser = learnedFromUser,
        createdAt = Instant.parse("2026-06-15T00:00:00Z"),
    )
}
