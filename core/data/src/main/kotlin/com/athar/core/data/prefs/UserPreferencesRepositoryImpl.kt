package com.athar.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.athar.core.domain.repo.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefs by preferencesDataStore(name = "user_prefs")

@Singleton
internal class UserPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : UserPreferencesRepository {

    private val onboardingKey = booleanPreferencesKey("onboarding_complete")
    private val lastBackfillKey = longPreferencesKey("last_sms_backfill_epoch_seconds")
    private val hijriKey = booleanPreferencesKey("hijri_display_enabled")
    private val ownAccountsKey = stringPreferencesKey("own_account_numbers_csv")

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
}
