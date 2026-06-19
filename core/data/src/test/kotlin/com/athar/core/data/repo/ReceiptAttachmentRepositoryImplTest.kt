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
    fun `stores payload and exposes metadata list without payload`() = runTest {
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

        assertThat(repo.get("receipt-1")).isEqualTo(attachment)
        assertThat(repo.getForTransaction("tx-1")).isEqualTo(attachment)
        val meta = repo.metadataForTransaction("tx-1")
        assertThat(meta?.id).isEqualTo("receipt-1")
        assertThat(meta?.transactionId).isEqualTo("tx-1")
        assertThat(meta?.mimeType).isEqualTo("image/jpeg")
        assertThat(meta?.originalName).isEqualTo("coffee.jpg")
        assertThat(meta?.sizeBytes).isEqualTo(3)
        assertThat(repo.metadataListForTransaction("tx-1").map { it.id }).containsExactly("receipt-1")
    }

    @Test
    fun `stores multiple attachments for one transaction and deletes one by id`() = runTest {
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
        repo.upsert(
            ReceiptAttachment(
                id = "receipt-2",
                transactionId = "tx-1",
                mimeType = "image/jpeg",
                originalName = "card.jpg",
                sizeBytes = 2,
                payload = byteArrayOf(8, 7),
                createdAt = Instant.parse("2026-06-13T12:01:00Z"),
            ),
        )

        assertThat(repo.metadataListForTransaction("tx-1").map { it.id })
            .containsExactly("receipt-2", "receipt-1")
            .inOrder()

        repo.delete("receipt-2")

        assertThat(repo.metadataListForTransaction("tx-1").map { it.id }).containsExactly("receipt-1")
        assertThat(repo.get("receipt-2")).isNull()
    }

    @Test
    fun `deletes all attachments by transaction id`() = runTest {
        val dao = FakeTransactionReceiptDao()
        val repo = ReceiptAttachmentRepositoryImpl(dao)
        listOf("receipt-1", "receipt-2").forEach { id ->
            repo.upsert(
                ReceiptAttachment(
                    id = id,
                    transactionId = "tx-1",
                    mimeType = "image/png",
                    originalName = null,
                    sizeBytes = 1,
                    payload = byteArrayOf(9),
                    createdAt = Instant.parse("2026-06-13T12:00:00Z"),
                ),
            )
        }

        repo.deleteForTransaction("tx-1")

        assertThat(repo.getForTransaction("tx-1")).isNull()
        assertThat(repo.metadataForTransaction("tx-1")).isNull()
        assertThat(repo.metadataListForTransaction("tx-1")).isEmpty()
    }
}

private class FakeTransactionReceiptDao : TransactionReceiptDao {
    private val rows = linkedMapOf<String, TransactionReceiptEntity>()

    override suspend fun getForTransaction(transactionId: String): TransactionReceiptEntity? =
        listForTransaction(transactionId).firstOrNull()

    override suspend fun get(id: String): TransactionReceiptEntity? = rows[id]

    override suspend fun listForTransaction(transactionId: String): List<TransactionReceiptEntity> =
        rows.values
            .filter { it.transactionId == transactionId }
            .sortedByDescending { it.createdAt }

    override suspend fun metadataForTransaction(transactionId: String): TransactionReceiptMetaRow? =
        metadataListForTransaction(transactionId).firstOrNull()

    override suspend fun metadataListForTransaction(transactionId: String): List<TransactionReceiptMetaRow> =
        listForTransaction(transactionId).map {
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
        rows[entity.id] = entity
    }

    override suspend fun delete(id: String) {
        rows.remove(id)
    }

    override suspend fun deleteForTransaction(transactionId: String) {
        rows.entries.removeIf { it.value.transactionId == transactionId }
    }

    override suspend fun clear() {
        rows.clear()
    }
}
