package com.athar.core.data.support

import com.athar.core.data.db.entity.SmsMessageEntity
import com.athar.core.domain.model.SmsParseStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class SupportDiagnosticsExporterTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    @Test
    fun `payload omits raw sender body merchant and amount text`() {
        val rawSender = "+966501234567"
        val rawBody = "Debit Card POS Purchase at Jarir SAR 1,234.56 card 1234 OTP 9999"
        val payload = SupportDiagnosticsReportBuilder.build(
            rows = listOf(
                sms(
                    sender = rawSender,
                    body = rawBody,
                    status = SmsParseStatus.FAILED.name,
                    error = "amount unparseable: 1,234.56 · tried=alrajhi_pos,universal_amount",
                ),
            ),
            totalRows = 1,
            statusCounts = mapOf(SmsParseStatus.FAILED.name to 1),
            generatedAt = "2026-06-13T00:00:00Z",
        )

        val encoded = json.encodeToString(payload)

        assertThat(encoded).doesNotContain(rawSender)
        assertThat(encoded).doesNotContain(rawBody)
        assertThat(encoded).doesNotContain("Jarir")
        assertThat(encoded).doesNotContain("1,234.56")
        assertThat(encoded).doesNotContain("OTP 9999")
        assertThat(encoded).doesNotContain("card 1234")
        assertThat(payload.recentAuditSamples.single().bodyShapeSha256).hasLength(12)
        assertThat(payload.recentAuditSamples.single().redactedError).isEqualTo("amount unparseable: [number]")
    }

    @Test
    fun `summary uses database totals instead of recent sample size`() {
        val payload = SupportDiagnosticsReportBuilder.build(
            rows = listOf(
                sms(status = SmsParseStatus.PARSED.name),
                sms(id = "2", status = SmsParseStatus.FAILED.name),
            ),
            totalRows = 1_200,
            statusCounts = mapOf(
                SmsParseStatus.PARSED.name to 700,
                SmsParseStatus.FAILED.name to 300,
                SmsParseStatus.IGNORED.name to 190,
                "NEW" to 10,
            ),
            generatedAt = "2026-06-13T00:00:00Z",
        )

        assertThat(payload.summary.totalAuditRows).isEqualTo(1_200)
        assertThat(payload.summary.includedRecentRows).isEqualTo(2)
        assertThat(payload.summary.truncated).isTrue()
        assertThat(payload.summary.parsed).isEqualTo(700)
        assertThat(payload.summary.failed).isEqualTo(300)
        assertThat(payload.summary.ignored).isEqualTo(190)
        assertThat(payload.summary.newRows).isEqualTo(10)
    }

    @Test
    fun `failed rows group by redacted parser reason and keep template ids`() {
        val payload = SupportDiagnosticsReportBuilder.build(
            rows = listOf(
                sms(
                    id = "1",
                    status = SmsParseStatus.FAILED.name,
                    error = "amount unparseable: ١٢٣٤.٥٦ · tried=d360_online_purchase,universal_amount",
                ),
                sms(
                    id = "2",
                    status = SmsParseStatus.FAILED.name,
                    error = "amount unparseable: 999.00 · tried=d360_online_purchase",
                ),
            ),
            totalRows = 2,
            statusCounts = mapOf(SmsParseStatus.FAILED.name to 2),
            generatedAt = "2026-06-13T00:00:00Z",
        )

        val group = payload.errorGroups.single()
        assertThat(group.category).isEqualTo("amount_parse")
        assertThat(group.redactedError).isEqualTo("amount unparseable: [number]")
        assertThat(group.count).isEqualTo(2)
        assertThat(group.templateAttempts).containsExactly("d360_online_purchase", "universal_amount")
        assertThat(group.templateAttemptCount).isEqualTo(2)
    }

    private fun sms(
        id: String = "1",
        sender: String = "AlRajhiBank",
        body: String = "Purchase SAR 10.00",
        status: String = SmsParseStatus.PARSED.name,
        error: String? = null,
    ): SmsMessageEntity = SmsMessageEntity(
        id = id,
        sender = sender,
        body = body,
        receivedAt = Instant.parse("2026-06-13T12:00:00Z"),
        parsedTransactionId = if (status == SmsParseStatus.PARSED.name) "tx-$id" else null,
        parseStatus = status,
        parseError = error,
    )
}
