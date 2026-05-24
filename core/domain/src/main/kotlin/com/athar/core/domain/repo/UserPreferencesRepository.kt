package com.athar.core.domain.repo

import kotlinx.coroutines.flow.Flow

/**
 * Small DataStore-backed user-preference surface.
 *
 * Lives in core:domain so feature modules can read without depending on core:data.
 */
interface UserPreferencesRepository {
    fun onboardingComplete(): Flow<Boolean>
    suspend fun setOnboardingComplete(complete: Boolean)

    fun lastSmsBackfillEpochSeconds(): Flow<Long>
    suspend fun setLastSmsBackfillEpochSeconds(epoch: Long)

    fun hijriEnabled(): Flow<Boolean>
    suspend fun setHijriEnabled(enabled: Boolean)
}
