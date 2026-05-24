package com.athar.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.domain.model.SmsParseStatus
import com.athar.core.domain.repo.SmsAuditEntry
import com.athar.core.domain.repo.SmsAuditRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class SmsAuditState(
    val entries: ImmutableList<SmsAuditEntry>,
    val totalParsed: Int,
    val totalFailed: Int,
    val totalIgnored: Int,
    val isLoading: Boolean,
) {
    companion object {
        fun initial(): SmsAuditState = SmsAuditState(
            entries = persistentListOf(),
            totalParsed = 0,
            totalFailed = 0,
            totalIgnored = 0,
            isLoading = true,
        )
    }
}

@HiltViewModel
class SmsAuditViewModel @Inject constructor(
    repo: SmsAuditRepository,
) : ViewModel() {

    val state: StateFlow<SmsAuditState> = repo.observeAll()
        .map { all ->
            SmsAuditState(
                entries = all.take(MAX_VISIBLE).toImmutableList(),
                totalParsed = all.count { it.status == SmsParseStatus.PARSED },
                totalFailed = all.count { it.status == SmsParseStatus.FAILED },
                totalIgnored = all.count { it.status == SmsParseStatus.IGNORED },
                isLoading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SmsAuditState.initial())

    private companion object {
        const val MAX_VISIBLE = 200
    }
}
