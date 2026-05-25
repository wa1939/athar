package com.athar.ingestion

import com.athar.core.domain.model.CategorySource
import com.athar.core.domain.model.CategorySuggestion
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.MANUAL_ACCOUNT_ID
import com.athar.core.domain.model.PatternType
import com.athar.core.domain.model.RawIngestEvent
import com.athar.core.domain.model.SmsParseStatus
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.repo.CategoryRuleRepository
import com.athar.core.domain.repo.SmsAuditRepository
import com.athar.core.domain.repo.TransactionRepository
import com.athar.core.domain.repo.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import com.athar.ingestion.smsparser.ParseResult
import com.athar.ingestion.smsparser.SmsParser
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real ingestion pipeline.
 *
 * For each [RawIngestEvent]:
 *   1. Record the raw event in the SMS audit log (status NEW initially).
 *   2. Run the parser. Update audit with the outcome (PARSED / FAILED / IGNORED).
 *   3. On PARSED: categorize via [CategoryRuleRepository], persist a pending [Transaction],
 *      and link `parsedTransactionId` on the audit entry.
 *
 * Idempotency:
 *   - `transactions.sourceRefId` is unique-indexed; re-delivery of the same SMS collapses
 *     into a single row via OnConflictStrategy.REPLACE.
 *   - The audit table uses OnConflictStrategy.IGNORE on insert; duplicate-event records
 *     keep the first row instead of duplicating.
 */
@Singleton
class SmsIngestionPipeline @Inject constructor(
    private val parser: SmsParser,
    private val transactions: TransactionRepository,
    private val rules: CategoryRuleRepository,
    private val audit: SmsAuditRepository,
    private val prefs: UserPreferencesRepository,
    private val clock: Clock,
) {

    suspend fun process(event: RawIngestEvent) {
        val auditId = audit.record(event)
        when (val result = parser.parse(event)) {
            ParseResult.Ignored -> {
                audit.updateParseResult(auditId, SmsParseStatus.IGNORED, parsedTransactionId = null, error = null)
                Timber.d("[ingest] ignored sender=%s", event.sender)
            }
            is ParseResult.Failed -> {
                audit.updateParseResult(
                    auditId,
                    SmsParseStatus.FAILED,
                    parsedTransactionId = null,
                    error = "${result.reason} · tried=${result.templateAttempts.joinToString(",")}",
                )
                Timber.w("[ingest] parse failed: %s (tried %s)", result.reason, result.templateAttempts)
            }
            is ParseResult.Success -> {
                val txId = persist(event, result)
                audit.updateParseResult(
                    auditId,
                    SmsParseStatus.PARSED,
                    parsedTransactionId = txId,
                    error = null,
                )
            }
        }
    }

    private suspend fun persist(event: RawIngestEvent, parsed: ParseResult.Success): String {
        // Self-transfer detection: if the parsed transfer's body or counterparty mentions
        // one of the user's own account-number snippets (e.g. "0930", "4268"), tag the
        // merchant as a self-move so it shows up as a "Possible savings — confirm" item
        // rather than getting filed away as a generic outgoing transfer.
        val ownAccounts = runCatching { prefs.ownAccountNumbers().first() }.getOrDefault(emptyList())
        val isSelfTransfer = parsed.type.name == "TRANSFER" && ownAccounts.isNotEmpty() && (
            ownAccounts.any { acct ->
                acct.isNotBlank() && (
                    parsed.counterparty?.contains(acct, ignoreCase = true) == true ||
                    event.body.contains(acct, ignoreCase = true)
                )
            }
        )

        val effectiveMerchant = when {
            isSelfTransfer -> "تحويل داخلي · ادخار محتمل"
            else -> parsed.merchant ?: parsed.counterparty ?: event.sender
        }
        val merchantNormalized = effectiveMerchant.lowercase().trim()
        val suggestion = categorize(merchantNormalized)
        val now = clock.now()
        val occurredAt = parsed.occurredAt ?: event.receivedAt
        val date = occurredAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
        val txId = UUID.randomUUID().toString()

        // Never auto-dismiss a successfully-parsed transaction. If the SMS got far enough
        // to extract amount + type, money moved — the user must be the one to discard it.
        // CONFIRMED only when the categorizer matched a rule. Everything else (no category
        // match, low confidence, self-transfer) lands in PENDING for explicit user review.
        // Spam-shaped messages are filtered out earlier by IgnorePatterns and never reach
        // this point (they're tagged "ignored" in the SMS audit log instead).
        val confidence = suggestion.confidence
        val initialStatus = when {
            isSelfTransfer -> TxStatus.PENDING
            suggestion.categoryId != null -> TxStatus.CONFIRMED
            else -> TxStatus.PENDING
        }

        val tx = Transaction(
            id = txId,
            accountId = MANUAL_ACCOUNT_ID, // temporary until S-20 lands a real account map per SMS sender
            type = parsed.type,
            amount = parsed.amount,
            date = date,
            occurredAt = occurredAt,
            merchant = effectiveMerchant,
            merchantNormalized = merchantNormalized,
            categoryId = suggestion.categoryId,
            notes = if (isSelfTransfer) "تحويل بين حساباتك — أكِّد ما إذا كان ادخارًا" else null,
            source = if (event.source == IngestSource.NOTIFICATION) IngestSource.NOTIFICATION else IngestSource.SMS,
            sourceRefId = event.rawId,
            status = initialStatus,
            confidence = confidence,
            createdAt = now,
            updatedAt = now,
        )
        transactions.upsert(tx)
        Timber.i(
            "[ingest] OK %s %s %s → cat=%s (%s, conf=%.2f) template=%s",
            parsed.type, parsed.amount.amount.toPlainString(),
            parsed.merchant ?: "?",
            suggestion.categoryId ?: "—",
            suggestion.source,
            suggestion.confidence,
            parsed.templateId,
        )
        return txId
    }

    private suspend fun categorize(merchantNormalized: String): CategorySuggestion {
        val matches = rules.findMatching(merchantNormalized)
        val best = matches.firstOrNull()
            ?: return CategorySuggestion(null, 0f, CategorySource.UNKNOWN, null)
        val confidence = when (best.patternType) {
            PatternType.EXACT -> 1.0f
            PatternType.SUBSTRING -> 0.85f
            PatternType.REGEX -> 0.80f
        }
        val sourceTier = when (best.patternType) {
            PatternType.EXACT -> CategorySource.RULE_EXACT
            PatternType.SUBSTRING -> CategorySource.RULE_SUBSTRING
            PatternType.REGEX -> CategorySource.RULE_REGEX
        }
        return CategorySuggestion(best.categoryId, confidence, sourceTier, best.id)
    }

    private companion object {
    }
}
