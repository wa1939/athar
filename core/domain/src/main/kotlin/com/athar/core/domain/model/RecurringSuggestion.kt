package com.athar.core.domain.model

import com.athar.core.common.money.Money
import kotlinx.datetime.LocalDate

/**
 * A detected pattern in the user's confirmed transaction history that looks like
 * a recurring expense or income (rent, salary, Netflix). Produced by
 * [com.athar.core.domain.repo.RecurringSuggestionRepository] and shown to the user
 * for one-tap conversion into a [RecurringRule] — never auto-created.
 */
data class RecurringSuggestion(
    val merchant: String,
    val merchantNormalized: String,
    val amount: Money,
    val type: TxType,
    val occurrenceCount: Int,
    /** The day-of-month at which most occurrences happened (median-ish). */
    val typicalDayOfMonth: Int,
    /** The most recent occurrence — used to seed the new rule's nextRunDate. */
    val lastSeen: LocalDate,
    /** Estimated nextRunDate: same day-of-month next month, clamped. */
    val suggestedNextRun: LocalDate,
)
