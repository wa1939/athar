package com.athar.ml.categorizer

import com.athar.core.common.money.Money
import com.athar.core.domain.model.CategorySource
import com.athar.core.domain.model.PatternType
import com.athar.core.domain.model.TxType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RuleEngineTest {

    private fun rule(
        id: String = "r1",
        pattern: String,
        patternType: PatternType,
        categoryId: String = "cat-coffee",
        priority: Int = 100,
    ) = RuleSpec(id, pattern, patternType, categoryId, priority)

    @Test
    fun `exact match wins over substring`() = runTest {
        val engine = RuleEngine(
            listOf(
                rule(id = "exact", pattern = "starbucks 1234", patternType = PatternType.EXACT, categoryId = "exact-cat"),
                rule(id = "sub", pattern = "starbucks", patternType = PatternType.SUBSTRING, categoryId = "sub-cat"),
            ),
        )
        val s = engine.categorize("STARBUCKS 1234", Money.of("10"), TxType.EXPENSE)
        assertThat(s.source).isEqualTo(CategorySource.RULE_EXACT)
        assertThat(s.categoryId).isEqualTo("exact-cat")
        assertThat(s.confidence).isEqualTo(1.0f)
    }

    @Test
    fun `substring matches case-insensitively`() = runTest {
        val engine = RuleEngine(listOf(rule(pattern = "starbucks", patternType = PatternType.SUBSTRING)))
        val s = engine.categorize("Visa STARBUCKS 1234", Money.of("10"), TxType.EXPENSE)
        assertThat(s.source).isEqualTo(CategorySource.RULE_SUBSTRING)
        assertThat(s.categoryId).isEqualTo("cat-coffee")
    }

    @Test
    fun `regex matches when substring does not`() = runTest {
        val engine = RuleEngine(listOf(rule(pattern = """\bnoon\b|\bamazon\.sa\b""", patternType = PatternType.REGEX)))
        val s = engine.categorize("noon kuwait order", Money.of("10"), TxType.EXPENSE)
        assertThat(s.source).isEqualTo(CategorySource.RULE_REGEX)
    }

    @Test
    fun `unknown when no rule matches and no classifier`() = runTest {
        val engine = RuleEngine(emptyList())
        val s = engine.categorize("mystery merchant", Money.of("10"), TxType.EXPENSE)
        assertThat(s.source).isEqualTo(CategorySource.UNKNOWN)
        assertThat(s.categoryId).isNull()
        assertThat(s.confidence).isEqualTo(0.0f)
    }

    @Test
    fun `classifier fallback respects 0_7 cap`() = runTest {
        val engine = RuleEngine(
            rules = emptyList(),
            classifier = MerchantClassifier { ClassifierResult("cat-X", confidence = 0.95f) },
        )
        val s = engine.categorize("mystery", Money.of("10"), TxType.EXPENSE)
        assertThat(s.source).isEqualTo(CategorySource.CLASSIFIER)
        assertThat(s.confidence).isEqualTo(0.7f)
    }

    @Test
    fun `priority breaks substring ties`() = runTest {
        val engine = RuleEngine(
            listOf(
                rule(id = "low", pattern = "starbucks", patternType = PatternType.SUBSTRING, categoryId = "low", priority = 50),
                rule(id = "high", pattern = "starbucks", patternType = PatternType.SUBSTRING, categoryId = "high", priority = 200),
            ),
        )
        val s = engine.categorize("starbucks downtown", Money.of("10"), TxType.EXPENSE)
        assertThat(s.categoryId).isEqualTo("high")
    }
}
