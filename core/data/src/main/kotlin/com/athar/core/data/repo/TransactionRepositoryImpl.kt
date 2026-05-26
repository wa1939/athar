package com.athar.core.data.repo

import com.athar.core.common.time.Period
import com.athar.core.data.db.dao.TransactionDao
import com.athar.core.data.mapper.toDomain
import com.athar.core.data.mapper.toEntity
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.repo.ActivityAction
import com.athar.core.domain.repo.ActivityLogEntry
import com.athar.core.domain.repo.ActivityLogRepository
import com.athar.core.domain.repo.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao,
    private val activityLog: ActivityLogRepository,
    private val clock: Clock,
) : TransactionRepository {

    override fun observeByPeriod(period: Period, status: TxStatus?): Flow<List<Transaction>> =
        dao.observeByPeriod(period.start, period.endExclusive, status?.name)
            .map { list -> list.map { it.toDomain() } }

    override fun observePending(): Flow<List<Transaction>> =
        dao.observePending().map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Transaction>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: String): Transaction? = dao.get(id)?.toDomain()

    override suspend fun upsert(transaction: Transaction) {
        val existed = dao.get(transaction.id) != null
        dao.upsert(transaction.toEntity())
        activityLog.record(
            ActivityLogEntry(
                id = UUID.randomUUID().toString(),
                timestamp = clock.now(),
                action = if (existed) ActivityAction.UPDATE else ActivityAction.CREATE,
                entityType = ENTITY_TX,
                entityId = transaction.id,
                summary = "${transaction.merchant} · ${transaction.amount.amount.toPlainString()} ${transaction.amount.currency}",
            ),
        )
    }

    override suspend fun delete(id: String) {
        val existing = dao.get(id)
        dao.delete(id)
        activityLog.record(
            ActivityLogEntry(
                id = UUID.randomUUID().toString(),
                timestamp = clock.now(),
                action = ActivityAction.DELETE,
                entityType = ENTITY_TX,
                entityId = id,
                summary = existing?.merchant.orEmpty(),
            ),
        )
    }

    override suspend fun setStatus(id: String, status: TxStatus) {
        dao.setStatus(id, status.name, clock.now())
        val action = when (status) {
            TxStatus.CONFIRMED -> ActivityAction.CONFIRM
            TxStatus.DISMISSED -> ActivityAction.DISMISS
            TxStatus.PENDING -> null
        }
        if (action != null) {
            activityLog.record(
                ActivityLogEntry(
                    id = UUID.randomUUID().toString(),
                    timestamp = clock.now(),
                    action = action,
                    entityType = ENTITY_TX,
                    entityId = id,
                    summary = "",
                ),
            )
        }
    }

    override suspend fun clearPending(): Int {
        val count = dao.clearPending()
        if (count > 0) {
            activityLog.record(
                ActivityLogEntry(
                    id = UUID.randomUUID().toString(),
                    timestamp = clock.now(),
                    action = ActivityAction.DELETE,
                    entityType = ENTITY_TX,
                    entityId = "pending-tray",
                    summary = "Cleared $count pending entries",
                ),
            )
        }
        return count
    }

    override suspend fun confirmAllConfident(minConfidence: Float): Int =
        dao.confirmAllConfident(minConfidence, clock.now()).also {
            if (it > 0) logBulk(ActivityAction.CONFIRM, "Bulk-confirmed $it (≥${minConfidence})")
        }

    override suspend fun dismissAllLowConfidence(maxConfidence: Float): Int =
        dao.dismissAllLowConfidence(maxConfidence, clock.now()).also {
            if (it > 0) logBulk(ActivityAction.DISMISS, "Bulk-dismissed $it (<${maxConfidence})")
        }

    override suspend fun dismissAllPending(): Int =
        dao.dismissAllPending(clock.now()).also {
            if (it > 0) logBulk(ActivityAction.DISMISS, "Dismissed all $it pending")
        }

    override suspend fun recoverDismissedToPending(): Int =
        dao.recoverDismissedToPending(clock.now()).also {
            if (it > 0) logBulk(ActivityAction.UPDATE, "Recovered $it dismissed → pending")
        }

    override suspend fun applyCategoryToMatching(pattern: String, categoryId: String): Int =
        dao.applyCategoryToMatching(pattern.lowercase().trim(), categoryId, clock.now()).also {
            if (it > 0) logBulk(
                ActivityAction.UPDATE,
                "Applied category '$categoryId' to $it transactions matching '$pattern'",
            )
        }

    private suspend fun logBulk(action: ActivityAction, summary: String) {
        activityLog.record(
            ActivityLogEntry(
                id = UUID.randomUUID().toString(),
                timestamp = clock.now(),
                action = action,
                entityType = ENTITY_TX,
                entityId = "pending-tray",
                summary = summary,
            ),
        )
    }

    private companion object {
        const val ENTITY_TX = "TRANSACTION"
    }
}
