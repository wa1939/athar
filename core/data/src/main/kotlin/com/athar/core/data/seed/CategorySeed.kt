package com.athar.core.data.seed

import android.content.Context
import com.athar.core.data.db.dao.CategoryDao
import com.athar.core.data.db.entity.CategoryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

/**
 * Seeds the Category table from `assets/seed_categories.json`.
 *
 * Existing rows are never overwritten, so user edits, archived categories, targets,
 * and custom sort order survive app upgrades. Missing bundled categories are
 * appended after the user's current order so future built-in category additions
 * can reach existing installs. Master Brief §10.2 / Backlog F-10.
 */
internal class CategorySeed @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CategoryDao,
) {
    suspend fun seedMissingBundledCategories() {
        val bundled = loadBundledCategories()
        val existing = dao.all()
        if (existing.isEmpty()) {
            dao.upsertAll(bundled)
            Timber.i("Seeded ${bundled.size} categories.")
            return
        }

        val missing = CategorySeedPlanner.missingBundledCategories(existing, bundled)
        if (missing.isEmpty()) {
            Timber.d("Category table up-to-date (${existing.size}) — skipping seed.")
            return
        }

        dao.upsertAll(missing)
        Timber.i("Seeded ${missing.size} missing bundled categories.")
    }

    private fun loadBundledCategories(): List<CategoryEntity> {
        val raw = context.assets.open("seed_categories.json").bufferedReader().use { it.readText() }
        val payload = SeedJson.decodeFromString<SeedPayload>(raw)
        return payload.categories.mapIndexed { index, dto ->
            dto.toEntity(sortOrder = dto.sortOrder ?: index)
        }
    }

    @Serializable
    private data class SeedPayload(val categories: List<SeedCategory>)

    @Serializable
    private data class SeedCategory(
        val id: String,
        val name: String,
        val nameAr: String,
        val kind: String,
        val icon: String? = null,
        val sortOrder: Int? = null,
    ) {
        fun toEntity(sortOrder: Int): CategoryEntity = CategoryEntity(
            id = id,
            name = name,
            nameAr = nameAr,
            kind = kind,
            icon = icon,
            monthlyTargetMinor = null,
            currency = "SAR",
            archived = false,
            sortOrder = sortOrder,
        )
    }

    private companion object {
        val SeedJson = Json { ignoreUnknownKeys = true }
    }
}

internal object CategorySeedPlanner {
    fun missingBundledCategories(
        existing: List<CategoryEntity>,
        bundled: List<CategoryEntity>,
    ): List<CategoryEntity> {
        val existingIds = existing.mapTo(mutableSetOf()) { it.id }
        val firstSortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        return bundled
            .filterNot { it.id in existingIds }
            .mapIndexed { index, category ->
                category.copy(sortOrder = firstSortOrder + index)
            }
    }
}
