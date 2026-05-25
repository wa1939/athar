package com.athar.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.common.money.Money
import com.athar.core.domain.model.Account
import com.athar.core.domain.model.AccountBalance
import com.athar.core.domain.model.AccountType
import com.athar.core.domain.model.NetWorth
import com.athar.core.domain.repo.AccountRepository
import com.athar.core.domain.repo.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

/**
 * Accounts CRUD screen (G-4). Surfaces:
 *  - the user's full account list (active + archived) with computed balances
 *  - a no-FX net-worth total in the user's chosen display currency
 *  - add/edit/archive/delete operations
 *
 * Delete relies on a FK RESTRICT on transactions.accountId — calling delete on an
 * account that still has referencing transactions raises an exception. We catch it
 * and surface it through [errorMessage] so the UI can render the failure inline.
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val repo: AccountRepository,
    private val prefs: UserPreferencesRepository,
    private val clock: Clock,
) : ViewModel() {

    val balances: StateFlow<List<AccountBalance>> =
        repo.observeBalances()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val netWorth: StateFlow<NetWorth> =
        prefs.displayCurrency()
            .flatMapLatest { currency -> repo.observeNetWorth(currency) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = NetWorth(
                    total = Money.zero(),
                    byCurrency = emptyMap(),
                    accounts = emptyList(),
                ),
            )

    // TODO: localize ViewModel-emitted error fallbacks (currently Arabic-only;
    //  acceptable temporary debt when running in English mode).
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun add(
        name: String,
        type: AccountType,
        currency: String,
        openingBalanceText: String,
        notes: String,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        val amountText = openingBalanceText.trim().ifEmpty { "0" }
        val opening = runCatching { Money.of(BigDecimal(amountText), currency) }.getOrNull() ?: return

        viewModelScope.launch {
            val now = clock.now()
            val nextSort = (balances.value.maxOfOrNull { it.account.sortOrder } ?: 0) + 1
            val account = Account(
                id = UUID.randomUUID().toString(),
                name = trimmedName,
                type = type,
                currency = currency,
                openingBalance = opening,
                smsSenders = emptyList(),
                notes = notes.trim().ifBlank { null },
                sortOrder = nextSort,
                archived = false,
                createdAt = now,
                updatedAt = now,
            )
            runCatching { repo.upsert(account) }
                .onFailure { e -> _errorMessage.value = e.message ?: "تعذّر حفظ الحساب." }
        }
    }

    fun update(account: Account) {
        viewModelScope.launch {
            runCatching { repo.upsert(account) }
                .onFailure { e -> _errorMessage.value = e.message ?: "تعذّر تحديث الحساب." }
        }
    }

    fun setArchived(id: String, archived: Boolean) {
        viewModelScope.launch {
            runCatching { repo.setArchived(id, archived) }
                .onFailure { e -> _errorMessage.value = e.message ?: "تعذّر تغيير حالة الحساب." }
        }
    }

    /**
     * Delete an account. Throws via the repository if any transactions still reference
     * it (FK RESTRICT). We catch and surface a user-facing message rather than crash.
     */
    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { repo.delete(id) }
                .onFailure { e ->
                    _errorMessage.value =
                        "تعذّر حذف الحساب: هناك حركات مرتبطة به. اعتبره مؤرشفًا بدلاً من ذلك."
                }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /** Convenience for the screen — current display currency for default new-account form. */
    suspend fun defaultCurrency(): String = prefs.displayCurrency().first()
}
