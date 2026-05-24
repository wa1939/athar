package com.athar.ingestion.smsparser.user

import com.athar.core.common.money.Money
import com.athar.core.domain.model.UserTemplate
import com.athar.ingestion.smsparser.BankTemplate
import com.athar.ingestion.smsparser.Normalize
import com.athar.ingestion.smsparser.ParseResult
import com.athar.ingestion.smsparser.SenderMatcher
import kotlinx.datetime.Instant
import java.math.BigDecimal

/**
 * Adapter: wraps a [UserTemplate] (user-authored from inside the app) so it
 * participates in the parser registry exactly like a built-in [BankTemplate].
 *
 * Matching strategy is **anchor-based** — find `amountAnchorBefore` in the body,
 * skip whitespace + currency symbols, read the number, optionally bound by
 * `amountAnchorAfter`. Same for merchant + counterparty.
 *
 * The instances are short-lived: [UserTemplateRegistry] rebuilds them every time
 * the user adds/removes a template from Settings.
 */
class UserTemplateBankTemplate(
    private val template: UserTemplate,
) : BankTemplate {
    override val id: String = "user:${template.id}"
    override val senderMatcher: SenderMatcher = SenderMatcher.Exact(template.sender)

    override fun tryParse(body: String, receivedAt: Instant): ParseResult {
        val normalized = Normalize.digits(body)
        val amount = extractAmount(normalized, template.amountAnchorBefore, template.amountAnchorAfter)
            ?: return ParseResult.Failed("amount anchor not matched", listOf(id))

        val merchant = template.merchantAnchorBefore?.let {
            extractText(normalized, it, template.merchantAnchorAfter)
        }
        val counterparty = template.counterpartyAnchorBefore?.let {
            extractText(normalized, it, template.counterpartyAnchorAfter)
        }

        var confidence = 0.6f
        if (merchant != null) confidence += 0.1f
        if (counterparty != null) confidence += 0.1f

        return ParseResult.Success(
            type = template.txType,
            amount = Money.of(amount),
            merchant = merchant,
            counterparty = counterparty,
            balanceAfter = null,
            occurredAt = receivedAt,
            confidence = confidence,
            templateId = id,
        )
    }

    private fun extractAmount(body: String, before: String, after: String?): BigDecimal? {
        val idx = body.indexOf(before, ignoreCase = true).takeIf { it >= 0 } ?: return null
        val tail = body.substring(idx + before.length)
        val cleaned = tail.dropWhile { it.isWhitespace() || it in ":·" }
        // Strip leading currency symbols/text so "SAR 200" works.
        val stripped = cleaned.replace(Regex("^(?:SAR|SR|AED|USD|EUR|GBP|INR|PKR|TRY|EGP|﷼|\\$|€|£|₹|ر\\.?\\s*س)\\s*", RegexOption.IGNORE_CASE), "")

        // Number patterns we accept: "1,234.56" / "1 234.56" / "1234.56" / "1234" / "0.50".
        // First alternation REQUIRES at least one thousands separator so plain digit-runs
        // fall to the second alternation and aren't truncated (e.g. "1350" must match
        // wholly, not as "135").
        val numberMatch = Regex("""\d{1,3}(?:[ ,]\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?""").find(stripped)
            ?: return null
        val rawNumber = numberMatch.value.replace(" ", "").replace(",", "")
        val parsed = runCatching { BigDecimal(rawNumber) }.getOrNull() ?: return null

        // If user specified an `amountAnchorAfter`, sanity-check it appears within a
        // reasonable window after the number — otherwise this is probably the wrong number.
        if (after != null) {
            val windowEnd = (numberMatch.range.last + 50).coerceAtMost(stripped.length)
            val window = stripped.substring(numberMatch.range.last + 1, windowEnd)
            if (!window.contains(after, ignoreCase = true)) return null
        }
        return parsed
    }

    private fun extractText(body: String, before: String, after: String?): String? {
        val idx = body.indexOf(before, ignoreCase = true).takeIf { it >= 0 } ?: return null
        val tail = body.substring(idx + before.length).trimStart(' ', '\t', ':', '·')
        val end = if (after != null) {
            tail.indexOf(after, ignoreCase = true).takeIf { it > 0 }
        } else {
            tail.indexOf('\n').takeIf { it > 0 }
        } ?: tail.length
        return tail.substring(0, end).trim().takeIf { it.isNotEmpty() }
    }
}
