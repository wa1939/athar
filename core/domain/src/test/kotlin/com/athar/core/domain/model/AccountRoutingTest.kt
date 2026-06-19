package com.athar.core.domain.model

import com.athar.core.common.money.Money
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class AccountRoutingTest {

    @Test
    fun `routes by configured sender alias`() {
        val alRajhi = account(id = "alrajhi", aliases = listOf("AlRajhiBank"))

        val resolved = AccountRouting.resolve(
            accounts = listOf(alRajhi, account(id = "cash")),
            sender = "AlRajhiBank",
            body = "Purchase SAR 10",
            counterparty = null,
        )

        assertThat(resolved?.id).isEqualTo("alrajhi")
    }

    @Test
    fun `numeric account or card tail beats shared bank sender`() {
        val checking = account(id = "checking", aliases = listOf("AlRajhiBank", "0930"))
        val credit = account(id = "credit", aliases = listOf("AlRajhiBank", "9803"))

        val resolved = AccountRouting.resolve(
            accounts = listOf(checking, credit),
            sender = "AlRajhiBank",
            body = "PoS purchase\nCard: **9803\nAmount: SAR 25.00",
            counterparty = null,
        )

        assertThat(resolved?.id).isEqualTo("credit")
    }

    @Test
    fun `arabic digits in message match ascii alias`() {
        val account = account(id = "card", aliases = listOf("9803"))

        val resolved = AccountRouting.resolve(
            accounts = listOf(account),
            sender = "AlRajhiBank",
            body = "البطاقة: **٩٨٠٣",
            counterparty = null,
        )

        assertThat(resolved?.id).isEqualTo("card")
    }

    @Test
    fun `ambiguous sender match returns null instead of guessing`() {
        val checking = account(id = "checking", aliases = listOf("AlRajhiBank"))
        val credit = account(id = "credit", aliases = listOf("AlRajhiBank"))

        val resolved = AccountRouting.resolve(
            accounts = listOf(checking, credit),
            sender = "AlRajhiBank",
            body = "Purchase SAR 25.00",
            counterparty = null,
        )

        assertThat(resolved).isNull()
    }

    @Test
    fun `archived accounts are ignored`() {
        val archived = account(id = "old", aliases = listOf("AlRajhiBank"), archived = true)

        val resolved = AccountRouting.resolve(
            accounts = listOf(archived),
            sender = "AlRajhiBank",
            body = "Purchase SAR 10",
            counterparty = null,
        )

        assertThat(resolved).isNull()
    }

    @Test
    fun `alias input is normalized and deduplicated`() {
        val aliases = AccountRouting.normalizeAliases(" AlRajhiBank, alrajhibank\n٩٨٠٣; ")

        assertThat(aliases).containsExactly("alrajhibank", "9803").inOrder()
    }

    private fun account(
        id: String,
        aliases: List<String> = emptyList(),
        archived: Boolean = false,
    ): Account = Account(
        id = id,
        name = id,
        type = AccountType.CHECKING,
        currency = "SAR",
        openingBalance = Money.zero("SAR"),
        smsSenders = aliases,
        notes = null,
        sortOrder = 0,
        archived = archived,
        createdAt = Instant.parse("2026-06-13T00:00:00Z"),
        updatedAt = Instant.parse("2026-06-13T00:00:00Z"),
    )
}
