package com.athar.core.domain.model

import kotlinx.datetime.Instant

data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val currency: String,
    val smsSenders: List<String>,
    val active: Boolean,
    val createdAt: Instant,
)
