package com.athar.ingestion.smsparser

import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.RawIngestEvent
import com.athar.core.domain.model.TxType
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
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
        val output = auditOutputPath()
        Files.createDirectories(output.parent)
        Files.writeString(output, report.toJson())

        assertThat(report.records).isGreaterThan(0)
        assertThat(report.rawBodiesWritten).isEqualTo(0)
    }

    @Test
    fun `audit output path is rooted at repository build directory`() {
        val output = auditOutputPath()

        assertThat(output).isEqualTo(repositoryRoot().resolve("build/private-corpus-audit/latest.json"))
    }

    @Test
    fun `redacted report groups uncategorized merchants without raw labels`() {
        val export = Files.createTempFile("athar-private-audit-synthetic", ".txt")
        Files.writeString(
            export,
            """
                Received from AlRajhiBank on 2026-05-27 17:55

                PoS purchase
                Card:1234
                At: Local Test Merchant
                Amount:25 SAR
                --------------------------------------------------------------------------------
                Received from AlRajhiBank on 2026-05-28 17:55

                PoS purchase
                Card:1234
                At: Local Test Merchant
                Amount:25 SAR
            """.trimIndent(),
        )

        val report = audit(export)
        val json = report.toJson()

        assertThat(report.uncategorizedMerchantGroups).hasSize(1)
        val group = report.uncategorizedMerchantGroups.single()
        assertThat(group.sampleCount).isEqualTo(2)
        assertThat(group.shareOfUncategorizedPermille).isEqualTo(1000)
        assertThat(group.cumulativeShareOfUncategorizedPermille).isEqualTo(1000)
        assertThat(group.merchantHash).hasLength(12)
        assertThat(group.merchantLengthBucket).isEqualTo("9-32")
        assertThat(group.merchantScript).isEqualTo("latin")
        assertThat(report.categoryCoverage.categorizedExpenseCoveragePermille).isEqualTo(0)
        assertThat(report.categoryCoverage.uncategorizedExpenseBacklogPermille).isEqualTo(1000)
        assertThat(report.categoryCoverage.topUncategorizedGroupCoveragePermille).isEqualTo(1000)
        assertThat(report.categoryCoverage.otherUncategorizedExpenseCount).isEqualTo(0)
        assertThat(json).contains("uncategorizedMerchantGroups")
        assertThat(json).contains("categoryCoverage")
        assertThat(json).doesNotContain("Local Test Merchant")
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
        val missingMerchantGroups = linkedMapOf<MissingMerchantKey, Int>()
        val uncategorizedMerchantObservations = mutableListOf<UncategorizedMerchantObservation>()

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
                        if (merchant.isNullOrBlank()) {
                            missingMerchantExpenses += 1
                            val key = missingMerchantKey(
                                templateId = result.templateId,
                                sender = message.sender,
                                body = message.body,
                            )
                            missingMerchantGroups[key] = missingMerchantGroups.getOrDefault(key, 0) + 1
                        }

                        val effectiveMerchant = (merchant ?: result.counterparty ?: message.sender)
                            .lowercase()
                            .trim()
                        if (seedRules.matches(effectiveMerchant)) {
                            categorizedExpenses += 1
                        } else {
                            uncategorizedExpenses += 1
                            uncategorizedMerchantObservations += UncategorizedMerchantObservation(
                                merchant = effectiveMerchant,
                                templateId = result.templateId,
                            )
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
            missingMerchantGroups = missingMerchantGroups.entries
                .sortedWith(
                    compareByDescending<Map.Entry<MissingMerchantKey, Int>> { it.value }
                        .thenBy { it.key.templateId }
                        .thenBy { it.key.bodyShapeSha256 },
                )
                .take(20)
                .map { (key, count) -> key.toGroup(count) },
            uncategorizedMerchantGroups = buildUncategorizedMerchantGroups(uncategorizedMerchantObservations),
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

    private fun auditOutputPath(): Path =
        repositoryRoot().resolve("build/private-corpus-audit/latest.json")

    private fun repositoryRoot(): Path =
        findUpward("settings.gradle.kts").parent.toAbsolutePath().normalize()

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

    private fun buildUncategorizedMerchantGroups(
        observations: List<UncategorizedMerchantObservation>,
    ): List<UncategorizedMerchantGroup> {
        var cumulativeSamples = 0
        return observations
            .groupBy { it.merchant.lowercase().trim() }
            .map { (merchant, rows) ->
                UncategorizedMerchantGroup(
                    merchantHash = sha256Prefix("merchant:$merchant"),
                    merchantLengthBucket = bucket(merchant.length),
                    merchantScript = textScript(merchant),
                    sampleCount = rows.size,
                    shareOfUncategorizedPermille = permille(rows.size, observations.size),
                    cumulativeShareOfUncategorizedPermille = 0,
                    templateCounts = rows.groupingBy { it.templateId }
                        .eachCount()
                        .map { (templateId, count) -> TemplateCount(templateId = templateId, count = count) }
                        .sortedWith(compareByDescending<TemplateCount> { it.count }.thenBy { it.templateId }),
                )
            }
            .sortedWith(compareByDescending<UncategorizedMerchantGroup> { it.sampleCount }.thenBy { it.merchantHash })
            .take(20)
            .map { group ->
                cumulativeSamples += group.sampleCount
                group.copy(cumulativeShareOfUncategorizedPermille = permille(cumulativeSamples, observations.size))
            }
    }

    private fun textScript(text: String): String = when {
        text.isBlank() -> "blank"
        ArabicRegex.containsMatchIn(text) && LatinRegex.containsMatchIn(text) -> "mixed_arabic_latin"
        ArabicRegex.containsMatchIn(text) -> "arabic"
        LatinRegex.containsMatchIn(text) -> "latin"
        text.any { it.isDigit() } -> "numeric_or_mixed"
        else -> "other"
    }

    private fun senderHash(sender: String): String = sha256Prefix(sender.trim().lowercase())

    private fun senderKind(sender: String): String {
        val trimmed = sender.trim()
        val digitCount = trimmed.count { it.isDigit() || it in '٠'..'٩' || it in '۰'..'۹' }
        return when {
            trimmed.isBlank() -> "blank"
            digitCount == trimmed.count { !it.isWhitespace() } && digitCount <= 6 -> "short_code"
            digitCount >= 7 && trimmed.none { it.isLetter() } -> "phone_like"
            ArabicRegex.containsMatchIn(trimmed) -> "arabic_or_mixed"
            trimmed.any { it.isLetter() } && trimmed.any { it.isDigit() } -> "alphanumeric"
            trimmed.all { it.isLetter() || it.isWhitespace() || it == '-' } -> "alpha"
            else -> "mixed"
        }
    }

    private fun bodyFeatures(body: String): BodyFeatures {
        val normalized = normalizeArabicDigits(body)
        return BodyFeatures(
            lengthBucket = bucket(body.length),
            lineCount = body.lineSequence().count(),
            tokenCount = body.split(WhitespaceRegex).count { it.isNotBlank() },
            hasArabic = ArabicRegex.containsMatchIn(body),
            hasLatin = LatinRegex.containsMatchIn(body),
            hasCurrencyMarker = CurrencyRegex.containsMatchIn(normalized),
            amountTokenCount = AmountTokenRegex.findAll(normalized).count(),
            hasOtpMarker = OtpRegex.containsMatchIn(body),
            hasMaskedCardMarker = MaskedCardRegex.containsMatchIn(normalized),
            actionHints = ActionHintPatterns.mapNotNull { (hint, regex) ->
                hint.takeIf { regex.containsMatchIn(body) }
            },
        )
    }

    private fun bodyShapeSignature(body: String): String =
        normalizeArabicDigits(body)
            .map { ch ->
                when {
                    ch.isDigit() -> '9'
                    ch in '\u0600'..'\u06FF' -> 'r'
                    ch in 'A'..'Z' || ch in 'a'..'z' -> 'l'
                    ch.isWhitespace() -> ' '
                    else -> ch
                }
            }
            .joinToString("")
            .replace(WhitespaceRegex, " ")
            .take(500)

    private fun normalizeArabicDigits(input: String): String = buildString(input.length) {
        input.forEach { ch ->
            append(
                when (ch) {
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    else -> ch
                },
            )
        }
    }

    private fun bucket(length: Int): String = when {
        length <= 0 -> "0"
        length <= 8 -> "1-8"
        length <= 32 -> "9-32"
        length <= 80 -> "33-80"
        length <= 160 -> "81-160"
        length <= 320 -> "161-320"
        else -> "321+"
    }

    private fun sha256Prefix(value: String, length: Int = 12): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(length)
    }

    private fun missingMerchantKey(templateId: String, sender: String, body: String): MissingMerchantKey {
        val shape = bodyShapeSignature(body)
        return MissingMerchantKey(
            templateId = templateId,
            senderHash = senderHash(sender),
            senderKind = senderKind(sender),
            bodyShapeSha256 = sha256Prefix(shape),
            bodyShapePreview = shape.take(240),
            bodyFeatures = bodyFeatures(body),
        )
    }

    private data class ExportMessage(
        val sender: String,
        val body: String,
        val receivedAt: Instant,
    )

    private data class UncategorizedMerchantObservation(
        val merchant: String,
        val templateId: String,
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

    private data class MissingMerchantKey(
        val templateId: String,
        val senderHash: String,
        val senderKind: String,
        val bodyShapeSha256: String,
        val bodyShapePreview: String,
        val bodyFeatures: BodyFeatures,
    ) {
        fun toGroup(count: Int): MissingMerchantGroup =
            MissingMerchantGroup(
                templateId = templateId,
                senderHash = senderHash,
                senderKind = senderKind,
                bodyShapeSha256 = bodyShapeSha256,
                bodyShapePreview = bodyShapePreview,
                bodyFeatures = bodyFeatures,
                count = count,
            )
    }

    private data class MissingMerchantGroup(
        val templateId: String,
        val senderHash: String,
        val senderKind: String,
        val bodyShapeSha256: String,
        val bodyShapePreview: String,
        val bodyFeatures: BodyFeatures,
        val count: Int,
    ) {
        fun toJson(indent: String): String =
            """
                |$indent{
                |$indent  "templateId": "${jsonEscape(templateId)}",
                |$indent  "senderHash": "$senderHash",
                |$indent  "senderKind": "$senderKind",
                |$indent  "bodyShapeSha256": "$bodyShapeSha256",
                |$indent  "bodyShapePreview": "${jsonEscape(bodyShapePreview)}",
                |$indent  "bodyFeatures": ${bodyFeatures.toJson()},
                |$indent  "count": $count
                |$indent}
            """.trimMargin()

        private fun jsonEscape(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private data class BodyFeatures(
        val lengthBucket: String,
        val lineCount: Int,
        val tokenCount: Int,
        val hasArabic: Boolean,
        val hasLatin: Boolean,
        val hasCurrencyMarker: Boolean,
        val amountTokenCount: Int,
        val hasOtpMarker: Boolean,
        val hasMaskedCardMarker: Boolean,
        val actionHints: List<String>,
    ) {
        fun toJson(): String =
            """
                |{
                |  "lengthBucket": "$lengthBucket",
                |  "lineCount": $lineCount,
                |  "tokenCount": $tokenCount,
                |  "hasArabic": $hasArabic,
                |  "hasLatin": $hasLatin,
                |  "hasCurrencyMarker": $hasCurrencyMarker,
                |  "amountTokenCount": $amountTokenCount,
                |  "hasOtpMarker": $hasOtpMarker,
                |  "hasMaskedCardMarker": $hasMaskedCardMarker,
                |  "actionHints": [${actionHints.joinToString(",") { """"$it"""" }}]
                |}
            """.trimMargin().replace("\n", "")
    }

    private data class UncategorizedMerchantGroup(
        val merchantHash: String,
        val merchantLengthBucket: String,
        val merchantScript: String,
        val sampleCount: Int,
        val shareOfUncategorizedPermille: Int,
        val cumulativeShareOfUncategorizedPermille: Int,
        val templateCounts: List<TemplateCount>,
    ) {
        fun toJson(indent: String): String {
            val templates = templateCounts.joinToString(",") { it.toJson() }
            return """
                |$indent{
                |$indent  "merchantHash": "$merchantHash",
                |$indent  "merchantLengthBucket": "$merchantLengthBucket",
                |$indent  "merchantScript": "$merchantScript",
                |$indent  "sampleCount": $sampleCount,
                |$indent  "shareOfUncategorizedPermille": $shareOfUncategorizedPermille,
                |$indent  "cumulativeShareOfUncategorizedPermille": $cumulativeShareOfUncategorizedPermille,
                |$indent  "templateCounts": [$templates]
                |$indent}
            """.trimMargin()
        }
    }

    private data class CategoryCoverageSummary(
        val categorizedExpenseCoveragePermille: Int,
        val uncategorizedExpenseBacklogPermille: Int,
        val topUncategorizedGroupCount: Int,
        val topUncategorizedSampleCount: Int,
        val topUncategorizedGroupCoveragePermille: Int,
        val otherUncategorizedExpenseCount: Int,
        val largestUncategorizedGroupSampleCount: Int,
        val largestUncategorizedGroupCoveragePermille: Int,
    ) {
        fun toJson(indent: String): String =
            """
                |$indent{
                |$indent  "categorizedExpenseCoveragePermille": $categorizedExpenseCoveragePermille,
                |$indent  "uncategorizedExpenseBacklogPermille": $uncategorizedExpenseBacklogPermille,
                |$indent  "topUncategorizedGroupCount": $topUncategorizedGroupCount,
                |$indent  "topUncategorizedSampleCount": $topUncategorizedSampleCount,
                |$indent  "topUncategorizedGroupCoveragePermille": $topUncategorizedGroupCoveragePermille,
                |$indent  "otherUncategorizedExpenseCount": $otherUncategorizedExpenseCount,
                |$indent  "largestUncategorizedGroupSampleCount": $largestUncategorizedGroupSampleCount,
                |$indent  "largestUncategorizedGroupCoveragePermille": $largestUncategorizedGroupCoveragePermille
                |$indent}
            """.trimMargin()

        companion object {
            fun from(
                parsedExpenses: Int,
                categorizedExpenses: Int,
                uncategorizedExpenses: Int,
                groups: List<UncategorizedMerchantGroup>,
            ): CategoryCoverageSummary {
                val topGroupSampleCount = groups.sumOf { it.sampleCount }
                val largestGroupSampleCount = groups.firstOrNull()?.sampleCount ?: 0
                return CategoryCoverageSummary(
                    categorizedExpenseCoveragePermille = permille(categorizedExpenses, parsedExpenses),
                    uncategorizedExpenseBacklogPermille = permille(uncategorizedExpenses, parsedExpenses),
                    topUncategorizedGroupCount = groups.size,
                    topUncategorizedSampleCount = topGroupSampleCount,
                    topUncategorizedGroupCoveragePermille = permille(topGroupSampleCount, uncategorizedExpenses),
                    otherUncategorizedExpenseCount = (uncategorizedExpenses - topGroupSampleCount).coerceAtLeast(0),
                    largestUncategorizedGroupSampleCount = largestGroupSampleCount,
                    largestUncategorizedGroupCoveragePermille = permille(largestGroupSampleCount, uncategorizedExpenses),
                )
            }
        }
    }

    private data class TemplateCount(
        val templateId: String,
        val count: Int,
    ) {
        fun toJson(): String = """{"templateId":"${jsonEscape(templateId)}","count":$count}"""

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
        val missingMerchantGroups: List<MissingMerchantGroup>,
        val uncategorizedMerchantGroups: List<UncategorizedMerchantGroup>,
    ) {
        val rawBodiesWritten: Int = 0
        val categoryCoverage: CategoryCoverageSummary = CategoryCoverageSummary.from(
            parsedExpenses = parsedExpenses,
            categorizedExpenses = categorizedExpenses,
            uncategorizedExpenses = uncategorizedExpenses,
            groups = uncategorizedMerchantGroups,
        )

        fun toJson(): String {
            val candidates = inactiveCatalogMatches.joinToString(",\n") { it.toJson("    ") }
            val candidateBlock = if (candidates.isBlank()) "" else "\n$candidates\n  "
            val groups = missingMerchantGroups.joinToString(",\n") { it.toJson("    ") }
            val groupBlock = if (groups.isBlank()) "" else "\n$groups\n  "
            val uncategorizedGroups = uncategorizedMerchantGroups.joinToString(",\n") { it.toJson("    ") }
            val uncategorizedGroupBlock = if (uncategorizedGroups.isBlank()) "" else "\n$uncategorizedGroups\n  "
            val coverage = categoryCoverage.toJson("    ")
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
                |  "categoryCoverage": $coverage,
                |  "inactiveCatalogMatches": [$candidateBlock],
                |  "missingMerchantGroups": [$groupBlock],
                |  "uncategorizedMerchantGroups": [$uncategorizedGroupBlock]
                |}
                |
            """.trimMargin()
        }
    }

    companion object {
        private fun permille(numerator: Int, denominator: Int): Int =
            if (denominator <= 0) {
                0
            } else {
                ((numerator.toLong() * 1000L) / denominator.toLong()).toInt()
            }

        private val WhitespaceRegex = Regex("""\s+""")
        private val ArabicRegex = Regex("""[\u0600-\u06FF]""")
        private val LatinRegex = Regex("""[A-Za-z]""")
        private val CurrencyRegex = Regex(
            """\b(?:SAR|SR|AED|USD|EUR|GBP|CAD|AUD|CHF|INR|PKR|TRY|EGP|KWD|QAR|BHD|OMR|JOD|JPY|CNY|HKD|SGD)\b|[﷼$€£₹¥]""",
            RegexOption.IGNORE_CASE,
        )
        private val AmountTokenRegex = Regex("""(?<!\p{L})\d{1,3}(?:[,\s.]\d{3})*(?:[.,]\d{2})?(?!\p{L})""")
        private val OtpRegex = Regex("""\b(?:otp|code|رمز|تحقق|verification)\b""", RegexOption.IGNORE_CASE)
        private val MaskedCardRegex = Regex("""(?:\*{2,}|x{2,}|[•●]{2,})\s*\d{2,4}""", RegexOption.IGNORE_CASE)
        private val ActionHintPatterns = listOf(
            "purchase" to Regex("""(?:\b(?:purchase|spent|debit|charge|pos)\b|شراء|خصم|دفع)""", RegexOption.IGNORE_CASE),
            "income" to Regex("""(?:\b(?:credit|deposit|received|salary)\b|إيداع|ايداع|وارد|راتب)""", RegexOption.IGNORE_CASE),
            "transfer" to Regex("""(?:\b(?:transfer|sent|remit)\b|تحويل|حوالة)""", RegexOption.IGNORE_CASE),
            "withdrawal" to Regex("""(?:\b(?:atm|withdrawal)\b|سحب)""", RegexOption.IGNORE_CASE),
            "balance" to Regex("""(?:\b(?:balance|available)\b|رصيد)""", RegexOption.IGNORE_CASE),
        )
    }
}
