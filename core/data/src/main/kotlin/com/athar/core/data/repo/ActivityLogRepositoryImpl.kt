package com.athar.core.data.repo

import com.athar.core.data.db.dao.ActivityLogDao
import com.athar.core.data.db.entity.ActivityLogEntity
import com.athar.core.domain.repo.ActivityAction
import com.athar.core.domain.repo.ActivityLogEntry
import com.athar.core.domain.repo.ActivityLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ActivityLogRepositoryImpl @Inject constructor(
    private val dao: ActivityLogDao,
) : ActivityLogRepository {

    override fun observeRecent(limit: Int): Flow<List<ActivityLogEntry>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun record(entry: ActivityLogEntry) {
        dao.insert(
            ActivityLogEntity(
                id = entry.id,
                timestamp = entry.timestamp,
                action = entry.action.name,
                entityType = entry.entityType,
                entityId = entry.entityId,
                summary = entry.summary,
            ),
        )
    }
}

private fun ActivityLogEntity.toDomain(): ActivityLogEntry = ActivityLogEntry(
    id = id,
    timestamp = timestamp,
    action = runCatching { ActivityAction.valueOf(action) }.getOrDefault(ActivityAction.UPDATE),
    entityType = entityType,
    entityId = entityId,
    summary = summary,
)
