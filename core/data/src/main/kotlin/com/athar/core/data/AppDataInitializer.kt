package com.athar.core.data

import com.athar.core.data.seed.AccountSeed
import com.athar.core.data.seed.CategorySeed
import com.athar.core.data.seed.RuleSeed
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point the app calls on cold start to prime first-run data.
 *
 * Idempotent — safe to call on every launch.
 */
@Singleton
class AppDataInitializer @Inject internal constructor(
    private val categorySeed: CategorySeed,
    private val accountSeed: AccountSeed,
    private val ruleSeed: RuleSeed,
) {
    suspend fun initialize() {
        categorySeed.seedIfEmpty()
        accountSeed.seedManualIfMissing()
        ruleSeed.seedIfEmpty()
    }
}
