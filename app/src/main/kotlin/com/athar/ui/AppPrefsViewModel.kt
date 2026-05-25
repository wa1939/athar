package com.athar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.domain.repo.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * App-level preferences hoisted at [AtharApp] so any screen can read them via
 * [com.athar.core.designsystem.display.LocalHijriEnabled] (and future similar locals)
 * without each feature VM re-subscribing.
 */
@HiltViewModel
class AppPrefsViewModel @Inject constructor(
    prefs: UserPreferencesRepository,
) : ViewModel() {

    val hijriEnabled: StateFlow<Boolean> = prefs.hijriEnabled()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val displayCurrency: StateFlow<String> = prefs.displayCurrency()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "SAR")
}
