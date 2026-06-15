package com.athar.feature.settings

import com.athar.core.common.money.Money
import com.athar.core.domain.model.Cadence
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import com.athar.core.domain.model.RecurringRule
import com.athar.core.domain.model.RecurringSuggestion
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.CategoryRepository
import com.athar.core.domain.repo.RecurringRuleRepository
import com.athar.core.domain.repo.RecurringSuggestionRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
class RecurringRulesViewModelTest {

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
    fun `accept suggestion falls back to safe suggested category`() = runTest(mainDispatcher) {
        val rules = RecordingRecurringRuleRepository()
        val suggestion = recurringSuggestion(suggestedCategoryId = "cat-gym")
        val viewModel = recurringRulesViewModel(rules = rules, suggestions = listOf(suggestion))

        viewModel.acceptSuggestion(
            suggestion = suggestion,
            notes = null,
            cadence = Cadence.MONTHLY,
            dayOfMonth = 5,
            categoryId = null,
        )
        advanceUntilIdle()

        assertThat(rules.upserts.single().categoryId).isEqualTo("cat-gym")
    }

    @Test
    fun `accept suggestion keeps explicit category override`() = runTest(mainDispatcher) {
        val rules = RecordingRecurringRuleRepository()
        val suggestion = recurringSuggestion(suggestedCategoryId = "cat-gym")
        val viewModel = recurringRulesViewModel(rules = rules, suggestions = listOf(suggestion))

        viewModel.acceptSuggestion(
            suggestion = suggestion,
            notes = null,
            cadence = Cadence.MONTHLY,
            dayOfMonth = 5,
            categoryId = "cat-health",
        )
        advanceUntilIdle()

        assertThat(rules.upserts.single().categoryId).isEqualTo("cat-health")
    }
}

private fun recurringRulesViewModel(
    rules: RecordingRecurringRuleRepository,
    suggestions: List<RecurringSuggestion>,
): RecurringRulesViewModel =
    RecurringRulesViewModel(
        rules = rules,
        suggestionRepo = FakeRecurringSuggestionRepository(suggestions),
        categoryRepo = FakeCategoryRepository(),
        clock = FixedClock,
    )

private class RecordingRecurringRuleRepository : RecurringRuleRepository {
    val upserts = mutableListOf<RecurringRule>()

    override fun observeAll(includeInactive: Boolean): Flow<List<RecurringRule>> = flowOf(emptyList())

    override suspend fun get(id: String): RecurringRule? = upserts.firstOrNull { it.id == id }

    override suspend fun upsert(rule: RecurringRule) {
        upserts += rule
    }

    override suspend fun delete(id: String) = Unit

    override suspend fun setActive(id: String, active: Boolean) = Unit

    override suspend fun materializeDue(today: LocalDate): Int = 0
}

private class FakeRecurringSuggestionRepository(
    private val suggestions: List<RecurringSuggestion>,
) : RecurringSuggestionRepository {
    override fun observeSuggestions(): Flow<List<RecurringSuggestion>> = flowOf(suggestions)
}

private class FakeCategoryRepository : CategoryRepository {
    override fun observeAll(kind: CategoryKind?, includeArchived: Boolean): Flow<List<Category>> = flowOf(emptyList())

    override suspend fun get(id: String): Category? = null

    override suspend fun upsert(category: Category) = Unit

    override suspend fun archive(id: String) = Unit

    override suspend fun reorder(ids: List<String>) = Unit
}

private object FixedClock : Clock {
    override fun now(): Instant = Instant.parse("2026-06-15T12:00:00Z")
}

private fun recurringSuggestion(suggestedCategoryId: String?): RecurringSuggestion =
    RecurringSuggestion(
        merchant = "Gym Club",
        merchantNormalized = "gym club",
        amount = Money.ofMinor(9900, "SAR"),
        type = TxType.EXPENSE,
        suggestedCategoryId = suggestedCategoryId,
        occurrenceCount = 3,
        typicalDayOfMonth = 5,
        lastSeen = LocalDate(2026, 3, 5),
        suggestedNextRun = LocalDate(2026, 4, 5),
    )
