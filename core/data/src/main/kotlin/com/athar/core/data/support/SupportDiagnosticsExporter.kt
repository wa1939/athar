package com.athar.core.data.support

import com.athar.core.data.db.dao.SmsMessageDao
import com.athar.core.data.db.entity.SmsMessageEntity
import com.athar.core.domain.model.SmsParseStatus
import com.athar.core.domain.repo.SupportDiagnosticsExportResult
import com.athar.core.domain.repo.SupportDiagnosticsExportTrigger
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes an opt-in support report that helps diagnose SMS parser coverage without
 * exposing the user's financial messages. Raw sender/body values are never serialized.
 */
@Singleton
internal class SupportDiagnosticsExporter @Inject constructor(
    private val smsDao: SmsMessageDao,
    private val clock: Clock,
) : SupportDiagnosticsExportTrigger {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    override suspend fun exportDiagnostics(out: OutputStream): SupportDiagnosticsExportResult =
        runCatching {
            val counts = smsDao.statusCounts()
            val countMap = counts.associate { it.status to it.count }
            val payload = SupportDiagnosticsReportBuilder.build(
                rows = smsDao.recent(SupportDiagnosticsReportBuilder.MaxRecentSamples),
                totalRows = counts.sumOf { it.count },
                statusCounts = countMap,
                generatedAt = clock.now().toString(),
            )
            OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                writer.write(json.encodeToString(payload))
                writer.flush()
            }
            payload.summary
        }.fold(
            onSuccess = { summary ->
                Timber.i("Support diagnostics export: %d SMS audit rows", summary.totalAuditRows)
                SupportDiagnosticsExportResult.Done(
                    auditRows = summary.totalAuditRows,
                    parsed = summary.parsed,
                    failed = summary.failed,
                    ignored = summary.ignored,
                )
            },
            onFailure = {
                Timber.e(it, "Support diagnostics export failed")
                SupportDiagnosticsExportResult.Failed(it.message ?: it::class.simpleName.orEmpty())
            },
        )
}

internal object SupportDiagnosticsReportBuilder {
    const val MaxRecentSamples = 500
    private const val TopGroupLimit = 25
    private const val MaxTemplateIdsPerGroup = 12

    fun build(
        rows: List<SmsMessageEntity>,
        totalRows: Int,
        statusCounts: Map<String, Int>,
        generatedAt: String,
    ): SupportDiagnosticsPayload {
        val recentRows = rows.take(MaxRecentSamples)
        val normalizedCounts = statusCounts
            .toList()
            .sortedWith(compareBy<Pair<String, Int>> { statusPriority(it.first) }.thenBy { it.first })
        val summary = DiagnosticsSummary(
            totalAuditRows = totalRows,
            includedRecentRows = recentRows.size,
            truncated = totalRows > recentRows.size,
            parsed = statusCounts[SmsParseStatus.PARSED.name] ?: 0,
            failed = statusCounts[SmsParseStatus.FAILED.name] ?: 0,
            ignored = statusCounts[SmsParseStatus.IGNORED.name] ?: 0,
            newRows = statusCounts["NEW"] ?: 0,
        )
        return SupportDiagnosticsPayload(
            generatedAt = generatedAt,
            summary = summary,
            statusCounts = normalizedCounts.map { StatusCount(it.first, it.second) },
            senderGroups = buildSenderGroups(recentRows),
            errorGroups = buildErrorGroups(recentRows),
            recentAuditSamples = recentRows.map { it.toAuditSample() },
        )
    }

    private fun buildSenderGroups(rows: List<SmsMessageEntity>): List<SenderGroup> =
        rows.groupBy { it.senderHash() }
            .map { (hash, groupedRows) ->
                SenderGroup(
                    senderHash = hash,
                    senderKind = senderKind(groupedRows.first().sender),
                    senderLengthBucket = bucket(groupedRows.first().sender.length),
                    sampleCount = groupedRows.size,
                    parsed = groupedRows.countStatus(SmsParseStatus.PARSED.name),
                    failed = groupedRows.countStatus(SmsParseStatus.FAILED.name),
                    ignored = groupedRows.countStatus(SmsParseStatus.IGNORED.name),
                    firstSeen = groupedRows.minOf { it.receivedAt }.toString(),
                    lastSeen = groupedRows.maxOf { it.receivedAt }.toString(),
                )
            }
            .sortedWith(compareByDescending<SenderGroup> { it.sampleCount }.thenBy { it.senderHash })
            .take(TopGroupLimit)

    private fun buildErrorGroups(rows: List<SmsMessageEntity>): List<ErrorGroup> =
        rows.filter { it.parseStatus == SmsParseStatus.FAILED.name }
            .groupBy { row ->
                val reason = row.reasonWithoutTemplateAttempts()
                ErrorGroupKey(category = classifyError(reason), redactedError = redactSensitiveText(reason))
            }
            .map { (key, groupedRows) ->
                val templateIds = groupedRows
                    .flatMap { templateAttempts(it.parseError.orEmpty()) }
                    .distinct()
                    .sorted()
                    .take(MaxTemplateIdsPerGroup)
                ErrorGroup(
                    category = key.category,
                    redactedError = key.redactedError,
                    count = groupedRows.size,
                    firstSeen = groupedRows.minOf { it.receivedAt }.toString(),
                    lastSeen = groupedRows.maxOf { it.receivedAt }.toString(),
                    templateAttemptCount = groupedRows.maxOfOrNull { templateAttempts(it.parseError.orEmpty()).size } ?: 0,
                    templateAttempts = templateIds,
                )
            }
            .sortedWith(compareByDescending<ErrorGroup> { it.count }.thenBy { it.category })
            .take(TopGroupLimit)

    private fun SmsMessageEntity.toAuditSample(): AuditSample {
        val reason = reasonWithoutTemplateAttempts()
        return AuditSample(
            rowIdHash = sha256Prefix(id),
            senderHash = senderHash(),
            senderKind = senderKind(sender),
            senderLengthBucket = bucket(sender.length),
            receivedAt = receivedAt.toString(),
            status = parseStatus,
            hasParsedTransaction = parsedTransactionId != null,
            bodyShapeSha256 = sha256Prefix(bodyShapeSignature(body)),
            bodyFeatures = bodyFeatures(body),
            errorCategory = classifyError(reason),
            redactedError = if (reason.isBlank()) null else redactSensitiveText(reason),
            templateAttempts = templateAttempts(parseError.orEmpty()).take(MaxTemplateIdsPerGroup),
        )
    }

    private fun List<SmsMessageEntity>.countStatus(status: String): Int =
        count { it.parseStatus == status }

    private fun SmsMessageEntity.senderHash(): String = sha256Prefix(sender.trim().lowercase())

    private fun SmsMessageEntity.reasonWithoutTemplateAttempts(): String =
        parseError.orEmpty()
            .substringBefore("· tried=")
            .substringBefore("tried=")
            .trim()

    private fun statusPriority(status: String): Int = when (status) {
        SmsParseStatus.PARSED.name -> 0
        SmsParseStatus.FAILED.name -> 1
        SmsParseStatus.IGNORED.name -> 2
        "NEW" -> 3
        else -> 4
    }

    private fun classifyError(reason: String): String {
        val text = reason.lowercase()
        return when {
            text.isBlank() -> "none"
            "no template matched" in text -> "template_miss"
            "amount" in text || "monetary" in text -> "amount_parse"
            "date" in text -> "date_parse"
            "merchant" in text -> "merchant_parse"
            "ignore" in text || "ignorable" in text -> "ignore_miss"
            "exception" in text || "illegal" in text || "error" in text -> "exception"
            text.startsWith("not ") -> "template_miss"
            else -> "other"
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
            actionHints = actionHints(body),
        )
    }

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

    private fun actionHints(body: String): List<String> =
        ActionHintPatterns.mapNotNull { (hint, regex) -> hint.takeIf { regex.containsMatchIn(body) } }

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

    private fun templateAttempts(error: String): List<String> =
        TemplateAttemptsRegex.find(error)
            ?.groupValues
            ?.getOrNull(1)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private fun redactSensitiveText(input: String): String {
        val normalized = normalizeArabicDigits(input)
        return normalized
            .replace(EmailRegex, "[email]")
            .replace(IbanRegex, "[iban]")
            .replace(LongNumberRegex, "[number]")
            .replace(AnyNumberRegex, "[number]")
            .replace(WhitespaceRegex, " ")
            .trim()
            .take(160)
    }

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

    private fun sha256Prefix(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            .take(12)

    private val WhitespaceRegex = Regex("\\s+")
    private val ArabicRegex = Regex("[\\u0600-\\u06FF]")
    private val LatinRegex = Regex("[A-Za-z]")
    private val CurrencyRegex = Regex("(?i)\\b(SAR|SR|USD|AED|KWD|QAR|BHD|OMR|JOD|EUR|GBP)\\b|ر\\.?س|ريال")
    private val AmountTokenRegex = Regex("\\b\\d{1,3}(?:[, ]\\d{3})*(?:\\.\\d+)?\\b|\\b\\d+\\.\\d+\\b")
    private val OtpRegex = Regex("(?i)\\b(otp|code|verification|pin)\\b|رمز|تحقق|كود")
    private val MaskedCardRegex = Regex("(?i)(\\*{2,}|x{2,}|ending|card ending|بطاقة)")
    private val TemplateAttemptsRegex = Regex("tried=([^\\s]+)")
    private val EmailRegex = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val IbanRegex = Regex("\\b[A-Z]{2}\\d{2}[A-Z0-9]{10,30}\\b", RegexOption.IGNORE_CASE)
    private val LongNumberRegex = Regex("\\+?\\d[\\d\\s().,/_-]{5,}\\d")
    private val AnyNumberRegex = Regex("\\d+")
    private val ActionHintPatterns = listOf(
        "purchase" to Regex("(?i)\\b(pos|purchase|debit card|online purchase)\\b|شراء|نقاط البيع"),
        "transfer" to Regex("(?i)\\b(transfer|sarie)\\b|تحويل|حوالة"),
        "deposit" to Regex("(?i)\\b(deposit|credit)\\b|إيداع|ايداع"),
        "withdrawal" to Regex("(?i)\\b(withdrawal|atm)\\b|سحب"),
        "payment" to Regex("(?i)\\b(payment|bill)\\b|سداد|دفع"),
        "declined" to Regex("(?i)\\b(declined|rejected|failed)\\b|مرفوض|فشلت"),
        "otp" to OtpRegex,
        "promo" to Regex("(?i)\\b(offer|discount|cashback|promo)\\b|عرض|خصم|استرداد"),
    )
}

private data class ErrorGroupKey(
    val category: String,
    val redactedError: String,
)

@Serializable
internal data class SupportDiagnosticsPayload(
    val schema: String = "athar-support-diagnostics-v1",
    @SerialName("generated_at") val generatedAt: String,
    val privacy: DiagnosticsPrivacy = DiagnosticsPrivacy(),
    val summary: DiagnosticsSummary,
    @SerialName("status_counts") val statusCounts: List<StatusCount>,
    @SerialName("sender_groups") val senderGroups: List<SenderGroup>,
    @SerialName("error_groups") val errorGroups: List<ErrorGroup>,
    @SerialName("recent_audit_samples") val recentAuditSamples: List<AuditSample>,
)

@Serializable
internal data class DiagnosticsPrivacy(
    @SerialName("raw_sms_bodies") val rawSmsBodies: String = "omitted",
    @SerialName("raw_senders") val rawSenders: String = "omitted",
    val balances: String = "omitted",
    val amounts: String = "omitted",
    val accounts: String = "omitted",
    val uploads: String = "none; user saves and shares the file manually",
)

@Serializable
internal data class DiagnosticsSummary(
    @SerialName("total_audit_rows") val totalAuditRows: Int,
    @SerialName("included_recent_rows") val includedRecentRows: Int,
    val truncated: Boolean,
    val parsed: Int,
    val failed: Int,
    val ignored: Int,
    @SerialName("new_rows") val newRows: Int,
)

@Serializable
internal data class StatusCount(
    val status: String,
    val count: Int,
)

@Serializable
internal data class SenderGroup(
    @SerialName("sender_hash") val senderHash: String,
    @SerialName("sender_kind") val senderKind: String,
    @SerialName("sender_length_bucket") val senderLengthBucket: String,
    @SerialName("sample_count") val sampleCount: Int,
    val parsed: Int,
    val failed: Int,
    val ignored: Int,
    @SerialName("first_seen") val firstSeen: String,
    @SerialName("last_seen") val lastSeen: String,
)

@Serializable
internal data class ErrorGroup(
    val category: String,
    @SerialName("redacted_error") val redactedError: String,
    val count: Int,
    @SerialName("first_seen") val firstSeen: String,
    @SerialName("last_seen") val lastSeen: String,
    @SerialName("template_attempt_count") val templateAttemptCount: Int,
    @SerialName("template_attempts") val templateAttempts: List<String>,
)

@Serializable
internal data class AuditSample(
    @SerialName("row_id_hash") val rowIdHash: String,
    @SerialName("sender_hash") val senderHash: String,
    @SerialName("sender_kind") val senderKind: String,
    @SerialName("sender_length_bucket") val senderLengthBucket: String,
    @SerialName("received_at") val receivedAt: String,
    val status: String,
    @SerialName("has_parsed_transaction") val hasParsedTransaction: Boolean,
    @SerialName("body_shape_sha256_12") val bodyShapeSha256: String,
    @SerialName("body_features") val bodyFeatures: BodyFeatures,
    @SerialName("error_category") val errorCategory: String,
    @SerialName("redacted_error") val redactedError: String?,
    @SerialName("template_attempts") val templateAttempts: List<String>,
)

@Serializable
internal data class BodyFeatures(
    @SerialName("length_bucket") val lengthBucket: String,
    @SerialName("line_count") val lineCount: Int,
    @SerialName("token_count") val tokenCount: Int,
    @SerialName("has_arabic") val hasArabic: Boolean,
    @SerialName("has_latin") val hasLatin: Boolean,
    @SerialName("has_currency_marker") val hasCurrencyMarker: Boolean,
    @SerialName("amount_token_count") val amountTokenCount: Int,
    @SerialName("has_otp_marker") val hasOtpMarker: Boolean,
    @SerialName("has_masked_card_marker") val hasMaskedCardMarker: Boolean,
    @SerialName("action_hints") val actionHints: List<String>,
)
