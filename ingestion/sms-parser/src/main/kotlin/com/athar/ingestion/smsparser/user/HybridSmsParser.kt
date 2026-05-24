package com.athar.ingestion.smsparser.user

import com.athar.core.domain.model.RawIngestEvent
import com.athar.core.domain.model.UserTemplate
import com.athar.ingestion.smsparser.BankTemplate
import com.athar.ingestion.smsparser.ParseResult
import com.athar.ingestion.smsparser.SmsParser
import com.athar.ingestion.smsparser.TemplateBasedSmsParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Composes built-in [BankTemplate]s with user-authored [UserTemplate]s.
 *
 * Order:
 *   1. User templates (most specific to the user's banks — checked FIRST so they
 *      win over fallbacks like UniversalAmountTemplate).
 *   2. Built-in templates (the static list from IngestionModule).
 *
 * Templates can be swapped at runtime by [updateUserTemplates] without restarting
 * the app — the call rebuilds the internal parser. The UI uses this whenever the
 * user adds or removes a template from Settings.
 */
class HybridSmsParser(
    private val builtInTemplates: List<BankTemplate>,
) : SmsParser {

    private val state: MutableStateFlow<TemplateBasedSmsParser> =
        MutableStateFlow(TemplateBasedSmsParser(builtInTemplates))

    /** Snapshot of the user-template list currently feeding the parser. */
    val userTemplates: StateFlow<List<UserTemplate>> = MutableStateFlow<List<UserTemplate>>(emptyList()).asStateFlow()
    private val mutableUserTemplates = MutableStateFlow<List<UserTemplate>>(emptyList())

    fun updateUserTemplates(templates: List<UserTemplate>) {
        mutableUserTemplates.value = templates
        val wrapped = templates.map { UserTemplateBankTemplate(it) }
        // User templates first so they override fallbacks; built-ins after so
        // a corrupted user template doesn't break a working bank-specific match.
        state.value = TemplateBasedSmsParser(wrapped + builtInTemplates)
    }

    override fun parse(event: RawIngestEvent): ParseResult = state.value.parse(event)
}
