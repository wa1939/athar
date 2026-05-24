package com.athar.core.data.seed

import com.athar.core.data.db.dao.AccountDao
import com.athar.core.data.db.entity.AccountEntity
import com.athar.core.domain.model.MANUAL_ACCOUNT_ID
import kotlinx.datetime.Clock
import javax.inject.Inject

/**
 * Ensures the synthetic "manual" account exists so foreign-key constraints on
 * manually-entered transactions are satisfied. Idempotent.
 *
 * Replaced once real account CRUD lands (S-20). The id is stable so existing manual
 * transactions keep referring to it after that migration.
 */
internal class AccountSeed @Inject constructor(
    private val dao: AccountDao,
    private val clock: Clock,
) {

    suspend fun seedManualIfMissing() {
        dao.upsert(
            AccountEntity(
                id = MANUAL_ACCOUNT_ID,
                name = "يدوي",
                type = "CASH",
                currency = "SAR",
                smsSenders = "[]",
                active = true,
                createdAt = clock.now(),
            ),
        )
    }
}
