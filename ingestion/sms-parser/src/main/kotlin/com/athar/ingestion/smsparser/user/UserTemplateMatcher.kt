package com.athar.ingestion.smsparser.user

import com.athar.core.common.money.Money
import com.athar.core.domain.model.UserTemplate
import com.athar.core.domain.model.UserTemplateAnchorMatcher
import com.athar.ingestion.smsparser.BankTemplate
import com.athar.ingestion.smsparser.ParseResult
import com.athar.ingestion.smsparser.SenderMatcher
import kotlinx.datetime.Instant

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
        val match = UserTemplateAnchorMatcher.match(template, body)
        val amount = match.amount
            ?: return ParseResult.Failed("amount anchor not matched", listOf(id))

        var confidence = 0.6f
        if (match.merchant != null) confidence += 0.1f
        if (match.counterparty != null) confidence += 0.1f

        return ParseResult.Success(
            type = template.txType,
            amount = Money.of(amount),
            merchant = match.merchant,
            counterparty = match.counterparty,
            balanceAfter = null,
            occurredAt = receivedAt,
            confidence = confidence,
            templateId = id,
        )
    }
}
