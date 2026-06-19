package com.athar.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.athar.core.domain.model.GoalSettings
import com.athar.core.domain.repo.UserPreferencesRepository
import com.athar.core.domain.repo.WidgetRefresher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefs by preferencesDataStore(name = "user_prefs")

@Singleton
internal class UserPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val widgetRefresher: WidgetRefresher,
) : UserPreferencesRepository {

    private val onboardingKey = booleanPreferencesKey("onboarding_complete")
    private val lastBackfillKey = longPreferencesKey("last_sms_backfill_epoch_seconds")
    private val hijriKey = booleanPreferencesKey("hijri_display_enabled")
    private val ownAccountsKey = stringPreferencesKey("own_account_numbers_csv")
    private val displayCurrencyKey = stringPreferencesKey("display_currency_iso4217")
    private val appLocaleKey = stringPreferencesKey("app_locale_tag")
    private val savingsRateTargetPercentKey = intPreferencesKey("savings_rate_target_percent")
    private val emergencyFundTargetMonthsKey = intPreferencesKey("emergency_fund_target_months")
    private val billRemindersEnabledKey = booleanPreferencesKey("bill_reminders_enabled")
    private val billReminderSentKeysKey = stringPreferencesKey("bill_reminder_sent_keys_csv")

    override fun onboardingComplete(): Flow<Boolean> =
        context.userPrefs.data.map { it[onboardingKey] ?: false }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        context.userPrefs.edit { it[onboardingKey] = complete }
    }

    override fun lastSmsBackfillEpochSeconds(): Flow<Long> =
        context.userPrefs.data.map { it[lastBackfillKey] ?: 0L }

    override suspend fun setLastSmsBackfillEpochSeconds(epoch: Long) {
        context.userPrefs.edit { it[lastBackfillKey] = epoch }
    }

    override fun hijriEnabled(): Flow<Boolean> =
        context.userPrefs.data.map { it[hijriKey] ?: false }

    override suspend fun setHijriEnabled(enabled: Boolean) {
        context.userPrefs.edit { it[hijriKey] = enabled }
    }

    override fun ownAccountNumbers(): Flow<List<String>> =
        context.userPrefs.data.map { p ->
            p[ownAccountsKey].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }

    override suspend fun setOwnAccountNumbers(numbers: List<String>) {
        context.userPrefs.edit { it[ownAccountsKey] = numbers.joinToString(",") { it.trim() } }
    }

    override fun displayCurrency(): Flow<String> =
        context.userPrefs.data.map { it[displayCurrencyKey] ?: "SAR" }

    override suspend fun setDisplayCurrency(currency: String) {
        val normalized = currency.trim().uppercase()
        require(normalized.length == 3) { "Currency must be ISO-4217 3-letter code, got '$currency'" }
        var changed = false
        context.userPrefs.edit {
            if ((it[displayCurrencyKey] ?: "SAR") != normalized) {
                it[displayCurrencyKey] = normalized
                changed = true
            }
        }
        if (changed) {
            widgetRefresher.requestRefresh()
        }
    }

    override fun appLocale(): Flow<String> =
        context.userPrefs.data.map { it[appLocaleKey].orEmpty() }

    override suspend fun setAppLocale(languageTag: String) {
        val normalized = languageTag.trim()
        var changed = false
        context.userPrefs.edit {
            if (it[appLocaleKey].orEmpty() != normalized) {
                it[appLocaleKey] = normalized
                changed = true
            }
        }
        if (changed) {
            widgetRefresher.requestRefresh()
        }
    }

    override fun savingsRateTargetPercent(): Flow<Int> =
        context.userPrefs.data.map {
            it[savingsRateTargetPercentKey] ?: GoalSettings.DEFAULT_SAVINGS_RATE_TARGET_PERCENT
        }

    override suspend fun setSavingsRateTargetPercent(percent: Int) {
        require(percent in 0..100) { "Savings-rate target must be in 0..100, got $percent" }
        context.userPrefs.edit { it[savingsRateTargetPercentKey] = percent }
    }

    override fun emergencyFundTargetMonths(): Flow<Int> =
        context.userPrefs.data.map {
            it[emergencyFundTargetMonthsKey] ?: GoalSettings.DEFAULT_EMERGENCY_FUND_TARGET_MONTHS
        }

    override suspend fun setEmergencyFundTargetMonths(months: Int) {
        require(months in 1..120) { "Emergency-fund target must be in 1..120 months, got $months" }
        context.userPrefs.edit { it[emergencyFundTargetMonthsKey] = months }
    }

    override fun billRemindersEnabled(): Flow<Boolean> =
        context.userPrefs.data.map { it[billRemindersEnabledKey] ?: false }

    override suspend fun setBillRemindersEnabled(enabled: Boolean) {
        context.userPrefs.edit {
            it[billRemindersEnabledKey] = enabled
            if (!enabled) it.remove(billReminderSentKeysKey)
        }
    }

    override fun billReminderSentKeys(): Flow<Set<String>> =
        context.userPrefs.data.map { prefs ->
            prefs[billReminderSentKeysKey]
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }

    override suspend fun setBillReminderSentKeys(keys: Set<String>) {
        context.userPrefs.edit { prefs ->
            val encoded = keys
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .sorted()
                .joinToString(",")
            if (encoded.isBlank()) {
                prefs.remove(billReminderSentKeysKey)
            } else {
                prefs[billReminderSentKeysKey] = encoded
            }
        }
    }
}
