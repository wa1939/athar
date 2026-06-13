package com.athar.feature.settings

import com.athar.core.domain.model.SmsParseStatus
import com.athar.core.domain.repo.SmsAuditEntry
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class SmsAuditSummaryTest {

    @Test
    fun `builds counts parse rate and sender health`() {
        val state = buildSmsAuditState(
            listOf(
                entry("AlRajhiBank", SmsParseStatus.PARSED),
                entry("AlRajhiBank", SmsParseStatus.PARSED),
                entry("UnknownBank", SmsParseStatus.FAILED),
                entry("UnknownBank", SmsParseStatus.FAILED),
                entry("Promo-AD", SmsParseStatus.IGNORED),
            ),
        )

        assertThat(state.totalParsed).isEqualTo(2)
        assertThat(state.totalFailed).isEqualTo(2)
        assertThat(state.totalIgnored).isEqualTo(1)
        assertThat(state.parseRatePercent).isEqualTo(40)

        assertThat(state.senderHealth.map { it.sender })
            .containsExactly("UnknownBank", "Promo-AD", "AlRajhiBank")
            .inOrder()
        assertThat(state.senderHealth.first().failed).isEqualTo(2)
    }

    @Test
    fun `limits visible entries but summarizes all senders`() {
        val rows = buildList {
            repeat(205) { add(entry("Bank-$it", SmsParseStatus.FAILED)) }
            add(entry("HighVolume", SmsParseStatus.PARSED))
            add(entry("HighVolume", SmsParseStatus.PARSED))
        }

        val state = buildSmsAuditState(rows)

        assertThat(state.entries).hasSize(200)
        assertThat(state.totalParsed).isEqualTo(2)
        assertThat(state.totalFailed).isEqualTo(205)
        assertThat(state.senderHealth).hasSize(5)
    }

    private fun entry(sender: String, status: SmsParseStatus) = SmsAuditEntry(
        id = "$sender-$status-${counter++}",
        sender = sender,
        body = "body",
        receivedAt = Instant.parse("2026-06-13T00:00:00Z"),
        parsedTransactionId = null,
        status = status,
        error = null,
    )

    private companion object {
        var counter = 0
    }
}
