package com.athar.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.domain.repo.BackfillProgress
import com.athar.core.domain.repo.BackupRepository
import com.athar.core.domain.repo.CsvExportResult
import com.athar.core.domain.repo.CsvExportTrigger
import com.athar.core.domain.repo.CsvImportResult
import com.athar.core.domain.repo.CsvImportTrigger
import com.athar.core.domain.repo.SmsBackfillTrigger
import com.athar.core.domain.repo.TransactionRepository
import com.athar.core.domain.repo.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BackupStatus {
    data object Idle : BackupStatus
    data object Working : BackupStatus
    data object ExportSuccess : BackupStatus
    data object ImportSuccess : BackupStatus
    data class ExportFailure(val detail: String?) : BackupStatus
    data class ImportFailure(val detail: String?) : BackupStatus
}

sealed interface CsvStatus {
    data object Idle : CsvStatus
    data object Working : CsvStatus
    data class Done(val imported: Int, val skipped: Int) : CsvStatus
    data class Exported(val count: Int) : CsvStatus
    data class Failed(val reason: String) : CsvStatus
}

sealed interface RescanStatus {
    data object Idle : RescanStatus
    data object Working : RescanStatus
    data class Done(val clearedPending: Int) : RescanStatus
}

sealed interface RecoverStatus {
    data object Idle : RecoverStatus
    data object Working : RecoverStatus
    data class Done(val recovered: Int) : RecoverStatus
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backup: BackupRepository,
    private val backfillTrigger: SmsBackfillTrigger,
    private val csvImporter: CsvImportTrigger,
    private val csvExporter: CsvExportTrigger,
    private val prefs: UserPreferencesRepository,
    private val transactions: TransactionRepository,
) : ViewModel() {

    private val _rescan = MutableStateFlow<RescanStatus>(RescanStatus.Idle)
    val rescanStatus: StateFlow<RescanStatus> = _rescan.asStateFlow()

    private val _recover = MutableStateFlow<RecoverStatus>(RecoverStatus.Idle)
    val recoverStatus: StateFlow<RecoverStatus> = _recover.asStateFlow()

    private val _status = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    private val _csv = MutableStateFlow<CsvStatus>(CsvStatus.Idle)
    val csvStatus: StateFlow<CsvStatus> = _csv.asStateFlow()

    val backfillProgress: StateFlow<BackfillProgress> = backfillTrigger.progress

    val hijriEnabled: StateFlow<Boolean> = prefs.hijriEnabled()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val ownAccountNumbers: StateFlow<List<String>> = prefs.ownAccountNumbers()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val displayCurrency: StateFlow<String> = prefs.displayCurrency()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "SAR")

    val appLocale: StateFlow<String> = prefs.appLocale()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setHijriEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setHijriEnabled(enabled) }
    }

    fun setOwnAccountNumbers(csv: String) {
        viewModelScope.launch {
            val list = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            prefs.setOwnAccountNumbers(list)
        }
    }

    fun setDisplayCurrency(code: String) {
        viewModelScope.launch { prefs.setDisplayCurrency(code) }
    }

    fun setAppLocale(tag: String) {
        viewModelScope.launch { prefs.setAppLocale(tag) }
    }

    fun export(resolver: ContentResolver, uri: Uri, passphrase: String) {
        viewModelScope.launch {
            _status.value = BackupStatus.Working
            runCatching {
                val output = resolver.openOutputStream(uri)
                    ?: error("Couldn't open output stream for $uri")
                backup.export(output, passphrase.toCharArray())
            }
                .onSuccess { _status.value = BackupStatus.ExportSuccess }
                .onFailure { _status.value = BackupStatus.ExportFailure(it.message) }
        }
    }

    fun import(resolver: ContentResolver, uri: Uri, passphrase: String) {
        viewModelScope.launch {
            _status.value = BackupStatus.Working
            runCatching {
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Couldn't open input stream for $uri")
                backup.import(bytes, passphrase.toCharArray())
            }
                .onSuccess { _status.value = BackupStatus.ImportSuccess }
                .onFailure { _status.value = BackupStatus.ImportFailure(it.message) }
        }
    }

    fun importCsv(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _csv.value = CsvStatus.Working
            val input = resolver.openInputStream(uri)
            if (input == null) {
                _csv.value = CsvStatus.Failed("Couldn't open CSV file.")
                return@launch
            }
            _csv.value = when (val result = csvImporter.import(input)) {
                is CsvImportResult.Done -> CsvStatus.Done(result.imported, result.skipped)
                is CsvImportResult.Failed -> CsvStatus.Failed(result.reason)
            }
        }
    }

    fun exportCsv(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _csv.value = CsvStatus.Working
            val output = resolver.openOutputStream(uri)
            if (output == null) {
                _csv.value = CsvStatus.Failed("Couldn't open CSV destination.")
                return@launch
            }
            _csv.value = when (val result = csvExporter.exportAll(output)) {
                is CsvExportResult.Done -> CsvStatus.Exported(result.exported)
                is CsvExportResult.Failed -> CsvStatus.Failed(result.reason)
            }
        }
    }

    fun runBackfill() {
        viewModelScope.launch { backfillTrigger.backfill() }
    }

    /**
     * Wipes the pending tray (transactions never confirmed by the user) and triggers
     * a full SMS backfill so the audit log gets re-parsed with the latest templates.
     * Used to recover from earlier beta builds that ingested ads as transactions.
     * Confirmed transactions are untouched.
     */
    fun rescanAndClean() {
        viewModelScope.launch {
            _rescan.value = RescanStatus.Working
            val cleared = transactions.clearPending()
            backfillTrigger.backfill()
            _rescan.value = RescanStatus.Done(cleared)
        }
    }

    fun clearRescanStatus() {
        _rescan.value = RescanStatus.Idle
    }

    /**
     * Recovers transactions that older builds auto-dismissed for low confidence.
     * Older policy auto-DISMISSED any parse with confidence < 0.50; the new policy
     * keeps them all in PENDING so the user decides. This action moves the legacy
     * DISMISSED rows back to PENDING so the user can review what was hidden.
     */
    fun recoverDismissed() {
        viewModelScope.launch {
            _recover.value = RecoverStatus.Working
            val count = transactions.recoverDismissedToPending()
            _recover.value = RecoverStatus.Done(count)
        }
    }

    fun clearRecoverStatus() {
        _recover.value = RecoverStatus.Idle
    }

    fun clearStatus() {
        _status.value = BackupStatus.Idle
    }

    fun clearCsvStatus() {
        _csv.value = CsvStatus.Idle
    }
}
