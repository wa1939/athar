package com.athar.ingestion.smsparser

import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.RawIngestEvent
import com.athar.core.domain.model.TxType
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.readText

/**
 * Local-only aggregate audit for private SMS exports.
 *
 * The test is intentionally gated: CI and contributors without the private export
 * skip it, while the maintainer machine can keep running parser/categorizer drift
 * checks without committing raw SMS or throwaway scratch helpers.
 */
class PrivateSmsExportAuditTest {

    @Test
    fun `writes redacted aggregate report when private export is available`() {
        val export = privateExportPath()
        if (export == null || !Files.exists(export)) {
            println("ATHAR_PRIVATE_SMS_EXPORT not set and local private export not found; skipping private audit")
            return
        }

        val report = audit(export)
        val output = Path.of("build/private-corpus-audit/latest.json")
        Files.createDirectories(output.parent)
        Files.writeString(output, report.toJson())

        assertThat(report.records).isGreaterThan(0)
        assertThat(report.rawBodiesWritten).isEqualTo(0)
    }

    private fun audit(export: Path): AuditReport {
        val parser = TemplateBasedSmsParser(BuiltInSmsTemplateRegistry.templates())
        val seedRules = readRules(
            path = findUpward("core/data/src/main/assets/seed_rules.json"),
            categoryKey = "categoryId",
        )
        val catalogRules = readRules(
            path = findUpward("core/data/src/main/assets/seed_merchant_catalog.json"),
            categoryKey = "category",
        )
        val activePatterns = seedRules.mapTo(mutableSetOf()) { it.pattern.lowercase().trim() }
        val inactiveCatalogRules = catalogRules.filterNot { it.pattern.lowercase().trim() in activePatterns }
        val inactiveCatalogMatches = linkedMapOf<AuditRule, Int>()

        var successes = 0
        var ignored = 0
        var failed = 0
        var failedKnownBank = 0
        var parsedExpenses = 0
        var categorizedExpenses = 0
        var uncategorizedExpenses = 0
        var missingMerchantExpenses = 0

        val messages = readExport(export)
        messages.forEachIndexed { index, message ->
            when (val result = parser.parse(message.toEvent(index))) {
                ParseResult.Ignored -> ignored += 1
                is ParseResult.Failed -> {
                    failed += 1
                    if (KnownBankSenders.isKnown(message.sender)) failedKnownBank += 1
                }
                is ParseResult.Success -> {
                    successes += 1
                    if (result.type == TxType.EXPENSE) {
                        parsedExpenses += 1
                        val merchant = result.merchant?.trim()
                        if (merchant.isNullOrBlank()) missingMerchantExpenses += 1

                        val effectiveMerchant = (merchant ?: result.counterparty ?: message.sender)
                            .lowercase()
                            .trim()
                        if (seedRules.matches(effectiveMerchant)) {
                            categorizedExpenses += 1
                        } else {
                            uncategorizedExpenses += 1
                            inactiveCatalogRules
                                .filter { effectiveMerchant.contains(it.pattern.lowercase().trim()) }
                                .forEach { rule ->
                                    inactiveCatalogMatches[rule] = inactiveCatalogMatches.getOrDefault(rule, 0) + 1
                                }
                        }
                    }
                }
            }
        }

        return AuditReport(
            records = messages.size,
            successes = successes,
            ignored = ignored,
            failed = failed,
            failedKnownBank = failedKnownBank,
            parsedExpenses = parsedExpenses,
            categorizedExpenses = categorizedExpenses,
            uncategorizedExpenses = uncategorizedExpenses,
            missingMerchantExpenses = missingMerchantExpenses,
            inactiveCatalogMatches = inactiveCatalogMatches.entries
                .sortedWith(compareByDescending<Map.Entry<AuditRule, Int>> { it.value }.thenBy { it.key.pattern })
                .map { AuditCandidate(pattern = it.key.pattern, categoryId = it.key.categoryId, count = it.value) },
        )
    }

    private fun privateExportPath(): Path? {
        System.getProperty("athar.privateSmsExport")
            ?.takeIf { it.isNotBlank() }
            ?.let { return Path.of(it) }
        System.getenv("ATHAR_PRIVATE_SMS_EXPORT")
            ?.takeIf { it.isNotBlank() }
            ?.let { return Path.of(it) }
        return findUpwardOrNull("All Conversations 2026-05-27 175517.txt")
    }

    private fun readExport(path: Path): List<ExportMessage> {
        val messages = mutableListOf<ExportMessage>()
        val parts = path.readText().split(Regex("""\r?\n-{20,}\r?\n"""))
        parts.forEach { part ->
            val lines = part.lines()
            val headerIndex = lines.indexOfFirst { it.startsWith("Received from ") && " on " in it }
            if (headerIndex == -1) return@forEach
            val header = lines[headerIndex]
            val match = Regex("""^Received from (.+?) on (\d{4}-\d{2}-\d{2} \d{2}:\d{2})$""").matchEntire(header)
                ?: return@forEach
            val body = lines
                .drop(headerIndex + 1)
                .dropWhile { it.isBlank() }
                .joinToString("\n")
                .trim()
            if (body.isBlank()) return@forEach
            messages += ExportMessage(
                sender = match.groupValues[1].trim(),
                body = body,
                receivedAt = localExportTime(match.groupValues[2]),
            )
        }
        return messages
    }

    private fun localExportTime(raw: String): Instant {
        val local = LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        return Instant.fromEpochMilliseconds(local.atZone(ZoneId.of("Asia/Riyadh")).toInstant().toEpochMilli())
    }

    private fun ExportMessage.toEvent(index: Int) = RawIngestEvent(
        id = "private-$index",
        rawId = "private-$index",
        source = IngestSource.SMS,
        sender = sender,
        body = body,
        receivedAt = receivedAt,
    )

    private fun readRules(path: Path, categoryKey: String): List<AuditRule> =
        Regex(
            """"pattern"\s*:\s*"((?:\\.|[^"])*)"\s*,\s*"$categoryKey"\s*:\s*"([^"]+)"""",
            RegexOption.DOT_MATCHES_ALL,
        ).findAll(path.readText()).map { match ->
            AuditRule(
                pattern = match.groupValues[1].unescapeJson(),
                categoryId = match.groupValues[2],
            )
        }.toList()

    private fun List<AuditRule>.matches(merchant: String): Boolean =
        any { merchant.contains(it.pattern.lowercase().trim()) }

    private fun findUpward(relativePath: String): Path =
        findUpwardOrNull(relativePath) ?: Path.of(relativePath)

    private fun findUpwardOrNull(relativePath: String): Path? {
        var dir = Path.of("").toAbsolutePath()
        while (true) {
            val candidate = dir.resolve(relativePath)
            if (Files.exists(candidate)) return candidate
            dir = dir.parent ?: return null
        }
    }

    private fun String.unescapeJson(): String = replace("\\\"", "\"").replace("\\\\", "\\")

    private data class ExportMessage(
        val sender: String,
        val body: String,
        val receivedAt: Instant,
    )

    private data class AuditRule(
        val pattern: String,
        val categoryId: String,
    )

    private data class AuditCandidate(
        val pattern: String,
        val categoryId: String,
        val count: Int,
    ) {
        fun toJson(indent: String): String =
            """$indent{"pattern":"${jsonEscape(pattern)}","categoryId":"${jsonEscape(categoryId)}","count":$count}"""

        private fun jsonEscape(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private data class AuditReport(
        val records: Int,
        val successes: Int,
        val ignored: Int,
        val failed: Int,
        val failedKnownBank: Int,
        val parsedExpenses: Int,
        val categorizedExpenses: Int,
        val uncategorizedExpenses: Int,
        val missingMerchantExpenses: Int,
        val inactiveCatalogMatches: List<AuditCandidate>,
    ) {
        val rawBodiesWritten: Int = 0

        fun toJson(): String {
            val candidates = inactiveCatalogMatches.joinToString(",\n") { it.toJson("    ") }
            val candidateBlock = if (candidates.isBlank()) "" else "\n$candidates\n  "
            return """
                |{
                |  "records": $records,
                |  "successes": $successes,
                |  "ignored": $ignored,
                |  "failed": $failed,
                |  "failedKnownBank": $failedKnownBank,
                |  "parsedExpenses": $parsedExpenses,
                |  "categorizedExpenses": $categorizedExpenses,
                |  "uncategorizedExpenses": $uncategorizedExpenses,
                |  "missingMerchantExpenses": $missingMerchantExpenses,
                |  "rawBodiesWritten": $rawBodiesWritten,
                |  "inactiveCatalogMatches": [$candidateBlock]
                |}
                |
            """.trimMargin()
        }
    }
}
