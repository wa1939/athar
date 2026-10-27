package com.athar.ingestion

import android.content.Context
import android.content.SharedPreferences
import com.athar.core.domain.model.CategorySource
import com.athar.core.domain.model.CategorySuggestion
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.MerchantCatalogRepository
import com.athar.core.domain.repo.TransactionRepository
import com.athar.core.testing.Fixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

/**
 * WM084: stored-row recategorization contract — fills only missing categories,
 * never overwrites user intent, never touches TRANSFER, updates the marker once.
 */
class StoredTransactionRecategorizerTest {

    private val seedPrefs = mockk<SharedPreferences> {
        every { getString("rule_seed_payload_sha256", null) } returns "sha-A"
    }
    private val recatPrefs = mockk<SharedPreferences> {
        every { getString("last_rules_sha", null) } returns null
        every { getString("last_catalog_version", null) } returns null
    }
    private val editor = mockk<SharedPreferences.Editor> {
        every { putString(any(), any<String>()) } returns this
        every { apply() } just runs
    }
    private val context = mockk<Context> {
        every { getSharedPreferences("athar_seed_markers", Context.MODE_PRIVATE) } returns seedPrefs
        every { getSharedPreferences("athar_recategorizer", Context.MODE_PRIVATE) } returns recatPrefs
        every { recatPrefs.edit() } returns editor
    }

    private fun tx(merchant: String, categoryId: String?, type: TxType = TxType.EXPENSE) =
        Fixtures.transaction(
            id = "tx-${merchant.replace(' ', '-')}",
            merchant = merchant,
        ).copy(type = type, categoryId = categoryId)

    @Test
    fun `fills missing category and leaves user-assigned and transfer rows untouched`() = kotlinx.coroutines.runBlocking {
        val uncategorized = tx("uber pending", null)
        val userCategorized = tx("coffee corner", "cat-coffee").copy(categorySource = CategorySource.USER)
        val transfer = tx("self transfer", null, TxType.TRANSFER)
        val rows = listOf(uncategorized, userCategorized, transfer)
        val repo = mockk<TransactionRepository> {
            coEvery { observeAll() } returns MutableStateFlow(rows)
            coEvery { upsert(any()) } just runs
        }
        val resolver = mockk<MerchantCategoryResolver> {
            coEvery { resolve(any<String>(), any<TxType>()) } answers {
                val merchant = firstArg<String>()
                if (merchant == "uber pending") {
                    MerchantCategoryResolution(
                        CategorySuggestion("cat-public-transport", 1.0f, CategorySource.RULE_EXACT, "r1"),
                        hadTrustedLocalRule = false,
                    )
                } else {
                    MerchantCategoryResolution(
                        CategorySuggestion(null, 0f, CategorySource.UNKNOWN, null),
                        hadTrustedLocalRule = false,
                    )
                }
            }
        }
        val catalog = mockk<MerchantCatalogRepository> { coEvery { snapshot() } returns null }
        val recat = StoredTransactionRecategorizer(context, repo, resolver, catalog, Clock.System)

        val result = recat.recategorizeIfRulesChanged()

        assertThat(result.candidates).isEqualTo(1) // only the blank-category expense
        assertThat(result.categorized).isEqualTo(1)
        coVerify(exactly = 1) {
            repo.upsert(match { it.id == "tx-uber-pending" && it.categoryId == "cat-public-transport" })
        }
        coVerify(exactly = 0) { repo.upsert(match { it.id == "tx-self-transfer" || it.id == "tx-coffee-corner" }) }
        verify(exactly = 1) { editor.putString("last_rules_sha", "sha-A") }
        verify(exactly = 1) { editor.apply() }
    }

    @Test
    fun `skips the pass entirely when the marker already matches`() = kotlinx.coroutines.runBlocking {
        every { recatPrefs.getString("last_rules_sha", null) } returns "sha-A"
        every { recatPrefs.getString("last_catalog_version", null) } returns "none"
        val repo = mockk<TransactionRepository> { coEvery { observeAll() } returns MutableStateFlow(emptyList()) }
        val resolver = mockk<MerchantCategoryResolver>()
        val catalog = mockk<MerchantCatalogRepository> { coEvery { snapshot() } returns null }
        val recat = StoredTransactionRecategorizer(context, repo, resolver, catalog, Clock.System)

        val result = recat.recategorizeIfRulesChanged()

        assertThat(result.candidates).isEqualTo(0)
        coVerify(exactly = 0) { repo.upsert(any()) }
    }

    private companion object {
        val DUMMY_INSTANT: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }
}
