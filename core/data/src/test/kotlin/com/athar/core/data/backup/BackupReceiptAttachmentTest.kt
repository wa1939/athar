package com.athar.core.data.backup

import com.athar.core.data.db.entity.TransactionReceiptEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class BackupReceiptAttachmentTest {

    @Test
    fun `receipt payload survives backup mapping roundtrip`() {
        val entity = TransactionReceiptEntity(
            id = "receipt-1",
            transactionId = "tx-1",
            mimeType = "image/jpeg",
            originalName = "coffee.jpg",
            sizeBytes = 4,
            payload = byteArrayOf(4, 3, 2, 1),
            createdAt = Instant.parse("2026-06-13T12:00:00Z"),
        )

        val roundTrip = entity.toBackup().toEntity()

        assertThat(roundTrip.id).isEqualTo(entity.id)
        assertThat(roundTrip.transactionId).isEqualTo(entity.transactionId)
        assertThat(roundTrip.mimeType).isEqualTo(entity.mimeType)
        assertThat(roundTrip.originalName).isEqualTo(entity.originalName)
        assertThat(roundTrip.sizeBytes).isEqualTo(entity.sizeBytes)
        assertThat(roundTrip.createdAt).isEqualTo(entity.createdAt)
        assertThat(roundTrip.payload.asList()).containsExactly(4.toByte(), 3.toByte(), 2.toByte(), 1.toByte()).inOrder()
    }

    @Test
    fun `multiple receipts for one transaction survive backup mapping`() {
        val receipts = listOf(
            TransactionReceiptEntity(
                id = "receipt-1",
                transactionId = "tx-1",
                mimeType = "image/jpeg",
                originalName = "itemized.jpg",
                sizeBytes = 2,
                payload = byteArrayOf(1, 2),
                createdAt = Instant.parse("2026-06-13T12:00:00Z"),
            ),
            TransactionReceiptEntity(
                id = "receipt-2",
                transactionId = "tx-1",
                mimeType = "image/png",
                originalName = "card-slip.png",
                sizeBytes = 3,
                payload = byteArrayOf(3, 4, 5),
                createdAt = Instant.parse("2026-06-13T12:01:00Z"),
            ),
        )

        val roundTrip = receipts.map { it.toBackup().toEntity() }

        assertThat(roundTrip.map { it.id }).containsExactly("receipt-1", "receipt-2").inOrder()
        assertThat(roundTrip.map { it.transactionId }).containsExactly("tx-1", "tx-1").inOrder()
        assertThat(roundTrip[1].payload.asList()).containsExactly(3.toByte(), 4.toByte(), 5.toByte()).inOrder()
    }
}
