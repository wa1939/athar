package com.athar.core.data.mapper

import com.athar.core.data.db.entity.TransactionReceiptEntity
import com.athar.core.data.db.entity.TransactionReceiptMetaRow
import com.athar.core.domain.model.ReceiptAttachment
import com.athar.core.domain.model.ReceiptAttachmentMeta

internal fun TransactionReceiptEntity.toDomain(): ReceiptAttachment = ReceiptAttachment(
    id = id,
    transactionId = transactionId,
    mimeType = mimeType,
    originalName = originalName,
    sizeBytes = sizeBytes,
    payload = payload,
    createdAt = createdAt,
)

internal fun ReceiptAttachment.toEntity(): TransactionReceiptEntity = TransactionReceiptEntity(
    id = id,
    transactionId = transactionId,
    mimeType = mimeType,
    originalName = originalName,
    sizeBytes = sizeBytes,
    payload = payload,
    createdAt = createdAt,
)

internal fun TransactionReceiptMetaRow.toDomain(): ReceiptAttachmentMeta = ReceiptAttachmentMeta(
    id = id,
    transactionId = transactionId,
    mimeType = mimeType,
    originalName = originalName,
    sizeBytes = sizeBytes,
    createdAt = createdAt,
)
