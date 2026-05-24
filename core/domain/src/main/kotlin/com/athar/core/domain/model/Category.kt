package com.athar.core.domain.model

import com.athar.core.common.money.Money

data class Category(
    val id: String,
    val name: String,
    val nameAr: String,
    val kind: CategoryKind,
    val icon: String?,
    val monthlyTarget: Money?,
    val archived: Boolean,
    val sortOrder: Int,
)
