package com.athar.ingestion.smsparser

import com.athar.core.common.money.Money
import com.athar.core.domain.model.RawIngestEvent
import com.athar.core.domain.model.TxType
import kotlinx.datetime.Instant

/**
 * Pure-Kotlin parser interface. Master Brief §5.5.
 *
 * Implementations register against a sender (e.g. AlRajhiBank) and run a template
 * registry against the body. JVM-testable — no Android imports allowed in this module.
 */
fun interface SmsParser {
    fun parse(event: RawIngestEvent): ParseResult
}

sealed interface ParseResult {
    data class Success(
        val type: TxType,
        val amount: Money,
        val merchant: String?,
        val counterparty: String?,
        val balanceAfter: Money?,
        val occurredAt: Instant?,
        val confidence: Float,
        val templateId: String,
    ) : ParseResult

    data class Failed(
        val reason: String,
        val templateAttempts: List<String>,
    ) : ParseResult

    data object Ignored : ParseResult
}

/**
 * Bank-template strategy. Each template owns the regex set for one bank + transaction kind.
 * Templates are tried in priority order; the first Success wins. Ignored short-circuits the rest.
 */
interface BankTemplate {
    val id: String
    val senderMatcher: SenderMatcher
    fun tryParse(body: String, receivedAt: Instant): ParseResult
}

/** Sender filter — exact, regex, or "any of these". */
sealed interface SenderMatcher {
    fun matches(sender: String): Boolean

    data class Exact(val value: String) : SenderMatcher {
        override fun matches(sender: String) = sender == value
    }

    data class AnyOf(val values: Set<String>) : SenderMatcher {
        override fun matches(sender: String) = sender in values
    }

    data class Regex(val pattern: kotlin.text.Regex) : SenderMatcher {
        override fun matches(sender: String) = pattern.matches(sender)
    }
}
