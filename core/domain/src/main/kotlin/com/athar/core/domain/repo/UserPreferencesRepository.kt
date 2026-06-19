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

    /**
     * The user's chosen display currency (ISO-4217 3-letter code). Defaults to SAR.
     * Drives the symbol shown in [com.athar.core.designsystem.component.AtharNumber]
     * and the default currency for new Money values created from manual entry.
     *
     * Stored transactions keep their original currency code (e.g., SMS-parsed Saudi
     * transactions stay SAR even if the user later switches display to USD). The display
     * layer renders them with the currency code suffix when they differ from the user's
     * chosen display currency.
     */
    fun displayCurrency(): Flow<String>
    suspend fun setDisplayCurrency(currency: String)

    /**
     * The user's chosen UI language. An empty string means "follow the system locale"
     * (the default). Otherwise an ISO 639-1 language tag like "ar" or "en".
     *
     * Applied at activity attach via a Configuration override; switching it triggers
     * an activity recreate. Stored independently of the system locale so the user can
     * deliberately read the Arabic UI on an English phone (and vice versa).
     */
    fun appLocale(): Flow<String>
    suspend fun setAppLocale(languageTag: String)

    /**
     * User's savings-rate target as a whole percent, e.g. 20 means save 20% of income.
     * Defaults to 20%.
     */
    fun savingsRateTargetPercent(): Flow<Int>
    suspend fun setSavingsRateTargetPercent(percent: Int)

    /**
     * User's emergency-fund target in months of average expenses. Defaults to 6 months.
     */
    fun emergencyFundTargetMonths(): Flow<Int>
    suspend fun setEmergencyFundTargetMonths(months: Int)

    /**
     * Explicit opt-in for quiet bill reminders. Runtime notification permission alone is not
     * treated as consent because Athar avoids unsolicited money notifications.
     */
    fun billRemindersEnabled(): Flow<Boolean>
    suspend fun setBillRemindersEnabled(enabled: Boolean)

    /**
     * Reminder keys already sent for the currently relevant recurring bill windows. The worker
     * prunes this set as windows pass so it prevents duplicate daily notifications without
     * becoming an unbounded event log.
     */
    fun billReminderSentKeys(): Flow<Set<String>>
    suspend fun setBillReminderSentKeys(keys: Set<String>)
}
