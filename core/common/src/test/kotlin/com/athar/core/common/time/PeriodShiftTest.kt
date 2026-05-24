package com.athar.core.common.time

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import java.time.YearMonth

class PeriodShiftTest {

    @Test
    fun `previous of a month rolls back one calendar month`() {
        val p = Period.Month(YearMonth.of(2026, 3))
        val prev = p.previous() as Period.Month
        assertThat(prev.month).isEqualTo(YearMonth.of(2026, 2))
    }

    @Test
    fun `previous of January rolls to previous December`() {
        val p = Period.Month(YearMonth.of(2026, 1))
        val prev = p.previous() as Period.Month
        assertThat(prev.month).isEqualTo(YearMonth.of(2025, 12))
    }

    @Test
    fun `previous of Year rolls back one year`() {
        val prev = Period.Year(2026).previous() as Period.Year
        assertThat(prev.year).isEqualTo(2025)
    }

    @Test
    fun `previous of a custom window is the same-length window ending at start`() {
        val p = Period.Custom(
            start = LocalDate(2026, 5, 1),
            endExclusive = LocalDate(2026, 5, 11), // 10-day window
        )
        val prev = p.previous() as Period.Custom
        assertThat(prev.start).isEqualTo(LocalDate(2026, 4, 21))
        assertThat(prev.endExclusive).isEqualTo(LocalDate(2026, 5, 1))
    }

    @Test
    fun `previous of Last is the prior N-month window`() {
        val p = Period.Last(months = 3, endingAt = LocalDate(2026, 5, 31))
        val prev = p.previous() as Period.Last
        assertThat(prev.endingAt).isEqualTo(LocalDate(2026, 2, 28))
        assertThat(prev.months).isEqualTo(3)
    }
}
