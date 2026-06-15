package com.athar.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.common.money.Money
import com.athar.core.domain.model.Cadence
import com.athar.core.domain.model.MANUAL_ACCOUNT_ID
import com.athar.core.domain.model.RecurringRule
import com.athar.core.domain.model.RecurringSuggestion
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.AccountRepository
import com.athar.core.domain.repo.CategoryRepository
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
    private val categoryRepo: CategoryRepository,
    private val accountRepo: AccountRepository,
    private val clock: Clock,
) : ViewModel() {

    val state: StateFlow<List<RecurringRule>> =
        rules.observeAll(includeInactive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val suggestions: StateFlow<List<RecurringSuggestion>> =
        suggestionRepo.observeSuggestions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<com.athar.core.domain.model.Category>> =
        categoryRepo.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<com.athar.core.domain.model.Account>> =
        accountRepo.observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _materializeStatus = MutableStateFlow(0)
    val lastMaterializeCount: StateFlow<Int> = _materializeStatus.asStateFlow()

    private val _lastAccepted = MutableStateFlow<String?>(null)
    val lastAccepted: StateFlow<String?> = _lastAccepted.asStateFlow()

    fun clearLastAccepted() { _lastAccepted.value = null }

    fun add(
        displayName: String,
        merchant: String,
        amountText: String,
        currency: String,
        type: TxType,
        accountId: String?,
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
            accountId = accountId.orManualAccount(),
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

    fun updateRouting(
        id: String,
        accountId: String?,
        categoryId: String?,
    ) {
        viewModelScope.launch {
            val current = rules.get(id) ?: return@launch
            rules.upsert(
                current.copy(
                    accountId = accountId.orManualAccount(current.accountId),
                    categoryId = categoryId,
                    updatedAt = clock.now(),
                ),
            )
        }
    }

    fun materializeNow() {
        viewModelScope.launch {
            val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            _materializeStatus.value = rules.materializeDue(today)
        }
    }

    /**
     * Create a rule from a suggestion using user-confirmed overrides from the confirm sheet.
     * Falls back to auto-detected values when the user accepts the defaults.
     */
    fun acceptSuggestion(
        suggestion: RecurringSuggestion,
        notes: String?,
        cadence: Cadence,
        dayOfMonth: Int?,
        accountId: String?,
        categoryId: String?,
    ) {
        val now = clock.now()
        val rule = RecurringRule(
            id = UUID.randomUUID().toString(),
            displayName = suggestion.merchant,
            merchant = suggestion.merchant,
            amount = suggestion.amount,
            type = suggestion.type,
            accountId = accountId.orManualAccount(suggestion.suggestedAccountId),
            categoryId = categoryId ?: suggestion.suggestedCategoryId,
            cadence = cadence,
            dayOfMonth = if (cadence == Cadence.MONTHLY) dayOfMonth else null,
            dayOfWeek = null,
            monthOfYear = null,
            nextRunDate = suggestion.suggestedNextRun,
            lastRunDate = null,
            isActive = true,
            notes = notes,
            createdAt = now,
            updatedAt = now,
        )
        viewModelScope.launch {
            rules.upsert(rule)
            _lastAccepted.value = suggestion.merchant
        }
    }

    private fun String?.orManualAccount(fallback: String? = null): String =
        this?.takeIf { it.isNotBlank() }
            ?: fallback?.takeIf { it.isNotBlank() }
            ?: MANUAL_ACCOUNT_ID
}
