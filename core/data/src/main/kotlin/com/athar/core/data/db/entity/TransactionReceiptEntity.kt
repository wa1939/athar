package com.athar.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "transaction_receipt",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["transactionId"]),
    ],
)
internal data class TransactionReceiptEntity(
    @PrimaryKey val id: String,
    val transactionId: String,
    val mimeType: String,
    val originalName: String?,
    val sizeBytes: Long,
    val payload: ByteArray,
    val createdAt: Instant,
)

internal data class TransactionReceiptMetaRow(
    val id: String,
    val transactionId: String,
    val mimeType: String,
    val originalName: String?,
    val sizeBytes: Long,
    val createdAt: Instant,
)
