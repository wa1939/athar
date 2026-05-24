package com.athar.core.domain.repo

import com.athar.core.domain.model.RawIngestEvent
import com.athar.core.domain.model.SmsParseStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * The audit log of every SMS the pipeline has seen.
 *
 * Master Brief §4.6 robustness rule: "Never delete a SMS. Audit log retains every parsed
 * and unparsed message." Failed/ignored events stay in the table so the user can teach
 * the parser from the Settings → SMS log screen later.
 */
interface SmsAuditRepository {
    fun observeAll(): Flow<List<SmsAuditEntry>>
    fun observeByStatus(status: SmsParseStatus): Flow<List<SmsAuditEntry>>
    /** Records the raw event; returns the audit row id. Idempotent on event.rawId duplicates. */
    suspend fun record(event: RawIngestEvent): String
    suspend fun updateParseResult(
        auditId: String,
        status: SmsParseStatus,
        parsedTransactionId: String?,
        error: String?,
    )
}

data class SmsAuditEntry(
    val id: String,
    val sender: String,
    val body: String,
    val receivedAt: Instant,
    val parsedTransactionId: String?,
    val status: SmsParseStatus,
    val error: String?,
)
