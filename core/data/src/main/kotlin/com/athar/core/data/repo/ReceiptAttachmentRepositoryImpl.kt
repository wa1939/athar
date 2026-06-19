package com.athar.core.data.repo

import com.athar.core.data.db.dao.TransactionReceiptDao
import com.athar.core.data.mapper.toDomain
import com.athar.core.data.mapper.toEntity
import com.athar.core.domain.model.ReceiptAttachment
import com.athar.core.domain.model.ReceiptAttachmentMeta
import com.athar.core.domain.repo.ReceiptAttachmentRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ReceiptAttachmentRepositoryImpl @Inject constructor(
    private val dao: TransactionReceiptDao,
) : ReceiptAttachmentRepository {

    override suspend fun upsert(attachment: ReceiptAttachment) {
        dao.upsert(attachment.toEntity())
    }

    override suspend fun get(id: String): ReceiptAttachment? =
        dao.get(id)?.toDomain()

    override suspend fun getForTransaction(transactionId: String): ReceiptAttachment? =
        dao.getForTransaction(transactionId)?.toDomain()

    override suspend fun metadataListForTransaction(transactionId: String): List<ReceiptAttachmentMeta> =
        dao.metadataListForTransaction(transactionId).map { it.toDomain() }

    override suspend fun metadataForTransaction(transactionId: String): ReceiptAttachmentMeta? =
        dao.metadataForTransaction(transactionId)?.toDomain()

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun deleteForTransaction(transactionId: String) {
        dao.deleteForTransaction(transactionId)
    }
}
