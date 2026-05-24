package com.athar.core.data.repo

import com.athar.core.data.db.dao.SmsMessageDao
import com.athar.core.data.db.entity.SmsMessageEntity
import com.athar.core.domain.model.RawIngestEvent
import com.athar.core.domain.model.SmsParseStatus
import com.athar.core.domain.repo.SmsAuditEntry
import com.athar.core.domain.repo.SmsAuditRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SmsAuditRepositoryImpl @Inject constructor(
    private val dao: SmsMessageDao,
) : SmsAuditRepository {

    override fun observeAll(): Flow<List<SmsAuditEntry>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeByStatus(status: SmsParseStatus): Flow<List<SmsAuditEntry>> =
        dao.observeByStatus(status.name).map { list -> list.map { it.toDomain() } }

    override suspend fun record(event: RawIngestEvent): String {
        val id = UUID.randomUUID().toString()
        // OnConflictStrategy.IGNORE on the dao keeps the first record for a duplicate rawId.
        // We could compute a deterministic id from rawId to make idempotency strict, but the
        // pipeline already collapses transaction inserts on tx.sourceRefId — duplicate audit
        // rows are harmless.
        dao.insertIgnoreOnDup(
            SmsMessageEntity(
                id = id,
                sender = event.sender,
                body = event.body,
                receivedAt = event.receivedAt,
                parsedTransactionId = null,
                parseStatus = "NEW",
                parseError = null,
            ),
        )
        return id
    }

    override suspend fun updateParseResult(
        auditId: String,
        status: SmsParseStatus,
        parsedTransactionId: String?,
        error: String?,
    ) {
        dao.updateParse(auditId, status.name, parsedTransactionId, error)
    }
}

private fun SmsMessageEntity.toDomain(): SmsAuditEntry = SmsAuditEntry(
    id = id,
    sender = sender,
    body = body,
    receivedAt = receivedAt,
    parsedTransactionId = parsedTransactionId,
    status = parseStatus.toStatusOrFailed(),
    error = parseError,
)

private fun String.toStatusOrFailed(): SmsParseStatus = runCatching {
    SmsParseStatus.valueOf(this)
}.getOrDefault(SmsParseStatus.FAILED)
