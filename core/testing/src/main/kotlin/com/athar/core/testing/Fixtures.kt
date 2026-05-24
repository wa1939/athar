package com.athar.core.testing

import com.athar.core.common.money.Money
import com.athar.core.domain.model.Account
import com.athar.core.domain.model.AccountType
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Test fixtures shared across module test suites.
 *
 * Use these to write tight, readable tests. Override only the field that matters.
 */
object Fixtures {

    val fixedInstant: Instant = Instant.parse("2026-02-14T14:32:00Z")
    val fixedDate: LocalDate = LocalDate(2026, 2, 14)

    fun account(
        id: String = "acc-1",
        name: String = "Al Rajhi Visa",
        smsSenders: List<String> = listOf("AlRajhiBank"),
    ): Account = Account(
        id = id,
        name = name,
        type = AccountType.CREDIT,
        currency = Money.SAR,
        smsSenders = smsSenders,
        active = true,
        createdAt = fixedInstant,
    )

    fun category(
        id: String = "cat-coffee",
        name: String = "Coffee",
        nameAr: String = "قهوة",
        kind: CategoryKind = CategoryKind.EXPENSE,
    ): Category = Category(
        id = id,
        name = name,
        nameAr = nameAr,
        kind = kind,
        icon = null,
        monthlyTarget = null,
        archived = false,
        sortOrder = 0,
    )

    fun transaction(
        id: String = "tx-1",
        amount: Money = Money.of("200"),
        merchant: String = "STARBUCKS",
        status: TxStatus = TxStatus.PENDING,
    ): Transaction = Transaction(
        id = id,
        accountId = "acc-1",
        type = TxType.EXPENSE,
        amount = amount,
        date = fixedDate,
        occurredAt = fixedInstant,
        merchant = merchant,
        merchantNormalized = merchant.lowercase().trim(),
        categoryId = null,
        notes = null,
        source = IngestSource.SMS,
        sourceRefId = "sms-1",
        status = status,
        confidence = 0.9f,
        createdAt = fixedInstant,
        updatedAt = fixedInstant,
    )
}
