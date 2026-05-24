package com.athar.core.domain.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Audit trail of write operations on user data. Backlog P-08.
 *
 * P-08 scope is transactions only: create / update / delete / confirm-pending / dismiss-pending.
 * Category and wishlist edits can be added by emitting the same shape from those repos in the
 * future without changing the table schema.
 */
interface ActivityLogRepository {
    fun observeRecent(limit: Int = 100): Flow<List<ActivityLogEntry>>
    suspend fun record(entry: ActivityLogEntry)
}

data class ActivityLogEntry(
    val id: String,
    val timestamp: Instant,
    val action: ActivityAction,
    val entityType: String,   // "TRANSACTION" for P-08
    val entityId: String,
    val summary: String,
)

enum class ActivityAction { CREATE, UPDATE, DELETE, CONFIRM, DISMISS }
