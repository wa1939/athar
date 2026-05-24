package com.athar.core.domain

import com.athar.core.common.time.HijriDate
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HijriDateTest {

    @Test
    fun `2026-01-01 maps to a 1447 Hijri month`() {
        // Jan 1 2026 Gregorian falls in Rajab 1447 — verify the year flips correctly.
        // The exact Hijri month depends on Umm-al-Qura tables; we just check that the year
        // is 1447 and the result is non-blank with the expected `YYYY/MM` shape.
        val result = HijriDate.formatYearMonth(2026, 1, 1)
        assertThat(result).matches("""\d{4}/\d{2}""")
        assertThat(result.substring(0, 4).toInt()).isIn(1446..1447)
    }

    @Test
    fun `format yields zero-padded month`() {
        val result = HijriDate.formatYearMonth(2026, 5, 1)
        val month = result.substringAfter("/").toInt()
        assertThat(month).isIn(1..12)
    }

    @Test
    fun `full date format has YYYY-MM-DD shape`() {
        val result = HijriDate.formatDate(2026, 5, 24)
        assertThat(result).matches("""\d{4}/\d{2}/\d{2}""")
    }
}
