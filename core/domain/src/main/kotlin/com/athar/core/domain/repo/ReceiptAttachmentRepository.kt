package com.athar.core.domain.repo

import com.athar.core.domain.model.ReceiptAttachment
import com.athar.core.domain.model.ReceiptAttachmentMeta

interface ReceiptAttachmentRepository {
    suspend fun upsert(attachment: ReceiptAttachment)
    suspend fun getForTransaction(transactionId: String): ReceiptAttachment?
    suspend fun metadataForTransaction(transactionId: String): ReceiptAttachmentMeta?
    suspend fun deleteForTransaction(transactionId: String)
}
