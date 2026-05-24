package com.athar.core.domain.model

import com.athar.core.common.money.Money
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class Transaction(
    val id: String,
    val accountId: String,
    val type: TxType,
    val amount: Money,
    val date: LocalDate,
    val occurredAt: Instant?,
    val merchant: String,
    val merchantNormalized: String,
    val categoryId: String?,
    val notes: String?,
    val source: IngestSource,
    val sourceRefId: String?,
    val status: TxStatus,
    val confidence: Float?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
