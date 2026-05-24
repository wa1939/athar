package com.athar.core.data.mapper

import com.athar.core.common.money.Money
import com.athar.core.data.db.entity.CategoryEntity
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind

internal fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    nameAr = nameAr,
    kind = CategoryKind.valueOf(kind),
    icon = icon,
    monthlyTarget = monthlyTargetMinor?.let { Money.ofMinor(it, currency) },
    archived = archived,
    sortOrder = sortOrder,
)

internal fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    nameAr = nameAr,
    kind = kind.name,
    icon = icon,
    monthlyTargetMinor = monthlyTarget?.toMinor(),
    currency = monthlyTarget?.currency ?: Money.SAR,
    archived = archived,
    sortOrder = sortOrder,
)

internal fun Money.toMinor(fractionDigits: Int = 2): Long =
    amount.movePointRight(fractionDigits).toLong()
