package com.athar.ingestion.smsparser

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Best-effort date extractor for bank SMS bodies. Handles every shape observed in
 * the user's corpus (`docs/sms-corpus-analysis.md`):
 *   - `Date:27-12-2025 23:04`        (Al Rajhi Online Purchase)
 *   - `Date:31-12-2025 20:43`        (Al Rajhi PoS purchase)
 *   - `Date:25-12-27 20:10`          (Al Rajhi Internal Transfer, YY-MM-DD)
 *   - `9\1\26 21:33`                 (Al Rajhi Reverse, no label)
 *   - `25/1/26 20:26`                (Al Rajhi Loan Instalment)
 *   - `26/1/25 09:04`                (Al Rajhi Credit Transfer Local, DD/MM/YY)
 *   - `26-1-6 19:46`                 (Al Rajhi Bill Payment, YY-MM-DD)
 *   - `On: 2025-07-11 11:35`         (D360 / Barq, ISO)
 *   - `At:22/11/25 01:16`            (STC Bank, DD/MM/YY)
 *   - `On: 22/08/2025 03:19:04`      (D360 Account Funding, DD/MM/YYYY)
 *
 * Falls back to `null` so the caller uses the SMS receivedAt as the date — the
 * pipeline never crashes on an unparseable shape.
 *
 * Two-digit-year heuristic: any year < 100 is interpreted as `2000 + year`.
 */
fun parseSmsDate(body: String): Instant? {
    val candidates = listOf(
        // YYYY-MM-DD HH:MM[:SS]  — D360 / Barq
        Regex("""(\d{4})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?"""),
        // DD-MM-YYYY HH:MM       — Al Rajhi Online Purchase
        Regex("""(\d{1,2})-(\d{1,2})-(\d{4})\s+(\d{1,2}):(\d{2})"""),
        // YY-MM-DD HH:MM         — Al Rajhi Internal Transfer
        Regex("""(\d{2})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})\b"""),
        // DD/MM/YYYY HH:MM[:SS]  — D360 Account Funding
        Regex("""(\d{1,2})/(\d{1,2})/(\d{4})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?"""),
        // DD/MM/YY HH:MM         — STC Bank, Al Rajhi Loan
        Regex("""(\d{1,2})/(\d{1,2})/(\d{2})\s+(\d{1,2}):(\d{2})\b"""),
        // YY-M-D HH:MM           — Al Rajhi Bill Payment (single-digit month/day)
        Regex("""(\d{2})-(\d{1,2})-(\d{1,2})\b"""),
        // D\M\YY HH:MM           — Al Rajhi Reverse Transaction
        Regex("""(\d{1,2})\\(\d{1,2})\\(\d{2})\s+(\d{1,2}):(\d{2})"""),
    )

    for ((idx, regex) in candidates.withIndex()) {
        val m = regex.find(body) ?: continue
        val g = m.groupValues
        val (year, month, day, hour, minute) = when (idx) {
            0 -> Five(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt())                  // YYYY-MM-DD HH:MM
            1 -> Five(g[3].toInt(), g[2].toInt(), g[1].toInt(), g[4].toInt(), g[5].toInt())                  // DD-MM-YYYY HH:MM
            2 -> Five(2000 + g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt())           // YY-MM-DD HH:MM
            3 -> Five(g[3].toInt(), g[2].toInt(), g[1].toInt(), g[4].toInt(), g[5].toInt())                  // DD/MM/YYYY HH:MM
            4 -> Five(2000 + g[3].toInt(), g[2].toInt(), g[1].toInt(), g[4].toInt(), g[5].toInt())           // DD/MM/YY HH:MM
            5 -> Five(2000 + g[1].toInt(), g[2].toInt(), g[3].toInt(), 12, 0)                                // YY-M-D (no time)
            6 -> Five(2000 + g[3].toInt(), g[2].toInt(), g[1].toInt(), g[4].toInt(), g[5].toInt())           // D\M\YY HH:MM
            else -> continue
        }

        val date = runCatching { LocalDate(year, month, day) }.getOrNull() ?: continue
        // Sanity: ignore dates from before 2015 or > 1 year in the future
        if (year < 2015 || year > 2099) continue
        val time = runCatching { LocalTime(hour, minute) }.getOrNull() ?: LocalTime(12, 0)
        return LocalDateTime(date, time).toInstant(TimeZone.currentSystemDefault())
    }
    return null
}

private data class Five(val a: Int, val b: Int, val c: Int, val d: Int, val e: Int)
