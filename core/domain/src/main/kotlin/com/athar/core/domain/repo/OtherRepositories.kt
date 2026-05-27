package com.athar.core.domain.repo

import com.athar.core.common.money.Money
import com.athar.core.domain.model.Account
import com.athar.core.domain.model.AccountBalance
import com.athar.core.domain.model.CategoryRule
import com.athar.core.domain.model.NetWorth
import com.athar.core.domain.model.InvestmentContribution
import com.athar.core.domain.model.InvestmentPool
import com.athar.core.domain.model.PatternType
import com.athar.core.domain.model.RecurringRule
import com.athar.core.domain.model.RecurringSuggestion
import com.athar.core.domain.model.WishlistItem
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeActive(): Flow<List<Account>>
    fun observeAll(includeArchived: Boolean = true): Flow<List<Account>>
    suspend fun get(id: String): Account?
    suspend fun upsert(account: Account)
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun delete(id: String)

    /**
     * Live net worth for the user's chosen display currency. Combines active accounts
     * with CONFIRMED transaction balances. Reads displayCurrency from prefs implicitly
     * via the call site — pass it in so this stays pure.
     */
    fun observeNetWorth(displayCurrency: String): Flow<NetWorth>

    /**
     * Computed balance per active account (openingBalance + confirmed Σ).
     * Use for the Accounts screen list rows.
     */
    fun observeBalances(): Flow<List<AccountBalance>>

    /**
     * "Match my bank balance" — instead of letting the user edit the opening balance
     * (which silently rewrites history and is fragile across currency changes), this
     * inserts one manual adjustment transaction so the computed running balance equals
     * [target]. delta = target − current; INCOME if positive, EXPENSE if negative.
     *
     * @param label  the merchant label, localized by the caller (e.g., "تسوية يدوية" / "Manual adjustment").
     * @param note   optional free-text reason the user typed in the sheet.
     * @return [ReconcileResult.Done] with the adjustment amount in minor units + new tx id,
     *         or [ReconcileResult.Failed] with a human-readable reason.
     */
    suspend fun reconcile(
        accountId: String,
        target: Money,
        label: String,
        note: String?,
    ): ReconcileResult
}

/** Outcome of [AccountRepository.reconcile]. */
sealed interface ReconcileResult {
    data class Done(val adjustmentMinor: Long, val txId: String) : ReconcileResult
    data class Failed(val reason: String) : ReconcileResult
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

/**
 * Analyzes the user's confirmed transaction history and surfaces pattern-detected
 * candidates that look like recurring transactions (Netflix charged 60 ر.س on the
 * 15th of each month for 3+ months, salary deposited on the 25th, etc.).
 *
 * Crucially, suggestions are **proposals** — the user always confirms before they
 * become a [RecurringRule]. Mirrors how SMS-parsed transactions are PENDING until
 * the user taps confirm.
 */
interface RecurringSuggestionRepository {
    fun observeSuggestions(): Flow<List<RecurringSuggestion>>
}
