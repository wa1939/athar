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
 * Since G-4 (multi-account), this seed remains the default account for new manual
 * transactions until the user creates their own. The user can rename it, change its
 * type/currency, or archive it — but never hard-delete it (the row is also the
 * fallback target for transactions whose source account can't be resolved).
 */
internal class AccountSeed @Inject constructor(
    private val dao: AccountDao,
    private val clock: Clock,
) {

    suspend fun seedManualIfMissing() {
        val existing = dao.get(MANUAL_ACCOUNT_ID)
        if (existing != null) return
        val now = clock.now()
        dao.upsert(
            AccountEntity(
                id = MANUAL_ACCOUNT_ID,
                name = "النقدي",
                type = "CASH",
                currency = "SAR",
                openingBalanceMinor = 0,
                openingBalanceCurrency = "SAR",
                smsSenders = "[]",
                notes = null,
                sortOrder = 0,
                active = true,
                archivedAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
