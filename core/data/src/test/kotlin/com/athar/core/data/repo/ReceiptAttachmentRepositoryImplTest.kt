package com.athar.core.data.repo

import com.athar.core.data.db.dao.TransactionReceiptDao
import com.athar.core.data.db.entity.TransactionReceiptEntity
import com.athar.core.data.db.entity.TransactionReceiptMetaRow
import com.athar.core.domain.model.ReceiptAttachment
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class ReceiptAttachmentRepositoryImplTest {

    @Test
    fun `stores payload and exposes metadata without payload`() = runTest {
        val dao = FakeTransactionReceiptDao()
        val repo = ReceiptAttachmentRepositoryImpl(dao)
        val attachment = ReceiptAttachment(
            id = "receipt-1",
            transactionId = "tx-1",
            mimeType = "image/jpeg",
            originalName = "coffee.jpg",
            sizeBytes = 3,
            payload = byteArrayOf(1, 2, 3),
            createdAt = Instant.parse("2026-06-13T12:00:00Z"),
        )

        repo.upsert(attachment)

        assertThat(repo.getForTransaction("tx-1")).isEqualTo(attachment)
        val meta = repo.metadataForTransaction("tx-1")
        assertThat(meta?.id).isEqualTo("receipt-1")
        assertThat(meta?.transactionId).isEqualTo("tx-1")
        assertThat(meta?.mimeType).isEqualTo("image/jpeg")
        assertThat(meta?.originalName).isEqualTo("coffee.jpg")
        assertThat(meta?.sizeBytes).isEqualTo(3)
    }

    @Test
    fun `deletes attachment by transaction id`() = runTest {
        val dao = FakeTransactionReceiptDao()
        val repo = ReceiptAttachmentRepositoryImpl(dao)
        repo.upsert(
            ReceiptAttachment(
                id = "receipt-1",
                transactionId = "tx-1",
                mimeType = "image/png",
                originalName = null,
                sizeBytes = 1,
                payload = byteArrayOf(9),
                createdAt = Instant.parse("2026-06-13T12:00:00Z"),
            ),
        )

        repo.deleteForTransaction("tx-1")

        assertThat(repo.getForTransaction("tx-1")).isNull()
        assertThat(repo.metadataForTransaction("tx-1")).isNull()
    }
}

private class FakeTransactionReceiptDao : TransactionReceiptDao {
    private val rows = linkedMapOf<String, TransactionReceiptEntity>()

    override suspend fun getForTransaction(transactionId: String): TransactionReceiptEntity? =
        rows.values.firstOrNull { it.transactionId == transactionId }

    override suspend fun metadataForTransaction(transactionId: String): TransactionReceiptMetaRow? =
        getForTransaction(transactionId)?.let {
            TransactionReceiptMetaRow(
                id = it.id,
                transactionId = it.transactionId,
                mimeType = it.mimeType,
                originalName = it.originalName,
                sizeBytes = it.sizeBytes,
                createdAt = it.createdAt,
            )
        }

    override suspend fun all(): List<TransactionReceiptEntity> = rows.values.toList()

    override suspend fun upsert(entity: TransactionReceiptEntity) {
        rows.entries.firstOrNull { it.value.transactionId == entity.transactionId && it.key != entity.id }
            ?.let { rows.remove(it.key) }
        rows[entity.id] = entity
    }

    override suspend fun deleteForTransaction(transactionId: String) {
        rows.entries.firstOrNull { it.value.transactionId == transactionId }
            ?.let { rows.remove(it.key) }
    }

    override suspend fun clear() {
        rows.clear()
    }
}
