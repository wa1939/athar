package com.athar.feature.settings

import com.athar.core.common.time.Period
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
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
import com.athar.core.domain.repo.SupportDiagnosticsExportResult
import com.athar.core.domain.repo.SupportDiagnosticsExportTrigger
import com.athar.core.domain.repo.TaxExportResult
import com.athar.core.domain.repo.TaxExportTrigger
import com.athar.core.domain.repo.TransactionRepository
import com.athar.core.domain.repo.UserPreferencesRepository
import com.athar.core.testing.Fixtures
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var mainDispatcher: TestDispatcher

    @BeforeEach
    fun setUp() {
        mainDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pending count follows pending repository flow`() = runTest(mainDispatcher) {
        val transactions = FakeTransactionRepository()
        val viewModel = settingsViewModel(transactions = transactions)

        transactions.pending.value = listOf(
            Fixtures.transaction(id = "pending-1"),
            Fixtures.transaction(id = "pending-2"),
        )
        advanceUntilIdle()

        assertThat(viewModel.pendingCount.value).isEqualTo(2)
    }

    @Test
    fun `rescan and clean clears pending before backfill`() = runTest(mainDispatcher) {
        val calls = mutableListOf<String>()
        val transactions = FakeTransactionRepository(
            clearPendingResult = 7,
            onClearPending = { calls += "clear" },
        )
        val backfill = FakeSmsBackfillTrigger(onBackfill = { calls += "backfill" })
        val viewModel = settingsViewModel(
            backfill = backfill,
            transactions = transactions,
        )

        viewModel.rescanAndClean()
        advanceUntilIdle()

        assertThat(calls).containsExactly("clear", "backfill").inOrder()
        assertThat(viewModel.rescanStatus.value).isEqualTo(RescanStatus.Done(clearedPending = 7))
    }

    private fun settingsViewModel(
        backfill: SmsBackfillTrigger = FakeSmsBackfillTrigger(),
        transactions: TransactionRepository = FakeTransactionRepository(),
    ) = SettingsViewModel(
        backup = FakeBackupRepository,
        backfillTrigger = backfill,
        csvImporter = FakeCsvImportTrigger,
        csvExporter = FakeCsvExportTrigger,
        bulkExporter = FakeMerchantBulkExportTrigger,
        bulkImporter = FakeMerchantBulkImportTrigger,
        communityShare = FakeCommunityRulesShareTrigger,
        supportDiagnostics = FakeSupportDiagnosticsExportTrigger,
        taxExport = FakeTaxExportTrigger,
        prefs = FakeUserPreferencesRepository(),
        transactions = transactions,
    )
}

private class FakeTransactionRepository(
    val pending: MutableStateFlow<List<Transaction>> = MutableStateFlow(emptyList()),
    private val clearPendingResult: Int = 0,
    private val onClearPending: () -> Unit = {},
) : TransactionRepository {
    override fun observeByPeriod(period: Period, status: TxStatus?): Flow<List<Transaction>> = flowOf(emptyList())
    override fun observePending(): Flow<List<Transaction>> = pending
    override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())
    override suspend fun get(id: String): Transaction? = null
    override suspend fun upsert(transaction: Transaction) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun setStatus(id: String, status: TxStatus) = Unit
    override suspend fun clearPending(): Int {
        onClearPending()
        return clearPendingResult
    }
    override suspend fun confirmAllConfident(minConfidence: Float): Int = 0
    override suspend fun dismissAllLowConfidence(maxConfidence: Float): Int = 0
    override suspend fun dismissAllPending(): Int = 0
    override suspend fun recoverDismissedToPending(): Int = 0
    override suspend fun applyCategoryToMatching(pattern: String, categoryId: String): Int = 0
}

private class FakeSmsBackfillTrigger(
    private val onBackfill: () -> Unit = {},
) : SmsBackfillTrigger {
    private val _progress = MutableStateFlow<BackfillProgress>(BackfillProgress.Idle)
    override val progress: StateFlow<BackfillProgress> = _progress.asStateFlow()
    override suspend fun backfill(daysBack: Int?) {
        onBackfill()
        _progress.value = BackfillProgress.Done(scanned = 0, sentToPipeline = 0)
    }
}

private object FakeBackupRepository : BackupRepository {
    override suspend fun export(output: OutputStream, passphrase: CharArray) = Unit
    override suspend fun import(bytes: ByteArray, passphrase: CharArray) = Unit
}

private object FakeCsvImportTrigger : CsvImportTrigger {
    override suspend fun import(input: InputStream): CsvImportResult = CsvImportResult.Done(imported = 0, skipped = 0)
}

private object FakeCsvExportTrigger : CsvExportTrigger {
    override suspend fun exportAll(output: OutputStream): CsvExportResult = CsvExportResult.Done(exported = 0)
}

private object FakeMerchantBulkExportTrigger : MerchantBulkExportTrigger {
    override suspend fun exportUncategorized(out: OutputStream): MerchantBulkExportResult =
        MerchantBulkExportResult.Done(rows = 0)
}

private object FakeMerchantBulkImportTrigger : MerchantBulkImportTrigger {
    override suspend fun importCategorizations(input: InputStream): MerchantBulkImportResult =
        MerchantBulkImportResult.Done(updated = 0, rulesAdded = 0, skipped = 0)
}

private object FakeCommunityRulesShareTrigger : CommunityRulesShareTrigger {
    override suspend fun exportLearnedRules(out: OutputStream): CommunityRulesShareResult =
        CommunityRulesShareResult.Empty
}

private object FakeSupportDiagnosticsExportTrigger : SupportDiagnosticsExportTrigger {
    override suspend fun exportDiagnostics(out: OutputStream): SupportDiagnosticsExportResult =
        SupportDiagnosticsExportResult.Done(auditRows = 0, parsed = 0, failed = 0, ignored = 0)
}

private object FakeTaxExportTrigger : TaxExportTrigger {
    override suspend fun exportAnnual(output: OutputStream, year: Int, localeTag: String): TaxExportResult =
        TaxExportResult.Done(year = year, transactions = 0, categoryTotals = 0, excludedReconciliations = 0)
}

private class FakeUserPreferencesRepository : UserPreferencesRepository {
    override fun onboardingComplete(): Flow<Boolean> = flowOf(false)
    override suspend fun setOnboardingComplete(complete: Boolean) = Unit
    override fun lastSmsBackfillEpochSeconds(): Flow<Long> = flowOf(0L)
    override suspend fun setLastSmsBackfillEpochSeconds(epoch: Long) = Unit
    override fun hijriEnabled(): Flow<Boolean> = flowOf(false)
    override suspend fun setHijriEnabled(enabled: Boolean) = Unit
    override fun ownAccountNumbers(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun setOwnAccountNumbers(numbers: List<String>) = Unit
    override fun displayCurrency(): Flow<String> = flowOf("SAR")
    override suspend fun setDisplayCurrency(currency: String) = Unit
    override fun appLocale(): Flow<String> = flowOf("")
    override suspend fun setAppLocale(languageTag: String) = Unit
    override fun savingsRateTargetPercent(): Flow<Int> = flowOf(20)
    override suspend fun setSavingsRateTargetPercent(percent: Int) = Unit
    override fun emergencyFundTargetMonths(): Flow<Int> = flowOf(6)
    override suspend fun setEmergencyFundTargetMonths(months: Int) = Unit
    override fun billRemindersEnabled(): Flow<Boolean> = flowOf(false)
    override suspend fun setBillRemindersEnabled(enabled: Boolean) = Unit
    override fun billReminderSentKeys(): Flow<Set<String>> = flowOf(emptySet())
    override suspend fun setBillReminderSentKeys(keys: Set<String>) = Unit
}
