package com.athar.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "sms_message",
    indices = [
        Index(value = ["sender"]),
        Index(value = ["parseStatus"]),
        Index(value = ["receivedAt"]),
    ],
)
internal data class SmsMessageEntity(
    @PrimaryKey val id: String,
    val sender: String,
    val body: String,
    val receivedAt: Instant,
    val parsedTransactionId: String?,
    val parseStatus: String,    // SmsParseStatus.name
    val parseError: String?,
)
