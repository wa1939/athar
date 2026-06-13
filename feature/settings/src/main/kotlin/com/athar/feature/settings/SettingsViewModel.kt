package com.athar.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.domain.repo.BackfillProgress
import com.athar.core.domain.repo.BackupRepository
import com.athar.core.domain.repo.CommunityRulesShareResult
import com.athar.core.domain.repo.CommunityRulesShareTrigger
import com.athar.core.domain.repo.CsvExportResult
import com.athar.core.domain.repo.CsvExportTrigger
import com.athar.core.domain.repo.CsvImportResult
import com.athar.core.domain.repo.CsvImportTrigger
import com.athar.core.domain.repo.MerchantBulkExportResult
import com.athar.core.domain.repo.MerchantBulkExportTrigger
import com.athar.core.domain.repo.MerchantBulkImportResult
import com.athar.core.domain.repo.MerchantBulkImportTrigger
import com.athar.core.domain.repo.SmsBackfillTrigger
import com.athar.core.domain.repo.TransactionRepository
import com.athar.core.domain.repo.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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

/**
 * Status of the bulk-categorize-via-AI flow (Issue #5a). Export writes a CSV of every
 * uncategorized transaction; the user feeds it to ChatGPT/Claude with the AI triage
 * prompt and imports the filled CSV. Each row's `category_id` becomes the transaction's
 * new category AND seeds a `CategoryRule` so future ingests benefit too.
 */
sealed interface BulkCategorizeStatus {
    data object Idle : BulkCategorizeStatus
    data object Working : BulkCategorizeStatus
    data class Exported(val rows: Int) : BulkCategorizeStatus
    data class Imported(val updated: Int, val rulesAdded: Int, val skipped: Int) : BulkCategorizeStatus
    data class Failed(val reason: String) : BulkCategorizeStatus
}

/**
 * Status of the community-rule sharing flow (beta.20). User exports their
 * `learnedFromUser=true` rules to a JSON file, then submits it as a GitHub issue.
 * Pure local-only — no upload, no backend. See `docs/COMMUNITY_RULES_WORKFLOW.md`.
 */
sealed interface CommunityShareStatus {
    data object Idle : CommunityShareStatus
    data object Working : CommunityShareStatus
    data class Exported(val rows: Int, val fileUri: Uri) : CommunityShareStatus
    data object Empty : CommunityShareStatus
    data class Failed(val reason: String) : CommunityShareStatus
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backup: BackupRepository,
    private val backfillTrigger: SmsBackfillTrigger,
    private val csvImporter: CsvImportTrigger,
    private val csvExporter: CsvExportTrigger,
    private val bulkExporter: MerchantBulkExportTrigger,
    private val bulkImporter: MerchantBulkImportTrigger,
    private val communityShare: CommunityRulesShareTrigger,
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

    private val _bulkCategorize = MutableStateFlow<BulkCategorizeStatus>(BulkCategorizeStatus.Idle)
    val bulkCategorizeStatus: StateFlow<BulkCategorizeStatus> = _bulkCategorize.asStateFlow()

    private val _communityShare = MutableStateFlow<CommunityShareStatus>(CommunityShareStatus.Idle)
    val communityShareStatus: StateFlow<CommunityShareStatus> = _communityShare.asStateFlow()

    val backfillProgress: StateFlow<BackfillProgress> = backfillTrigger.progress

    val pendingCount: StateFlow<Int> = transactions.observePending()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

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

    /**
     * Bulk-categorize export: writes a CSV of every PENDING/DISMISSED/uncategorized
     * transaction so the user can run them through an AI and import the filled file back.
     */
    fun exportUncategorized(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _bulkCategorize.value = BulkCategorizeStatus.Working
            val out = resolver.openOutputStream(uri)
            if (out == null) {
                _bulkCategorize.value = BulkCategorizeStatus.Failed("Couldn't open CSV destination.")
                return@launch
            }
            _bulkCategorize.value = when (val r = bulkExporter.exportUncategorized(out)) {
                is MerchantBulkExportResult.Done -> BulkCategorizeStatus.Exported(r.rows)
                is MerchantBulkExportResult.Failed -> BulkCategorizeStatus.Failed(r.reason)
            }
        }
    }

    /**
     * Bulk-categorize import: each row whose `category_id` is set updates the matching
     * transaction (→ CONFIRMED) AND records a learned `CategoryRule` so future ingests
     * of the same merchant auto-categorize.
     */
    fun importCategorizations(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _bulkCategorize.value = BulkCategorizeStatus.Working
            val input = resolver.openInputStream(uri)
            if (input == null) {
                _bulkCategorize.value = BulkCategorizeStatus.Failed("Couldn't open CSV file.")
                return@launch
            }
            _bulkCategorize.value = when (val r = bulkImporter.importCategorizations(input)) {
                is MerchantBulkImportResult.Done ->
                    BulkCategorizeStatus.Imported(r.updated, r.rulesAdded, r.skipped)
                is MerchantBulkImportResult.Failed -> BulkCategorizeStatus.Failed(r.reason)
            }
        }
    }

    fun clearBulkCategorizeStatus() {
        _bulkCategorize.value = BulkCategorizeStatus.Idle
    }

    /**
     * Export the user's `learnedFromUser=true` rules as JSON for submission to the
     * public Athar GitHub repository (beta.20). Privacy: ONLY (pattern, categoryId)
     * tuples — no transaction data of any kind. Caller passes the destination URI
     * picked via `CreateDocument("application/json")`.
     */
    fun exportLearnedRules(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _communityShare.value = CommunityShareStatus.Working
            val out = resolver.openOutputStream(uri)
            if (out == null) {
                _communityShare.value = CommunityShareStatus.Failed("Couldn't open JSON destination.")
                return@launch
            }
            _communityShare.value = when (val r = communityShare.exportLearnedRules(out)) {
                is CommunityRulesShareResult.Done -> CommunityShareStatus.Exported(r.rows, uri)
                CommunityRulesShareResult.Empty -> CommunityShareStatus.Empty
                is CommunityRulesShareResult.Failed -> CommunityShareStatus.Failed(r.reason)
            }
        }
    }

    fun clearCommunityShareStatus() {
        _communityShare.value = CommunityShareStatus.Idle
    }
}
