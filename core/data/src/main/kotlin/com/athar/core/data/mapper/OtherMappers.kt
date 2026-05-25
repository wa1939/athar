package com.athar.core.data.mapper

import com.athar.core.common.money.Money
import com.athar.core.data.db.entity.AccountEntity
import com.athar.core.data.db.entity.CategoryRuleEntity
import com.athar.core.data.db.entity.InvestmentContributionEntity
import com.athar.core.data.db.entity.InvestmentPoolEntity
import com.athar.core.data.db.entity.RecurringRuleEntity
import com.athar.core.data.db.entity.WishlistEntity
import com.athar.core.domain.model.Account
import com.athar.core.domain.model.AccountType
import com.athar.core.domain.model.CategoryRule
import com.athar.core.domain.model.InvestmentContribution
import com.athar.core.domain.model.InvestmentPool
import com.athar.core.domain.model.Cadence
import com.athar.core.domain.model.PatternType
import com.athar.core.domain.model.RecurringRule
import com.athar.core.domain.model.TxType
import com.athar.core.domain.model.WishlistItem
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.YearMonth

private val json = Json { ignoreUnknownKeys = true }
private val stringListSerializer = ListSerializer(String.serializer())

internal fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    type = AccountType.valueOf(type),
    currency = currency,
    smsSenders = json.decodeFromString(stringListSerializer, smsSenders),
    active = active,
    createdAt = createdAt,
)

internal fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    type = type.name,
    currency = currency,
    smsSenders = json.encodeToString(stringListSerializer, smsSenders),
    active = active,
    createdAt = createdAt,
)

internal fun CategoryRuleEntity.toDomain(): CategoryRule = CategoryRule(
    id = id,
    pattern = pattern,
    patternType = PatternType.valueOf(patternType),
    categoryId = categoryId,
    priority = priority,
    learnedFromUser = learnedFromUser,
    createdAt = createdAt,
)

internal fun CategoryRule.toEntity(): CategoryRuleEntity = CategoryRuleEntity(
    id = id,
    pattern = pattern,
    patternType = patternType.name,
    categoryId = categoryId,
    priority = priority,
    learnedFromUser = learnedFromUser,
    createdAt = createdAt,
)

internal fun WishlistEntity.toDomain(): WishlistItem = WishlistItem(
    id = id,
    name = name,
    cost = Money.ofMinor(costMinor, currency),
    currentSaved = Money.ofMinor(currentSavedMinor, currency),
    desiredMonths = desiredMonths,
    startMonth = YearMonth.of(startYear, startMonth),
    notes = notes,
)

internal fun WishlistItem.toEntity(): WishlistEntity = WishlistEntity(
    id = id,
    name = name,
    costMinor = cost.toMinor(),
    currentSavedMinor = currentSaved.toMinor(),
    currency = cost.currency,
    desiredMonths = desiredMonths,
    startYear = startMonth.year,
    startMonth = startMonth.monthValue,
    notes = notes,
)

internal fun InvestmentPoolEntity.toDomain(): InvestmentPool = InvestmentPool(
    id = id,
    name = name,
    period = period,
    totalReturn = Money.ofMinor(totalReturnMinor, currency),
)

internal fun InvestmentPool.toEntity(): InvestmentPoolEntity = InvestmentPoolEntity(
    id = id,
    name = name,
    period = period,
    totalReturnMinor = totalReturn.toMinor(),
    currency = totalReturn.currency,
)

internal fun InvestmentContributionEntity.toDomain(): InvestmentContribution = InvestmentContribution(
    id = id,
    poolId = poolId,
    ownerName = ownerName,
    amount = Money.ofMinor(amountMinor, currency),
)

internal fun InvestmentContribution.toEntity(): InvestmentContributionEntity = InvestmentContributionEntity(
    id = id,
    poolId = poolId,
    ownerName = ownerName,
    amountMinor = amount.toMinor(),
    currency = amount.currency,
)

internal fun RecurringRuleEntity.toDomain(): RecurringRule = RecurringRule(
    id = id,
    displayName = displayName,
    merchant = merchant,
    amount = Money.ofMinor(amountMinor, amountCurrency),
    type = TxType.valueOf(type),
    accountId = accountId,
    categoryId = categoryId,
    cadence = Cadence.valueOf(cadence),
    dayOfMonth = dayOfMonth,
    dayOfWeek = dayOfWeek,
    monthOfYear = monthOfYear,
    nextRunDate = LocalDate.parse(nextRunDate),
    lastRunDate = lastRunDate?.let { LocalDate.parse(it) },
    isActive = isActive,
    notes = notes,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
)

internal fun RecurringRule.toEntity(): RecurringRuleEntity = RecurringRuleEntity(
    id = id,
    displayName = displayName,
    merchant = merchant,
    amountMinor = amount.toMinor(),
    amountCurrency = amount.currency,
    type = type.name,
    accountId = accountId,
    categoryId = categoryId,
    cadence = cadence.name,
    dayOfMonth = dayOfMonth,
    dayOfWeek = dayOfWeek,
    monthOfYear = monthOfYear,
    nextRunDate = nextRunDate.toString(),
    lastRunDate = lastRunDate?.toString(),
    isActive = isActive,
    notes = notes,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
)
