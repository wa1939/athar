package com.athar.ingestion.smsparser

import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.RawIngestEvent
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * WM084 parent-local diagnostic: replays the private prepared archive through the
 * real parser and aggregates merchant/type counts for financial transactions so
 * the parent can author merchant→category rules for full coverage.
 *
 * Opt-in only; output stays under build/private-results (parent-local).
 * Emits merchant names and counts — no amounts, no raw bodies.
 */
class PrivateMerchantCoverageAuditTest {
    @Test
    fun writesMerchantHistogramOnlyWhenExplicitlyEnabled() {
        val token = System.getenv("ATHAR_WM084_OPT_IN")
        assumeTrue(token == "wm084-local", "WM084 coverage audit not engaged")
        val input = Path.of(checkNotNull(System.getenv("ATHAR_WM084_INPUT")))
        val output = Path.of(checkNotNull(System.getenv("ATHAR_WM084_OUTPUT")))
        check(Files.isRegularFile(input))
        Files.createDirectories(output.parent)

        val parser = TemplateBasedSmsParser(BuiltInSmsTemplateRegistry.templates())
        val decoder = Base64.getDecoder()
        // merchant -> (type -> count)
        val histogram = sortedMapOf<String, MutableMap<String, Int>>()
        var records = 0
        var successes = 0
        var ignored = 0
        var failed = 0
        var missingMerchant = 0

        Files.lines(input).use { lines ->
            lines.forEach { line ->
                val fields = line.split('\t', limit = 4)
                check(fields.size == 4)
                val event = RawIngestEvent(
                    id = fields[0], rawId = fields[0], source = IngestSource.SMS,
                    sender = String(decoder.decode(fields[2]), Charsets.UTF_8),
                    body = String(decoder.decode(fields[3]), Charsets.UTF_8),
                    receivedAt = Instant.parse(fields[1]),
                )
                records++
                when (val result = parser.parse(event)) {
                    is ParseResult.Success -> {
                        successes++
                        val merchant = result.merchant?.trim().orEmpty()
                        if (merchant.isEmpty()) {
                            missingMerchant++
                        } else {
                            val perType = histogram.getOrPut(merchant) { mutableMapOf() }
                            perType[result.type.name] = (perType[result.type.name] ?: 0) + 1
                        }
                    }
                    ParseResult.Ignored -> ignored++
                    is ParseResult.Failed -> failed++
                }
            }
        }

        val out = buildString {
            appendLine("records\t$records")
            appendLine("successes\t$successes")
            appendLine("ignored\t$ignored")
            appendLine("failed\t$failed")
            appendLine("missingMerchant\t$missingMerchant")
            appendLine("distinctMerchants\t${histogram.size}")
            for ((merchant, types) in histogram) {
                val counts = types.entries.joinToString(",") { "${it.key}=${it.value}" }
                appendLine("M\t$merchant\t$counts")
            }
        }
        Files.writeString(output, out)
        check(records > 5000) { "unexpected corpus size: $records" }
    }
}
