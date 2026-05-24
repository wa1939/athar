package com.athar.core.domain.repo

import com.athar.core.common.time.Period
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import kotlinx.coroutines.flow.Flow

/**
 * The public surface of the transaction data domain.
 *
 * Per the modularization skill (dimension 2: data ownership), data modules expose
 * repositories as their only public API. Feature modules depend on this interface
 * via `core:domain`; the implementation lives in `core:data` and is bound by Hilt.
 */
interface TransactionRepository {
    fun observeByPeriod(period: Period, status: TxStatus? = null): Flow<List<Transaction>>
    fun observePending(): Flow<List<Transaction>>
    suspend fun get(id: String): Transaction?
    suspend fun upsert(transaction: Transaction)
    suspend fun delete(id: String)
    suspend fun setStatus(id: String, status: TxStatus)
}
