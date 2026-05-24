package com.athar.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "category_rule",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["priority"]),
    ],
)
internal data class CategoryRuleEntity(
    @PrimaryKey val id: String,
    val pattern: String,
    val patternType: String,        // PatternType.name
    val categoryId: String,
    val priority: Int,
    val learnedFromUser: Boolean,
    val createdAt: Instant,
)
