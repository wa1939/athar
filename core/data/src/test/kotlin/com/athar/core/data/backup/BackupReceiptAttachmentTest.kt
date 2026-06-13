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
}
