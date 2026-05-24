package com.athar.core.domain.model

import kotlinx.datetime.Instant

data class CategoryRule(
    val id: String,
    val pattern: String,
    val patternType: PatternType,
    val categoryId: String,
    val priority: Int,
    val learnedFromUser: Boolean,
    val createdAt: Instant,
)
