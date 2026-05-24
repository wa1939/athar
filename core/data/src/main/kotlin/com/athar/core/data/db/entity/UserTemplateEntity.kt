package com.athar.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_template",
    indices = [Index(value = ["sender"])],
)
data class UserTemplateEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val sender: String,
    val txType: String,
    val amountAnchorBefore: String,
    val amountAnchorAfter: String?,
    val merchantAnchorBefore: String?,
    val merchantAnchorAfter: String?,
    val counterpartyAnchorBefore: String?,
    val counterpartyAnchorAfter: String?,
    val sampleBody: String,
    val createdAt: Long,
)
