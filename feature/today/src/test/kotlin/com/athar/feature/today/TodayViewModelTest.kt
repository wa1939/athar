package com.athar.feature.today

import com.athar.core.common.money.Money
import com.athar.core.common.time.Period
import com.athar.core.domain.model.Account
import com.athar.core.domain.model.AccountBalance
import com.athar.core.domain.model.AccountType
import com.athar.core.domain.model.CategoryRule
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.NetWorth
import com.athar.core.domain.model.PatternType
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.AccountRepository
import com.athar.core.domain.repo.CategoryRuleRepository
import com.athar.core.domain.repo.ReconcileResult
import com.athar.core.domain.repo.TransactionRepository
import com.athar.core.domain.repo.UserPreferencesRepository
import com.athar.core.testing.Fixtures
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

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
    fun `goal nudge follows persisted targets and last three month averages`() = runTest(mainDispatcher) {
        val viewModel = TodayViewModel(
            transactions = FakeTransactionRepository(
                currentConfirmed = listOf(
                    transaction(id = "today-income", type = TxType.INCOME, amount = Money.of("1000")),
                    transaction(id = "today-expense", type = TxType.EXPENSE, amount = Money.of("250")),
                ),
                goalConfirmed = listOf(
                    transaction(id = "goal-income", type = TxType.INCOME, amount = Money.of("9000")),
                    transaction(id = "goal-expense", type = TxType.EXPENSE, amount = Money.of("6000")),
                ),
            ),
            rules = RecordingCategoryRuleRepository(),
            prefs = FakeUserPreferencesRepository(savingsTarget = 30, emergencyMonths = 6),
            accounts = FakeAccountRepository(liquidBalance = Money.of("5000")),
            clock = FixedClock,
        )

        val state = viewModel.state.first { !it.isLoading }

        assertThat(state.goalNudge).isNotNull()
        val nudge = state.goalNudge!!
        assertThat(nudge.savingsRatePercent).isEqualTo(java.math.BigDecimal("33.3"))
        assertThat(nudge.savingsRateTargetPercent).isEqualTo(30)
        assertThat(nudge.savingsRateProgress).isEqualTo(1f)
        assertThat(nudge.emergencyMonthsCovered).isEqualTo(java.math.BigDecimal("2.5"))
        assertThat(nudge.emergencyFundTargetMonths).isEqualTo(6)
        assertThat(nudge.emergencyFundProgress).isWithin(0.0001f).of(0.4167f)
    }

    @Test
    fun `always categorize emits backfill event and can be cleared`() = runTest(mainDispatcher) {
        val transactions = FakeTransactionRepository(backfillCount = 3)
        val rules = RecordingCategoryRuleRepository()
        val viewModel = TodayViewModel(
            transactions = transactions,
            rules = rules,
            prefs = FakeUserPreferencesRepository(savingsTarget = 30, emergencyMonths = 6),
            accounts = FakeAccountRepository(liquidBalance = Money.of("5000")),
            clock = FixedClock,
        )

        viewModel.updateTransaction(
            transaction(
                id = "hemmah",
                type = TxType.EXPENSE,
                amount = Money.of("42"),
            ).copy(
                merchant = "Hemmah",
                merchantNormalized = "hemmah",
                categoryId = "cat-home-maintenance",
                status = TxStatus.PENDING,
            ),
            learnRule = true,
        )
        advanceUntilIdle()

        assertThat(transactions.upserts.single().status).isEqualTo(TxStatus.CONFIRMED)
        assertThat(rules.learned).containsExactly(
            LearnedRule(
                merchantNormalized = "hemmah",
                categoryId = "cat-home-maintenance",
                patternType = PatternType.SUBSTRING,
            ),
        )
        assertThat(transactions.appliedPatterns).containsExactly(
            "hemmah" to "cat-home-maintenance",
        )
        assertThat(viewModel.lastBackfill.value)
            .isEqualTo(TodayViewModel.BackfillEvent(pattern = "Hemmah", count = 3))

        viewModel.clearBackfill()

        assertThat(viewModel.lastBackfill.value).isNull()
    }
}

private class FakeTransactionRepository(
    private val currentConfirmed: List<Transaction> = emptyList(),
    private val goalConfirmed: List<Transaction> = emptyList(),
    private val pending: List<Transaction> = emptyList(),
    private val dismissed: List<Transaction> = emptyList(),
    private val backfillCount: Int = 0,
) : TransactionRepository {
    val upserts = mutableListOf<Transaction>()
    val appliedPatterns = mutableListOf<Pair<String, String>>()

    override fun observeByPeriod(period: Period, status: TxStatus?): Flow<List<Transaction>> {
        val rows = when (period) {
            is Period.Last -> goalConfirmed
            else -> when (status) {
                TxStatus.CONFIRMED -> currentConfirmed
                TxStatus.DISMISSED -> dismissed
                else -> currentConfirmed + dismissed
            }
        }
        return flowOf(rows.filter { status == null || it.status == status })
    }

    override fun observePending(): Flow<List<Transaction>> = flowOf(pending)
    override fun observeAll(): Flow<List<Transaction>> = flowOf(currentConfirmed + goalConfirmed + pending + dismissed)
    override suspend fun get(id: String): Transaction? = null
    override suspend fun upsert(transaction: Transaction) {
        upserts += transaction
    }
    override suspend fun delete(id: String) = Unit
    override suspend fun setStatus(id: String, status: TxStatus) = Unit
    override suspend fun clearPending(): Int = 0
    override suspend fun confirmAllConfident(minConfidence: Float): Int = 0
    override suspend fun dismissAllLowConfidence(maxConfidence: Float): Int = 0
    override suspend fun dismissAllPending(): Int = 0
    override suspend fun recoverDismissedToPending(): Int = 0
    override suspend fun applyCategoryToMatching(pattern: String, categoryId: String): Int {
        appliedPatterns += pattern to categoryId
        return backfillCount
    }
}

private class RecordingCategoryRuleRepository : CategoryRuleRepository {
    val learned = mutableListOf<LearnedRule>()

    override fun observeAll(): Flow<List<CategoryRule>> = flowOf(emptyList())
    override suspend fun findMatching(merchantNormalized: String): List<CategoryRule> = emptyList()
    override suspend fun upsert(rule: CategoryRule) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun learnFromCorrection(
        merchantNormalized: String,
        categoryId: String,
        patternType: PatternType,
    ): CategoryRule {
        learned += LearnedRule(merchantNormalized, categoryId, patternType)
        return CategoryRule(
            id = "learned-$merchantNormalized",
            pattern = merchantNormalized,
            patternType = patternType,
            categoryId = categoryId,
            priority = 100,
            learnedFromUser = true,
            createdAt = FixedClock.now(),
        )
    }
}

private data class LearnedRule(
    val merchantNormalized: String,
    val categoryId: String,
    val patternType: PatternType,
)

private class FakeUserPreferencesRepository(
    private val savingsTarget: Int,
    private val emergencyMonths: Int,
) : UserPreferencesRepository {
    override fun onboardingComplete(): Flow<Boolean> = flowOf(true)
    override suspend fun setOnboardingComplete(complete: Boolean) = Unit
    override fun lastSmsBackfillEpochSeconds(): Flow<Long> = flowOf(0L)
    override suspend fun setLastSmsBackfillEpochSeconds(epoch: Long) = Unit
    override fun hijriEnabled(): Flow<Boolean> = flowOf(false)
    override suspend fun setHijriEnabled(enabled: Boolean) = Unit
    override fun ownAccountNumbers(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun setOwnAccountNumbers(numbers: List<String>) = Unit
    override fun displayCurrency(): Flow<String> = flowOf(Money.SAR)
    override suspend fun setDisplayCurrency(currency: String) = Unit
    override fun appLocale(): Flow<String> = flowOf("")
    override suspend fun setAppLocale(languageTag: String) = Unit
    override fun savingsRateTargetPercent(): Flow<Int> = flowOf(savingsTarget)
    override suspend fun setSavingsRateTargetPercent(percent: Int) = Unit
    override fun emergencyFundTargetMonths(): Flow<Int> = flowOf(emergencyMonths)
    override suspend fun setEmergencyFundTargetMonths(months: Int) = Unit
    override fun billRemindersEnabled(): Flow<Boolean> = flowOf(false)
    override suspend fun setBillRemindersEnabled(enabled: Boolean) = Unit
    override fun billReminderSentKeys(): Flow<Set<String>> = flowOf(emptySet())
    override suspend fun setBillReminderSentKeys(keys: Set<String>) = Unit
}

private class FakeAccountRepository(
    private val liquidBalance: Money,
) : AccountRepository {
    private val account = Fixtures.account(
        id = "checking",
        type = AccountType.CHECKING,
        openingBalance = Money.zero(),
    )
    private val balance = AccountBalance(account = account, current = liquidBalance)
    private val netWorth = NetWorth(
        total = liquidBalance,
        byCurrency = mapOf(liquidBalance.currency to liquidBalance),
        accounts = listOf(balance),
    )

    override fun observeActive(): Flow<List<Account>> = flowOf(listOf(account))
    override fun observeAll(includeArchived: Boolean): Flow<List<Account>> = flowOf(listOf(account))
    override suspend fun get(id: String): Account? = account.takeIf { it.id == id }
    override suspend fun upsert(account: Account) = Unit
    override suspend fun setArchived(id: String, archived: Boolean) = Unit
    override suspend fun delete(id: String) = Unit
    override fun observeNetWorth(displayCurrency: String): Flow<NetWorth> = flowOf(netWorth)
    override fun observeBalances(): Flow<List<AccountBalance>> = flowOf(listOf(balance))
    override suspend fun resolveForIngest(sender: String, body: String, counterparty: String?): Account? = null
    override suspend fun reconcile(accountId: String, target: Money, label: String, note: String?): ReconcileResult =
        ReconcileResult.Failed("Not used")
}

private object FixedClock : Clock {
    override fun now(): Instant = Instant.parse("2026-06-13T12:00:00Z")
}

private fun transaction(
    id: String,
    type: TxType,
    amount: Money,
    date: LocalDate = LocalDate(2026, 6, 13),
): Transaction = Fixtures.transaction(
    id = id,
    amount = amount,
    merchant = id,
    status = TxStatus.CONFIRMED,
).copy(
    type = type,
    date = date,
    source = IngestSource.MANUAL,
)
