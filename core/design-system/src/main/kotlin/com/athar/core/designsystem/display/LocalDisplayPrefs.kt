package com.athar.core.designsystem.display

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * App-wide display flags hoisted at the AtharApp scaffold so any screen can read them
 * without threading the preference repository through each feature ViewModel.
 *
 * Default: Hijri off (Gregorian only). The wiring lives in app/AtharApp.
 */
val LocalHijriEnabled = staticCompositionLocalOf { false }
