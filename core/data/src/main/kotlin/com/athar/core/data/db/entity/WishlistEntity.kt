package com.athar.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wishlist_item")
internal data class WishlistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val costMinor: Long,
    val currentSavedMinor: Long,
    val currency: String,
    val desiredMonths: Int?,
    val startYear: Int,
    val startMonth: Int,   // 1..12
    val notes: String?,
)
