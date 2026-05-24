package com.athar.core.domain.model

import com.athar.core.common.money.Money

data class InvestmentPool(
    val id: String,
    val name: String,
    val period: String,
    val totalReturn: Money,
)

data class InvestmentContribution(
    val id: String,
    val poolId: String,
    val ownerName: String,
    val amount: Money,
)
