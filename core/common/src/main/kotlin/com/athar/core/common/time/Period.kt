package com.athar.core.common.time

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.time.YearMonth

/**
 * A closed date range used app-wide for filtering.
 *
 * Mirrors the period selector in Master Brief §3 (Trends screen): month / 3-month / year / custom.
 */
sealed interface Period {
    val start: LocalDate
    val endExclusive: LocalDate

    fun contains(date: LocalDate): Boolean = date >= start && date < endExclusive

    data class Month(val month: YearMonth) : Period {
        override val start: LocalDate = LocalDate(month.year, month.monthValue, 1)
        override val endExclusive: LocalDate = start.plus(DatePeriod(months = 1))
    }

    data class Last(val months: Int, val endingAt: LocalDate) : Period {
        override val endExclusive: LocalDate = endingAt.plus(DatePeriod(days = 1))
        override val start: LocalDate = endExclusive.minus(DatePeriod(months = months))

        init {
            require(months in 1..120) { "Period.Last expects 1..120 months, got $months" }
        }
    }

    data class Year(val year: Int) : Period {
        override val start: LocalDate = LocalDate(year, kotlinx.datetime.Month.JANUARY, 1)
        override val endExclusive: LocalDate = LocalDate(year + 1, kotlinx.datetime.Month.JANUARY, 1)
    }

    data class Custom(
        override val start: LocalDate,
        override val endExclusive: LocalDate,
    ) : Period {
        init {
            require(start < endExclusive) { "Custom period start must be before endExclusive" }
        }
    }
}
