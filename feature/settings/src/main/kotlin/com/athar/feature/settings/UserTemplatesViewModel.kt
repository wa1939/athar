package com.athar.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.domain.model.TxType
import com.athar.core.domain.model.UserTemplate
import com.athar.core.domain.repo.UserTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class UserTemplatesViewModel @Inject constructor(
    private val repository: UserTemplateRepository,
    private val clock: Clock,
) : ViewModel() {

    val templates: StateFlow<List<UserTemplate>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(form: TemplateForm) {
        if (!form.isValid) return
        val template = UserTemplate(
            id = UUID.randomUUID().toString(),
            displayName = form.displayName.trim(),
            sender = form.sender.trim(),
            txType = form.txType,
            amountAnchorBefore = form.amountAnchorBefore.trim(),
            amountAnchorAfter = form.amountAnchorAfter.trim().takeIf { it.isNotEmpty() },
            merchantAnchorBefore = form.merchantAnchorBefore.trim().takeIf { it.isNotEmpty() },
            merchantAnchorAfter = form.merchantAnchorAfter.trim().takeIf { it.isNotEmpty() },
            counterpartyAnchorBefore = form.counterpartyAnchorBefore.trim().takeIf { it.isNotEmpty() },
            counterpartyAnchorAfter = form.counterpartyAnchorAfter.trim().takeIf { it.isNotEmpty() },
            sampleBody = form.sampleBody.trim(),
            createdAt = clock.now(),
        )
        viewModelScope.launch { repository.upsert(template) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}

data class TemplateForm(
    val displayName: String = "",
    val sender: String = "",
    val txType: TxType = TxType.EXPENSE,
    val amountAnchorBefore: String = "",
    val amountAnchorAfter: String = "",
    val merchantAnchorBefore: String = "",
    val merchantAnchorAfter: String = "",
    val counterpartyAnchorBefore: String = "",
    val counterpartyAnchorAfter: String = "",
    val sampleBody: String = "",
) {
    val isValid: Boolean
        get() = displayName.isNotBlank() && sender.isNotBlank() && amountAnchorBefore.isNotBlank()
}
