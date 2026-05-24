package com.athar.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(tableName = "account")
internal data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,          // AccountType.name
    val currency: String,
    val smsSenders: String,    // JSON-encoded List<String>
    val active: Boolean,
    val createdAt: Instant,
)
