package com.athar.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "activity_log",
    indices = [Index(value = ["timestamp"])],
)
internal data class ActivityLogEntity(
    @PrimaryKey val id: String,
    val timestamp: Instant,
    val action: String,        // ActivityAction.name (CREATE/UPDATE/DELETE/CONFIRM/DISMISS)
    val entityType: String,    // TRANSACTION (only thing logged in P-08; CATEGORY/etc. can follow)
    val entityId: String,
    val summary: String,       // Short human-readable line: "STARBUCKS · -200.00 ر.س"
)
