package com.athar.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.domain.repo.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val pageIndex: Int = 0,
    val totalPages: Int = 3,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
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
}
