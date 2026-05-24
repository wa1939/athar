package com.athar.core.common.time

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import java.time.YearMonth

/**
 * Returns the same shape of period shifted one step into the past.
 *
 * Master Brief §4.3 "Historical Comparison" needs "current vs previous". This produces
 * the previous month for [Period.Month], the previous N-month window for [Period.Last],
 * the previous year for [Period.Year], and a same-length window ending immediately
 * before the start for [Period.Custom].
 */
fun Period.previous(): Period = when (this) {
    is Period.Month -> {
        val ym = month
        val prev = if (ym.month.value == 1) {
            YearMonth.of(ym.year - 1, 12)
        } else {
            YearMonth.of(ym.year, ym.month.value - 1)
        }
        Period.Month(prev)
    }
    is Period.Last -> Period.Last(months = months, endingAt = endingAt.minus(DatePeriod(months = months)))
    is Period.Year -> Period.Year(year - 1)
    is Period.Custom -> {
        val days = start.daysUntil(endExclusive)
        Period.Custom(
            start = start.minus(DatePeriod(days = days)),
            endExclusive = start,
        )
    }
}
