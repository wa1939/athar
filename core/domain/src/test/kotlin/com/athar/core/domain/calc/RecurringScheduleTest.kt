package com.athar.core.domain.calc

import com.athar.core.common.money.Money
import com.athar.core.domain.model.Cadence
import com.athar.core.domain.model.MANUAL_ACCOUNT_ID
import com.athar.core.domain.model.RecurringRule
import com.athar.core.domain.model.TxType
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class RecurringScheduleTest {

    @Test
    fun `monthly rules clamp to shorter months`() {
        val next = RecurringSchedule.nextRunAfter(
            fromDate = LocalDate(2026, 1, 31),
            cadence = Cadence.MONTHLY,
            dayOfMonth = 31,
            dayOfWeek = null,
            monthOfYear = null,
        )

        assertThat(next).isEqualTo(LocalDate(2026, 2, 28))
    }

    @Test
    fun `weekly rules land on configured day of week`() {
        val next = RecurringSchedule.nextRunAfter(
            fromDate = LocalDate(2026, 6, 13),
            cadence = Cadence.WEEKLY,
            dayOfMonth = null,
            dayOfWeek = 1,
            monthOfYear = null,
        )

        assertThat(next).isEqualTo(LocalDate(2026, 6, 15))
    }

    @Test
    fun `yearly rules use configured month and day`() {
        val next = RecurringSchedule.nextRunAfter(
            fromDate = LocalDate(2026, 6, 13),
            cadence = Cadence.YEARLY,
            dayOfMonth = 29,
            dayOfWeek = null,
            monthOfYear = 2,
        )

        assertThat(next).isEqualTo(LocalDate(2027, 2, 28))
    }

    @Test
    fun `project returns active occurrences through horizon`() {
        val occurrences = RecurringSchedule.project(
            rule = rule(
                cadence = Cadence.MONTHLY,
                dayOfMonth = 5,
                nextRunDate = LocalDate(2026, 6, 5),
            ),
            today = LocalDate(2026, 6, 1),
            horizonDays = 65,
        )

        assertThat(occurrences.map { it.dueDate }).containsExactly(
            LocalDate(2026, 6, 5),
            LocalDate(2026, 7, 5),
            LocalDate(2026, 8, 5),
        ).inOrder()
        assertThat(occurrences.any { it.isOverdue }).isFalse()
    }

    @Test
    fun `project includes one overdue occurrence then upcoming occurrences`() {
        val occurrences = RecurringSchedule.project(
            rule = rule(
                cadence = Cadence.MONTHLY,
                dayOfMonth = 10,
                nextRunDate = LocalDate(2026, 5, 10),
            ),
            today = LocalDate(2026, 6, 13),
            horizonDays = 45,
        )

        assertThat(occurrences.map { it.dueDate }).containsExactly(
            LocalDate(2026, 5, 10),
            LocalDate(2026, 7, 10),
        ).inOrder()
        assertThat(occurrences.first().isOverdue).isTrue()
        assertThat(occurrences.last().isOverdue).isFalse()
    }

    @Test
    fun `inactive rules project no occurrences`() {
        val occurrences = RecurringSchedule.project(
            rule = rule(isActive = false),
            today = LocalDate(2026, 6, 13),
        )

        assertThat(occurrences).isEmpty()
    }

    private fun rule(
        cadence: Cadence = Cadence.MONTHLY,
        dayOfMonth: Int? = 15,
        dayOfWeek: Int? = null,
        monthOfYear: Int? = null,
        nextRunDate: LocalDate = LocalDate(2026, 6, 15),
        isActive: Boolean = true,
    ): RecurringRule {
        val now = Instant.parse("2026-06-01T12:00:00Z")
        return RecurringRule(
            id = "rule-${cadence.name}",
            displayName = "Rent",
            merchant = "Rent",
            amount = Money.of("2500"),
            type = TxType.EXPENSE,
            accountId = MANUAL_ACCOUNT_ID,
            categoryId = "cat-rent",
            cadence = cadence,
            dayOfMonth = dayOfMonth,
            dayOfWeek = dayOfWeek,
            monthOfYear = monthOfYear,
            nextRunDate = nextRunDate,
            lastRunDate = null,
            isActive = isActive,
            notes = null,
            createdAt = now,
            updatedAt = now,
        )
    }
}
