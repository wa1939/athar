package com.athar.core.domain.model

import kotlinx.datetime.Instant

data class ReceiptAttachment(
    val id: String,
    val transactionId: String,
    val mimeType: String,
    val originalName: String?,
    val sizeBytes: Long,
    val payload: ByteArray,
    val createdAt: Instant,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReceiptAttachment) return false
        return id == other.id &&
            transactionId == other.transactionId &&
            mimeType == other.mimeType &&
            originalName == other.originalName &&
            sizeBytes == other.sizeBytes &&
            payload.contentEquals(other.payload) &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + transactionId.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + (originalName?.hashCode() ?: 0)
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}

data class ReceiptAttachmentMeta(
    val id: String,
    val transactionId: String,
    val mimeType: String,
    val originalName: String?,
    val sizeBytes: Long,
    val createdAt: Instant,
)
