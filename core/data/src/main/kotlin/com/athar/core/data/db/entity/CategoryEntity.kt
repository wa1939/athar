package com.athar.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category")
internal data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameAr: String,
    val kind: String,             // CategoryKind.name
    val icon: String?,
    val monthlyTargetMinor: Long?, // halalas, null = no target
    val currency: String,          // default SAR
    val archived: Boolean,
    val sortOrder: Int,
)
