package com.athar.ui.onboarding

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.domain.repo.CsvImportResult
import com.athar.core.domain.repo.CsvImportTrigger
import com.athar.core.domain.repo.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface OnboardingImportStatus {
    data object Idle : OnboardingImportStatus
    data object Working : OnboardingImportStatus
    data class Done(val imported: Int, val skipped: Int) : OnboardingImportStatus
    data class Failed(val reason: String) : OnboardingImportStatus
}

data class OnboardingState(
    val pageIndex: Int = 0,
    val totalPages: Int = 4,
    val importStatus: OnboardingImportStatus = OnboardingImportStatus.Idle,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val csvImporter: CsvImportTrigger,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    val onboardingComplete: StateFlow<Boolean> = prefs.onboardingComplete()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun next() {
        _state.value = _state.value.copy(
            pageIndex = (_state.value.pageIndex + 1).coerceAtMost(_state.value.totalPages - 1),
        )
    }

    fun back() {
        _state.value = _state.value.copy(
            pageIndex = (_state.value.pageIndex - 1).coerceAtLeast(0),
        )
    }

    fun finish() {
        viewModelScope.launch { prefs.setOnboardingComplete(true) }
    }

    fun importCsv(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(importStatus = OnboardingImportStatus.Working)
            val input = resolver.openInputStream(uri)
            if (input == null) {
                _state.value = _state.value.copy(
                    importStatus = OnboardingImportStatus.Failed("Couldn't open CSV file."),
                )
                return@launch
            }
            val status = when (val result = csvImporter.import(input)) {
                is CsvImportResult.Done -> OnboardingImportStatus.Done(
                    imported = result.imported,
                    skipped = result.skipped,
                )
                is CsvImportResult.Failed -> OnboardingImportStatus.Failed(result.reason)
            }
            _state.value = _state.value.copy(importStatus = status)
        }
    }
}
