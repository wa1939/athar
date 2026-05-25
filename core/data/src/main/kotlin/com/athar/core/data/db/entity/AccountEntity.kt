package com.athar.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "account",
    indices = [
        Index(value = ["archivedAt"]),
        Index(value = ["sortOrder"]),
    ],
)
internal data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,                // AccountType.name
    val currency: String,
    val openingBalanceMinor: Long,
    val openingBalanceCurrency: String,
    val smsSenders: String,          // JSON-encoded List<String>
    val notes: String?,
    val sortOrder: Int,
    val active: Boolean,             // kept for backward compat; mirrors archivedAt == null
    val archivedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Projection POJO produced by `TransactionDao.observeBalancesByAccount` — the SUM of
 * confirmed transactions per (accountId, currency), expressed in signed minor units
 * (positive INCOME, negative EXPENSE).
 */
internal data class AccountBalanceRow(
    val accountId: String,
    val currency: String,
    val sumMinor: Long,
)
