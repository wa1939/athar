package com.athar.core.data.repo

import com.athar.core.common.money.Money
import com.athar.core.data.db.dao.RecurringRuleDao
import com.athar.core.data.db.dao.TransactionDao
import com.athar.core.data.db.entity.TransactionEntity
import com.athar.core.domain.model.RecurringSuggestion
import com.athar.core.domain.model.TxType
import com.athar.core.domain.model.specificMerchantKey
import com.athar.core.domain.repo.RecurringSuggestionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Detects recurring patterns in confirmed transaction history:
 * - Same merchant + same amount appearing in 3+ distinct months
 * - Day-of-month within a ±5-day window (handles weekends/holidays shifting bill days)
 * - Excludes anything that already has a matching active rule
 *
 * Looks back 365 days. Output is ranked by occurrence count.
 */
@Singleton
internal class RecurringSuggestionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val ruleDao: RecurringRuleDao,
    private val clock: Clock,
) : RecurringSuggestionRepository {

    override fun observeSuggestions(): Flow<List<RecurringSuggestion>> {
        val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val lookbackStart = LocalDate(today.year - 1, today.monthNumber, 1)
        return transactionDao.observeConfirmedSince(lookbackStart)
            .combine(ruleDao.observeAll()) { txs, existingRules ->
                buildSuggestions(txs, existingKeys = existingRules.map { ruleKey(it.merchantNormalized(), it.amountMinor) }.toSet())
            }
    }

    private fun com.athar.core.data.db.entity.RecurringRuleEntity.merchantNormalized() =
        merchant.lowercase().trim()

    private fun ruleKey(merchantNormalized: String, amountMinor: Long): String =
        "$merchantNormalized@$amountMinor"

    private fun buildSuggestions(
        txs: List<TransactionEntity>,
        existingKeys: Set<String>,
    ): List<RecurringSuggestion> {
        if (txs.size < MIN_OCCURRENCES) return emptyList()
        // Group by (specific merchant, amountMinor, currency) — strict same-amount match.
        val groups: Map<Triple<String, Long, String>, List<TransactionEntity>> =
            txs.mapNotNull { tx ->
                val merchantKey = specificMerchantKey(
                    merchantNormalized = tx.merchantNormalized,
                    merchant = tx.merchant,
                ) ?: return@mapNotNull null
                merchantKey to tx
            }.groupBy(
                keySelector = { (merchantKey, tx) -> Triple(merchantKey, tx.amountMinor, tx.currency) },
                valueTransform = { (_, tx) -> tx },
            )

        return groups.mapNotNull { (key, list) ->
            if (list.size < MIN_OCCURRENCES) return@mapNotNull null
            val (merchantNorm, amountMinor, currency) = key
            if (ruleKey(merchantNorm, amountMinor) in existingKeys) return@mapNotNull null
            val distinctMonths = list.map { YearMonth.of(it.date.year, it.date.monthNumber) }.distinct()
            if (distinctMonths.size < MIN_OCCURRENCES) return@mapNotNull null

            val dayValues = list.map { it.date.dayOfMonth }
            val typicalDom = dayValues.sorted()[dayValues.size / 2] // median
            // Check spread — if days are too scattered, not a recurring pattern
            val spread = dayValues.maxOf { abs(it - typicalDom) }
            if (spread > MAX_DAY_SPREAD) return@mapNotNull null

            val latest = list.maxBy { it.date }
            val typeEnum = runCatching { TxType.valueOf(latest.type) }.getOrNull() ?: return@mapNotNull null
            val nextRun = computeNextRunFromLast(latest.date, typicalDom)
            RecurringSuggestion(
                merchant = latest.merchant,
                merchantNormalized = merchantNorm,
                amount = Money.ofMinor(amountMinor, currency),
                type = typeEnum,
                suggestedCategoryId = unambiguousCategoryId(list),
                occurrenceCount = list.size,
                typicalDayOfMonth = typicalDom,
                lastSeen = latest.date,
                suggestedNextRun = nextRun,
            )
        }
            .sortedWith(compareByDescending<RecurringSuggestion> { it.occurrenceCount }.thenByDescending { it.lastSeen })
            .take(MAX_SUGGESTIONS)
    }

    private fun unambiguousCategoryId(list: List<TransactionEntity>): String? {
        val categoryIds = list.map { tx ->
            tx.categoryId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        }
        return categoryIds.distinct().singleOrNull()
    }

    private fun computeNextRunFromLast(lastSeen: LocalDate, dayOfMonth: Int): LocalDate {
        val java = java.time.LocalDate.of(lastSeen.year, lastSeen.monthNumber, 1).plusMonths(1)
        val safeDay = dayOfMonth.coerceIn(1, java.lengthOfMonth())
        val next = java.withDayOfMonth(safeDay)
        return LocalDate(next.year, next.monthValue, next.dayOfMonth)
    }

    companion object {
        private const val MIN_OCCURRENCES = 3
        private const val MAX_DAY_SPREAD = 5
        private const val MAX_SUGGESTIONS = 20
    }
}
