package com.athar.core.domain.repo

import com.athar.core.domain.model.ReceiptAttachment
import com.athar.core.domain.model.ReceiptAttachmentMeta

interface ReceiptAttachmentRepository {
    suspend fun upsert(attachment: ReceiptAttachment)
    suspend fun get(id: String): ReceiptAttachment?
    suspend fun getForTransaction(transactionId: String): ReceiptAttachment?
    suspend fun metadataListForTransaction(transactionId: String): List<ReceiptAttachmentMeta>
    suspend fun metadataForTransaction(transactionId: String): ReceiptAttachmentMeta?
    suspend fun delete(id: String)
    suspend fun deleteForTransaction(transactionId: String)
}
