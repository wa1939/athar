package com.athar.feature.today

import com.athar.core.common.money.Money
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class HistoryFilterTest {

    @Test
    fun `filters by source independently from status and type`() {
        val rows = listOf(
            tx(id = "sms-expense", source = IngestSource.SMS, status = TxStatus.PENDING),
            tx(id = "manual-expense", source = IngestSource.MANUAL, status = TxStatus.PENDING),
            tx(id = "notification-income", source = IngestSource.NOTIFICATION, type = TxType.INCOME),
        )

        val filtered = filterHistoryTransactions(
            all = rows,
            query = "",
            status = HistoryStatusFilter.PENDING,
            type = HistoryTypeFilter.EXPENSE,
            source = HistorySourceFilter.SMS,
        )

        assertThat(filtered.map { it.id }).containsExactly("sms-expense")
    }

    @Test
    fun `searches source reference ids for imported and SMS rows`() {
        val rows = listOf(
            tx(id = "import", source = IngestSource.IMPORT, sourceRefId = "csv-row-42"),
            tx(id = "sms", source = IngestSource.SMS, sourceRefId = "content://sms/900"),
            tx(id = "manual", source = IngestSource.MANUAL, sourceRefId = null),
        )

        val filtered = filterHistoryTransactions(
            all = rows,
            query = "row-42",
            status = HistoryStatusFilter.ALL,
            type = HistoryTypeFilter.ALL,
            source = HistorySourceFilter.ALL,
        )

        assertThat(filtered.map { it.id }).containsExactly("import")
    }

    @Test
    fun `keeps all source filter inclusive`() {
        val rows = listOf(
            tx(id = "sms", source = IngestSource.SMS),
            tx(id = "manual", source = IngestSource.MANUAL),
            tx(id = "recurring", source = IngestSource.RECURRING),
        )

        val filtered = filterHistoryTransactions(
            all = rows,
            query = "",
            status = HistoryStatusFilter.ALL,
            type = HistoryTypeFilter.ALL,
            source = HistorySourceFilter.ALL,
        )

        assertThat(filtered.map { it.id }).containsExactly("sms", "manual", "recurring").inOrder()
    }

    private fun tx(
        id: String,
        source: IngestSource,
        status: TxStatus = TxStatus.CONFIRMED,
        type: TxType = TxType.EXPENSE,
        sourceRefId: String? = "$source-$id",
    ) = Transaction(
        id = id,
        accountId = "account",
        type = type,
        amount = Money.of(BigDecimal("10")),
        date = LocalDate(2026, 6, 13),
        occurredAt = Instant.parse("2026-06-13T00:00:00Z"),
        merchant = "Merchant $id",
        merchantNormalized = "merchant $id",
        categoryId = null,
        notes = null,
        source = source,
        sourceRefId = sourceRefId,
        status = status,
        confidence = null,
        createdAt = Instant.parse("2026-06-13T00:00:00Z"),
        updatedAt = Instant.parse("2026-06-13T00:00:00Z"),
    )
}
