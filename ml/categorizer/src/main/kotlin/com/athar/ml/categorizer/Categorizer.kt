package com.athar.ml.categorizer

import com.athar.core.common.money.Money
import com.athar.core.domain.model.CategorySource
import com.athar.core.domain.model.CategorySuggestion
import com.athar.core.domain.model.PatternType
import com.athar.core.domain.model.TxType

/**
 * Pure-Kotlin auto-categorizer. Master Brief §4.7 / §5.6.
 *
 * Tier order: exact rule → substring rule → regex rule → classifier → unknown.
 * Returns the highest-priority match for the merchant.
 */
fun interface Categorizer {
    suspend fun categorize(
        merchantNormalized: String,
        amount: Money,
        type: TxType,
    ): CategorySuggestion
}

/** Lightweight rule snapshot consumed by the rule engine — independent of Room types. */
data class RuleSpec(
    val id: String,
    val pattern: String,
    val patternType: PatternType,
    val categoryId: String,
    val priority: Int,
)

/** Optional on-device classifier (TFLite). Wire after rule engine ships. */
fun interface MerchantClassifier {
    suspend fun classify(merchantNormalized: String): ClassifierResult?
}

data class ClassifierResult(val categoryId: String, val confidence: Float)

/**
 * Reference rule engine implementation. Pure logic, JVM-testable.
 *
 * Caller passes a current snapshot of [RuleSpec]s; the engine has no opinion about
 * how that snapshot is produced (Flow, suspend get-all, whatever). Keeping the
 * snapshot at the seam keeps this implementation deterministic and stateless.
 */
class RuleEngine(
    private val rules: List<RuleSpec>,
    private val classifier: MerchantClassifier? = null,
) : Categorizer {

    override suspend fun categorize(
        merchantNormalized: String,
        amount: Money,
        type: TxType,
    ): CategorySuggestion {
        val name = merchantNormalized.lowercase().trim()

        // 1. Exact match — confidence 1.0
        rules.asSequence()
            .filter { it.patternType == PatternType.EXACT && it.pattern.equals(name, ignoreCase = true) }
            .sortedByDescending { it.priority }
            .firstOrNull()
            ?.let { return CategorySuggestion(it.categoryId, 1.0f, CategorySource.RULE_EXACT, it.id) }

        // 2. Substring — confidence 0.85
        rules.asSequence()
            .filter { it.patternType == PatternType.SUBSTRING && name.contains(it.pattern.lowercase()) }
            .sortedByDescending { it.priority }
            .firstOrNull()
            ?.let { return CategorySuggestion(it.categoryId, 0.85f, CategorySource.RULE_SUBSTRING, it.id) }

        // 3. Regex — confidence 0.80
        rules.asSequence()
            .filter { it.patternType == PatternType.REGEX && runCatching { Regex(it.pattern).containsMatchIn(name) }.getOrDefault(false) }
            .sortedByDescending { it.priority }
            .firstOrNull()
            ?.let { return CategorySuggestion(it.categoryId, 0.80f, CategorySource.RULE_REGEX, it.id) }

        // 4. Classifier — capped at 0.7
        classifier?.classify(name)?.let { result ->
            return CategorySuggestion(
                categoryId = result.categoryId,
                confidence = result.confidence.coerceAtMost(0.7f),
                source = CategorySource.CLASSIFIER,
                ruleId = null,
            )
        }

        // 5. Unknown
        return CategorySuggestion(null, 0.0f, CategorySource.UNKNOWN, null)
    }
}
