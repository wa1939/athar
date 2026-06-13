package com.athar.core.domain.calc

import com.athar.core.domain.model.Cadence
import com.athar.core.domain.model.RecurringRule
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class RecurringOccurrence(
    val rule: RecurringRule,
    val dueDate: LocalDate,
    val isOverdue: Boolean,
)

object RecurringSchedule {

    fun project(
        rule: RecurringRule,
        today: LocalDate,
        horizonDays: Int = DEFAULT_HORIZON_DAYS,
        maxOccurrences: Int = DEFAULT_MAX_OCCURRENCES,
    ): List<RecurringOccurrence> {
        require(horizonDays in 1..366) { "horizonDays must be in 1..366, got $horizonDays" }
        require(maxOccurrences in 1..120) { "maxOccurrences must be in 1..120, got $maxOccurrences" }
        if (!rule.isActive) return emptyList()

        val endInclusive = today.plus(DatePeriod(days = horizonDays))
        val occurrences = mutableListOf<RecurringOccurrence>()
        var cursor = rule.nextRunDate

        if (cursor < today) {
            occurrences += RecurringOccurrence(rule = rule, dueDate = cursor, isOverdue = true)
        }

        var guard = 0
        while (cursor < today && guard < MAX_CATCH_UP_STEPS) {
            cursor = nextRunAfter(cursor, rule)
            guard++
        }

        while (cursor <= endInclusive && occurrences.size < maxOccurrences) {
            occurrences += RecurringOccurrence(rule = rule, dueDate = cursor, isOverdue = false)
            cursor = nextRunAfter(cursor, rule)
        }

        return occurrences
    }

    fun nextRunAfter(fromDate: LocalDate, rule: RecurringRule): LocalDate =
        nextRunAfter(
            fromDate = fromDate,
            cadence = rule.cadence,
            dayOfMonth = rule.dayOfMonth,
            dayOfWeek = rule.dayOfWeek,
            monthOfYear = rule.monthOfYear,
        )

    fun nextRunAfter(
        fromDate: LocalDate,
        cadence: Cadence,
        dayOfMonth: Int?,
        dayOfWeek: Int?,
        monthOfYear: Int?,
    ): LocalDate {
        val date = java.time.LocalDate.of(fromDate.year, fromDate.monthNumber, fromDate.dayOfMonth)
        val next = when (cadence) {
            Cadence.MONTHLY -> {
                val targetDay = (dayOfMonth ?: date.dayOfMonth).coerceIn(1, 31)
                val base = date.plusMonths(1)
                base.withDayOfMonth(targetDay.coerceAtMost(base.lengthOfMonth()))
            }
            Cadence.WEEKLY -> {
                val targetDow = (dayOfWeek ?: date.dayOfWeek.value).coerceIn(1, 7)
                var nextDate = date.plusDays(1)
                while (nextDate.dayOfWeek.value != targetDow) {
                    nextDate = nextDate.plusDays(1)
                }
                nextDate
            }
            Cadence.YEARLY -> {
                val targetMonth = (monthOfYear ?: date.monthValue).coerceIn(1, 12)
                val targetDay = (dayOfMonth ?: date.dayOfMonth).coerceIn(1, 31)
                val base = java.time.LocalDate.of(date.year + 1, targetMonth, 1)
                base.withDayOfMonth(targetDay.coerceAtMost(base.lengthOfMonth()))
            }
        }
        return LocalDate(next.year, next.monthValue, next.dayOfMonth)
    }

    private const val DEFAULT_HORIZON_DAYS = 60
    private const val DEFAULT_MAX_OCCURRENCES = 24
    private const val MAX_CATCH_UP_STEPS = 240
}
