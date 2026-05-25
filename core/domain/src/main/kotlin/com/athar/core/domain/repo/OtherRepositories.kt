package com.athar.core.domain.repo

import com.athar.core.domain.model.Account
import com.athar.core.domain.model.CategoryRule
import com.athar.core.domain.model.InvestmentContribution
import com.athar.core.domain.model.InvestmentPool
import com.athar.core.domain.model.PatternType
import com.athar.core.domain.model.RecurringRule
import com.athar.core.domain.model.WishlistItem
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeActive(): Flow<List<Account>>
    suspend fun upsert(account: Account)
    suspend fun delete(id: String)
}

interface CategoryRuleRepository {
    fun observeAll(): Flow<List<CategoryRule>>
    suspend fun findMatching(merchantNormalized: String): List<CategoryRule>
    suspend fun upsert(rule: CategoryRule)
    suspend fun delete(id: String)
    suspend fun learnFromCorrection(
        merchantNormalized: String,
        categoryId: String,
        patternType: PatternType = PatternType.SUBSTRING,
    ): CategoryRule
}

interface WishlistRepository {
    fun observeAll(): Flow<List<WishlistItem>>
    suspend fun upsert(item: WishlistItem)
    suspend fun delete(id: String)
}

interface InvestmentRepository {
    fun observePoolsWithContributions(): Flow<Map<InvestmentPool, List<InvestmentContribution>>>
    suspend fun upsertPool(pool: InvestmentPool)
    suspend fun upsertContribution(contribution: InvestmentContribution)
    suspend fun deletePool(id: String)
    suspend fun deleteContribution(id: String)
}

/**
 * Manages user-defined recurring rules (rent, salary, subscriptions). Materialization
 * into PENDING transactions happens via [materializeDue] — invoke from a periodic
 * WorkManager job or from a settings 'Run now' button (v1 ships the manual trigger;
 * WorkManager wiring can follow without changing this interface).
 */
interface RecurringRuleRepository {
    fun observeAll(includeInactive: Boolean = false): Flow<List<RecurringRule>>
    suspend fun get(id: String): RecurringRule?
    suspend fun upsert(rule: RecurringRule)
    suspend fun delete(id: String)
    suspend fun setActive(id: String, active: Boolean)

    /**
     * Walks every active rule and creates a PENDING transaction for any whose
     * [RecurringRule.nextRunDate] is on or before [today]. Returns the count of
     * materialized transactions. Idempotent — multiple calls in the same day
     * don't double-create because [RecurringRule.lastRunDate] gates each rule.
     */
    suspend fun materializeDue(today: kotlinx.datetime.LocalDate): Int
}
