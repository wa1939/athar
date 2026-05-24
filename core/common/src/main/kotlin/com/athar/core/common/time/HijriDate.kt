package com.athar.core.common.time

import java.time.LocalDate as JavaLocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * Hijri (Islamic, Umm al-Qura) date conversions for display.
 *
 * Saudi Arabia uses Umm al-Qura — Android's `java.time.chrono.HijrahDate` (from API 26)
 * is built on top of [HijrahChronology.INSTANCE], which on Android implements
 * Umm al-Qura via system data. minSdk 28 → safe to use directly without backport.
 *
 * Master Brief §10.3 / Backlog P-07.
 */
object HijriDate {

    /** Returns `YYYY/MM` Hijri for a given Gregorian `year, month, day`. */
    fun formatYearMonth(year: Int, month: Int, day: Int = 1): String {
        val hijri = HijrahDate.from(JavaLocalDate.of(year, month, day))
        val hYear = hijri.get(ChronoField.YEAR_OF_ERA)
        val hMonth = hijri.get(ChronoField.MONTH_OF_YEAR)
        return "$hYear/${hMonth.toString().padStart(2, '0')}"
    }

    /** Full `YYYY/MM/DD` Hijri for a given Gregorian date. */
    fun formatDate(year: Int, month: Int, day: Int): String {
        val hijri = HijrahDate.from(JavaLocalDate.of(year, month, day))
        val hYear = hijri.get(ChronoField.YEAR_OF_ERA)
        val hMonth = hijri.get(ChronoField.MONTH_OF_YEAR)
        val hDay = hijri.get(ChronoField.DAY_OF_MONTH)
        return "$hYear/${hMonth.toString().padStart(2, '0')}/${hDay.toString().padStart(2, '0')}"
    }
}
