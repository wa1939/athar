package com.athar.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.domain.repo.ActivityLogEntry
import com.athar.core.domain.repo.ActivityLogRepository
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
data class ActivityLogState(
    val entries: ImmutableList<ActivityLogEntry>,
    val isLoading: Boolean,
) {
    companion object {
        fun initial(): ActivityLogState = ActivityLogState(persistentListOf(), isLoading = true)
    }
}

@HiltViewModel
class ActivityLogViewModel @Inject constructor(
    repo: ActivityLogRepository,
) : ViewModel() {
    val state: StateFlow<ActivityLogState> = repo.observeRecent(limit = 200)
        .map { list -> ActivityLogState(entries = list.toImmutableList(), isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityLogState.initial())
}
