package com.athar.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "investment_pool")
internal data class InvestmentPoolEntity(
    @PrimaryKey val id: String,
    val name: String,
    val period: String,
    val totalReturnMinor: Long,
    val currency: String,
)

@Entity(
    tableName = "investment_contribution",
    foreignKeys = [
        ForeignKey(
            entity = InvestmentPoolEntity::class,
            parentColumns = ["id"],
            childColumns = ["poolId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["poolId"])],
)
internal data class InvestmentContributionEntity(
    @PrimaryKey val id: String,
    val poolId: String,
    val ownerName: String,
    val amountMinor: Long,
    val currency: String,
)
