package com.athar.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.common.money.Money
import com.athar.core.domain.model.Cadence
import com.athar.core.domain.model.MANUAL_ACCOUNT_ID
import com.athar.core.domain.model.RecurringRule
import com.athar.core.domain.model.RecurringSuggestion
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.RecurringRuleRepository
import com.athar.core.domain.repo.RecurringSuggestionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RecurringRulesViewModel @Inject constructor(
    private val rules: RecurringRuleRepository,
    private val suggestionRepo: RecurringSuggestionRepository,
    private val clock: Clock,
) : ViewModel() {

    val state: StateFlow<List<RecurringRule>> =
        rules.observeAll(includeInactive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val suggestions: StateFlow<List<RecurringSuggestion>> =
        suggestionRepo.observeSuggestions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _materializeStatus = MutableStateFlow(0)
    val lastMaterializeCount: StateFlow<Int> = _materializeStatus.asStateFlow()

    fun add(
        displayName: String,
        merchant: String,
        amountText: String,
        currency: String,
        type: TxType,
        categoryId: String?,
        cadence: Cadence,
        dayOfMonth: Int?,
        startDate: LocalDate,
    ) {
        val amount = runCatching { Money.of(BigDecimal(amountText.trim()), currency) }.getOrNull() ?: return
        val now = clock.now()
        val rule = RecurringRule(
            id = UUID.randomUUID().toString(),
            displayName = displayName.trim().ifBlank { merchant },
            merchant = merchant.trim(),
            amount = amount,
            type = type,
            accountId = MANUAL_ACCOUNT_ID,
            categoryId = categoryId,
            cadence = cadence,
            dayOfMonth = dayOfMonth,
            dayOfWeek = null,
            monthOfYear = null,
            nextRunDate = startDate,
            lastRunDate = null,
            isActive = true,
            notes = null,
            createdAt = now,
            updatedAt = now,
        )
        viewModelScope.launch { rules.upsert(rule) }
    }

    fun toggle(id: String, active: Boolean) {
        viewModelScope.launch { rules.setActive(id, active) }
    }

    fun delete(id: String) {
        viewModelScope.launch { rules.delete(id) }
    }

    fun materializeNow() {
        viewModelScope.launch {
            val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            _materializeStatus.value = rules.materializeDue(today)
        }
    }

    /**
     * Convert an auto-detected suggestion into an active recurring rule.
     *
     * TODO: localize the auto-generated `notes` field below (Arabic only for now;
     *  acceptable temporary debt when running in English mode).
     */
    fun acceptSuggestion(suggestion: RecurringSuggestion) {
        val now = clock.now()
        val rule = RecurringRule(
            id = UUID.randomUUID().toString(),
            displayName = suggestion.merchant,
            merchant = suggestion.merchant,
            amount = suggestion.amount,
            type = suggestion.type,
            accountId = MANUAL_ACCOUNT_ID,
            categoryId = null,
            cadence = com.athar.core.domain.model.Cadence.MONTHLY,
            dayOfMonth = suggestion.typicalDayOfMonth,
            dayOfWeek = null,
            monthOfYear = null,
            nextRunDate = suggestion.suggestedNextRun,
            lastRunDate = null,
            isActive = true,
            notes = "اكتُشف تلقائيًا من ${suggestion.occurrenceCount} حركات سابقة.",
            createdAt = now,
            updatedAt = now,
        )
        viewModelScope.launch { rules.upsert(rule) }
    }
}
