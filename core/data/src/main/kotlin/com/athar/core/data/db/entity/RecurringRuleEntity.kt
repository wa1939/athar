package com.athar.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistence shape for [com.athar.core.domain.model.RecurringRule]. Money stored as
 * minor units (halalas / cents) consistent with the rest of the schema; currency code
 * lives in [amountCurrency].
 */
@Entity(
    tableName = "recurring_rule",
    indices = [Index(value = ["nextRunDate"]), Index(value = ["isActive"])],
)
data class RecurringRuleEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val merchant: String,
    val amountMinor: Long,
    val amountCurrency: String,
    /** [com.athar.core.domain.model.TxType] name. */
    val type: String,
    val accountId: String,
    val categoryId: String?,
    /** [com.athar.core.domain.model.Cadence] name. */
    val cadence: String,
    val dayOfMonth: Int?,
    val dayOfWeek: Int?,
    val monthOfYear: Int?,
    /** ISO LocalDate yyyy-MM-dd. */
    val nextRunDate: String,
    val lastRunDate: String?,
    val isActive: Boolean,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
