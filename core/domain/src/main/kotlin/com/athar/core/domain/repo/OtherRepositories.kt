package com.athar.core.domain.repo

import com.athar.core.domain.model.Account
import com.athar.core.domain.model.CategoryRule
import com.athar.core.domain.model.InvestmentContribution
import com.athar.core.domain.model.InvestmentPool
import com.athar.core.domain.model.PatternType
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
