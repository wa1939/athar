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
    val parseRatePercent: Int,
    val senderHealth: ImmutableList<SmsSenderHealth>,
    val isLoading: Boolean,
) {
    companion object {
        fun initial(): SmsAuditState = SmsAuditState(
            entries = persistentListOf(),
            totalParsed = 0,
            totalFailed = 0,
            totalIgnored = 0,
            parseRatePercent = 0,
            senderHealth = persistentListOf(),
            isLoading = true,
        )
    }
}

@Immutable
data class SmsSenderHealth(
    val sender: String,
    val total: Int,
    val parsed: Int,
    val failed: Int,
    val ignored: Int,
)

@HiltViewModel
class SmsAuditViewModel @Inject constructor(
    repo: SmsAuditRepository,
) : ViewModel() {

    val state: StateFlow<SmsAuditState> = repo.observeAll()
        .map(::buildSmsAuditState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SmsAuditState.initial())
}

internal fun buildSmsAuditState(all: List<SmsAuditEntry>): SmsAuditState {
    val parsed = all.count { it.status == SmsParseStatus.PARSED }
    val failed = all.count { it.status == SmsParseStatus.FAILED }
    val ignored = all.count { it.status == SmsParseStatus.IGNORED }
    val senderHealth = all.groupBy { it.sender.ifBlank { "—" } }
        .map { (sender, rows) ->
            SmsSenderHealth(
                sender = sender,
                total = rows.size,
                parsed = rows.count { it.status == SmsParseStatus.PARSED },
                failed = rows.count { it.status == SmsParseStatus.FAILED },
                ignored = rows.count { it.status == SmsParseStatus.IGNORED },
            )
        }
        .sortedWith(
            compareByDescending<SmsSenderHealth> { it.failed }
                .thenByDescending { it.ignored }
                .thenByDescending { it.total }
                .thenBy { it.sender.lowercase() },
        )
        .take(MAX_SENDER_HEALTH)
        .toImmutableList()

    return SmsAuditState(
        entries = all.take(MAX_VISIBLE).toImmutableList(),
        totalParsed = parsed,
        totalFailed = failed,
        totalIgnored = ignored,
        parseRatePercent = if (all.isEmpty()) 0 else ((parsed * 100) / all.size),
        senderHealth = senderHealth,
        isLoading = false,
    )
}

private const val MAX_VISIBLE = 200
private const val MAX_SENDER_HEALTH = 5
