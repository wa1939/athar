package com.athar.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.common.money.Money
import com.athar.core.domain.model.Account
import com.athar.core.domain.model.AccountBalance
import com.athar.core.domain.model.AccountRouting
import com.athar.core.domain.model.AccountType
import com.athar.core.domain.model.NetWorth
import com.athar.core.domain.repo.AccountRepository
import com.athar.core.domain.repo.ReconcileResult
import com.athar.core.domain.repo.UserPreferencesRepository
import android.util.Log
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

enum class AccountError {
    SAVE_FAILED,
    UPDATE_FAILED,
    ARCHIVE_FAILED,
    DELETE_HAS_TRANSACTIONS,
}

/**
 * Outcome of a reconcile call surfaced to the UI for a 3-second olive toast.
 * `delta` is the signed minor-unit adjustment (positive when income was added, negative
 * when expense was added). `currency` is the account's currency so the UI can format.
 */
sealed interface ReconcileEvent {
    data class Done(val deltaMinor: Long, val currency: String) : ReconcileEvent
    data class NoChange(val accountId: String) : ReconcileEvent
    data class Failed(val reason: String) : ReconcileEvent
}

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

    private val _error = MutableStateFlow<AccountError?>(null)
    val error: StateFlow<AccountError?> = _error.asStateFlow()

    /**
     * Detail string captured from the last failed mutation (exception message). Surfaced under
     * the generic error label so the user (and we) can actually tell *why* save/update/archive
     * failed instead of staring at "تعذّر تحديث الحساب" with no recourse.
     */
    private val _errorDetail = MutableStateFlow<String?>(null)
    val errorDetail: StateFlow<String?> = _errorDetail.asStateFlow()

    fun add(
        name: String,
        type: AccountType,
        currency: String,
        openingBalanceText: String,
        smsSendersText: String,
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
                smsSenders = AccountRouting.normalizeAliases(smsSendersText),
                notes = notes.trim().ifBlank { null },
                sortOrder = nextSort,
                archived = false,
                createdAt = now,
                updatedAt = now,
            )
            runCatching { repo.upsert(account) }
                .onFailure { reportFailure(AccountError.SAVE_FAILED, "add(${account.name})", it) }
        }
    }

    fun update(account: Account) {
        viewModelScope.launch {
            runCatching { repo.upsert(account) }
                .onFailure { reportFailure(AccountError.UPDATE_FAILED, "update(${account.id})", it) }
        }
    }

    fun setArchived(id: String, archived: Boolean) {
        viewModelScope.launch {
            runCatching { repo.setArchived(id, archived) }
                .onFailure { reportFailure(AccountError.ARCHIVE_FAILED, "setArchived($id, $archived)", it) }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { repo.delete(id) }
                .onFailure { reportFailure(AccountError.DELETE_HAS_TRANSACTIONS, "delete($id)", it) }
        }
    }

    private val _reconcileEvent = MutableStateFlow<ReconcileEvent?>(null)
    val reconcileEvent: StateFlow<ReconcileEvent?> = _reconcileEvent.asStateFlow()
    fun clearReconcileEvent() { _reconcileEvent.value = null }

    /**
     * "Match my bank balance" entry point. Caller passes the [label] string already
     * resolved to the active locale ("تسوية يدوية" / "Manual adjustment") so the VM
     * stays Context-free. Empty target string → ignored.
     */
    fun reconcile(accountId: String, targetText: String, note: String?, label: String) {
        val account = balances.value.firstOrNull { it.account.id == accountId }?.account ?: return
        val parsed = runCatching {
            Money.of(BigDecimal(targetText.trim().replace(",", "")), account.currency)
        }.getOrNull() ?: run {
            _reconcileEvent.value = ReconcileEvent.Failed(reason = "Couldn't parse '$targetText' as a number.")
            return
        }
        viewModelScope.launch {
            when (val r = repo.reconcile(accountId, parsed, label, note?.takeIf { it.isNotBlank() })) {
                is ReconcileResult.Done -> {
                    _reconcileEvent.value = if (r.adjustmentMinor == 0L) {
                        ReconcileEvent.NoChange(accountId)
                    } else {
                        ReconcileEvent.Done(r.adjustmentMinor, account.currency)
                    }
                }
                is ReconcileResult.Failed -> {
                    Log.w("AccountsViewModel", "reconcile($accountId) failed: ${r.reason}")
                    _reconcileEvent.value = ReconcileEvent.Failed(r.reason)
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
        _errorDetail.value = null
    }

    private fun reportFailure(kind: AccountError, op: String, t: Throwable) {
        Log.e("AccountsViewModel", "$op failed", t)
        _error.value = kind
        _errorDetail.value = t.message?.take(280) ?: t::class.simpleName
    }

    /** Convenience for the screen — current display currency for default new-account form. */
    suspend fun defaultCurrency(): String = prefs.displayCurrency().first()
}
