package com.athar.core.data.seed

import com.athar.core.data.db.entity.CategoryEntity
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CategorySeedPlannerTest {

    @Test
    fun `missing bundled categories are appended without replacing existing categories`() {
        val existingRent = category(
            id = "cat-rent",
            name = "My custom rent",
            sortOrder = 40,
            archived = true,
        )
        val existingCoffee = category(
            id = "cat-coffee",
            name = "Coffee",
            sortOrder = 10,
        )
        val bundled = listOf(
            category(id = "cat-rent", name = "Rent", sortOrder = 0),
            category(id = "cat-personal-care", name = "Personal care", sortOrder = 5),
            category(id = "cat-coffee", name = "Coffee", sortOrder = 6),
            category(id = "cat-pets", name = "Pets", sortOrder = 7),
        )

        val missing = CategorySeedPlanner.missingBundledCategories(
            existing = listOf(existingRent, existingCoffee),
            bundled = bundled,
        )

        assertThat(missing.map { it.id }).containsExactly("cat-personal-care", "cat-pets").inOrder()
        assertThat(missing.map { it.sortOrder }).containsExactly(41, 42).inOrder()
        assertThat(missing.map { it.name }).containsExactly("Personal care", "Pets").inOrder()
    }

    @Test
    fun `no missing bundled categories returns empty list`() {
        val existing = listOf(
            category(id = "cat-rent", name = "My custom rent", sortOrder = 8),
            category(id = "cat-coffee", name = "Coffee", sortOrder = 3),
        )
        val bundled = listOf(
            category(id = "cat-rent", name = "Rent", sortOrder = 0),
            category(id = "cat-coffee", name = "Coffee", sortOrder = 1),
        )

        val missing = CategorySeedPlanner.missingBundledCategories(existing, bundled)

        assertThat(missing).isEmpty()
    }

    private fun category(
        id: String,
        name: String,
        sortOrder: Int,
        archived: Boolean = false,
    ): CategoryEntity = CategoryEntity(
        id = id,
        name = name,
        nameAr = name,
        kind = "EXPENSE",
        icon = null,
        monthlyTargetMinor = if (id == "cat-rent") 100_000 else null,
        currency = "SAR",
        archived = archived,
        sortOrder = sortOrder,
    )
}
