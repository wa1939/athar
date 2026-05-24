package com.athar.ingestion.smsparser

import com.athar.core.domain.model.RawIngestEvent

/**
 * Parser implementation that tries each [BankTemplate] in order, returning the first
 * Success or Ignored. Failed results are aggregated for diagnostics.
 *
 * Order matters: more specific templates should come first (balance-alert before purchase),
 * so balance/OTP/marketing messages exit early instead of getting parsed as zero-amount.
 */
class TemplateBasedSmsParser(
    private val templates: List<BankTemplate>,
) : SmsParser {

    override fun parse(event: RawIngestEvent): ParseResult {
        val applicable = templates.filter { it.senderMatcher.matches(event.sender) }
        if (applicable.isEmpty()) return ParseResult.Ignored

        val attempts = mutableListOf<String>()
        for (template in applicable) {
            when (val result = template.tryParse(event.body, event.receivedAt)) {
                is ParseResult.Success -> return result
                is ParseResult.Ignored -> return result
                is ParseResult.Failed -> attempts += template.id
            }
        }
        return ParseResult.Failed(
            reason = "no template matched (${attempts.size} attempts)",
            templateAttempts = attempts,
        )
    }
}
