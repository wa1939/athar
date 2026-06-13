package com.athar.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.common.money.Money
import com.athar.core.domain.model.CategoryKind
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.MANUAL_ACCOUNT_ID
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.CategoryRepository
import com.athar.core.domain.repo.TransactionRepository
import com.athar.core.domain.repo.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val prefs: UserPreferencesRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(AddTransactionState.initial(today()))
    val state: StateFlow<AddTransactionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AddTransactionResult>(extraBufferCapacity = 1)
    val events: SharedFlow<AddTransactionResult> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            categories.observeAll().collect { all ->
                val expense = all.filter { it.kind == CategoryKind.EXPENSE }.toImmutableList()
                val income = all.filter { it.kind == CategoryKind.INCOME }.toImmutableList()
                _state.update { it.copy(expenseCategories = expense, incomeCategories = income) }
            }
        }
        viewModelScope.launch {
            combine(transactions.observeAll(), prefs.displayCurrency()) { all, currency ->
                ManualEntrySuggestionBuilder.build(all, displayCurrency = currency).toImmutableList()
            }.collect { suggestions ->
                _state.update { it.copy(merchantSuggestions = suggestions) }
            }
        }
    }

    fun onEvent(event: AddTransactionEvent) {
        when (event) {
            is AddTransactionEvent.SetAmount -> _state.update { it.copy(amount = event.value, validationError = null) }
            is AddTransactionEvent.SetMerchant -> _state.update { it.copy(merchant = event.value, validationError = null) }
            is AddTransactionEvent.SetNotes -> _state.update { it.copy(notes = event.value) }
            is AddTransactionEvent.SetQuickEntry -> _state.update {
                it.copy(quickEntry = event.value, quickEntryError = null)
            }
            AddTransactionEvent.ApplyQuickEntry -> _state.update {
                ManualEntryPhraseApplier.apply(it, it.quickEntry)
            }
            is AddTransactionEvent.ApplyVoiceTranscript -> _state.update {
                ManualEntryPhraseApplier.apply(it.copy(quickEntry = event.value), event.value)
            }
            AddTransactionEvent.VoiceUnavailable -> _state.update {
                it.copy(quickEntryError = QuickEntryError.VOICE_UNAVAILABLE)
            }
            is AddTransactionEvent.SetType -> _state.update {
                it.copy(type = event.type, selectedCategoryId = null, validationError = null)
            }
            is AddTransactionEvent.SetDate -> _state.update { it.copy(date = event.date) }
            is AddTransactionEvent.SelectCategory -> _state.update {
                it.copy(selectedCategoryId = event.categoryId, validationError = null)
            }
            is AddTransactionEvent.ApplySuggestion -> _state.update {
                it.copy(
                    amount = event.suggestion.amountInput ?: it.amount,
                    merchant = event.suggestion.merchant,
                    type = event.suggestion.type,
                    selectedCategoryId = event.suggestion.categoryId,
                    validationError = null,
                )
            }
            AddTransactionEvent.Save -> save()
        }
    }

    private fun save() {
        val s = _state.value
        val validation = validate(s)
        if (validation != null) {
            _state.update { it.copy(validationError = validation) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val now = clock.now()
            val currency = prefs.displayCurrency().first()
            val tx = Transaction(
                id = UUID.randomUUID().toString(),
                accountId = MANUAL_ACCOUNT_ID,
                type = s.type,
                amount = Money.of(BigDecimal(s.amount), currency),
                date = s.date,
                occurredAt = now,
                merchant = s.merchant.trim(),
                merchantNormalized = s.merchant.lowercase().trim(),
                categoryId = s.selectedCategoryId,
                notes = s.notes.takeIf { it.isNotBlank() },
                source = IngestSource.MANUAL,
                sourceRefId = null,
                status = TxStatus.CONFIRMED,
                confidence = 1.0f,
                createdAt = now,
                updatedAt = now,
            )
            transactions.upsert(tx)
            _events.tryEmit(AddTransactionResult.Saved)
            _state.update {
                AddTransactionState.initial(today()).copy(
                    expenseCategories = s.expenseCategories,
                    incomeCategories = s.incomeCategories,
                    merchantSuggestions = s.merchantSuggestions,
                )
            }
        }
    }

    private fun validate(s: AddTransactionState): ValidationError? {
        if (s.amount.isBlank()) return ValidationError.AMOUNT_REQUIRED
        val amt = runCatching { BigDecimal(s.amount) }.getOrNull() ?: return ValidationError.AMOUNT_INVALID
        if (amt.signum() <= 0) return ValidationError.AMOUNT_INVALID
        if (s.merchant.isBlank()) return ValidationError.MERCHANT_REQUIRED
        if (s.selectedCategoryId == null) return ValidationError.CATEGORY_REQUIRED
        return null
    }

    private fun today() = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}

sealed interface AddTransactionResult {
    data object Saved : AddTransactionResult
}
