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

    /**
     * Comma-separated list of the user's own account numbers (e.g., the last-4
     * digits like "0930,4268"). When a parsed transfer's recipient or sender
     * matches one of these, the pipeline classifies it as "Own account move"
     * rather than a regular transfer / expense — useful for distinguishing
     * savings moves from real outgoing payments.
     */
    fun ownAccountNumbers(): Flow<List<String>>
    suspend fun setOwnAccountNumbers(numbers: List<String>)
}
