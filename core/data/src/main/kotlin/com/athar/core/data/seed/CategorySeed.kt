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
 * Seeds the Category table from `assets/seed_categories.json` on first run.
 *
 * Idempotent: only runs when the table is empty. Master Brief §10.2 / Backlog F-10.
 */
internal class CategorySeed @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CategoryDao,
) {
    suspend fun seedIfEmpty() {
        if (dao.count() > 0) {
            Timber.d("Category table not empty — skipping seed.")
            return
        }
        val raw = context.assets.open("seed_categories.json").bufferedReader().use { it.readText() }
        val payload = Json { ignoreUnknownKeys = true }
            .decodeFromString<SeedPayload>(raw)
        val entities = payload.categories.mapIndexed { index, dto ->
            CategoryEntity(
                id = dto.id,
                name = dto.name,
                nameAr = dto.nameAr,
                kind = dto.kind,
                icon = dto.icon,
                monthlyTargetMinor = null,
                currency = "SAR",
                archived = false,
                sortOrder = dto.sortOrder ?: index,
            )
        }
        dao.upsertAll(entities)
        Timber.i("Seeded ${entities.size} categories.")
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
    )
}
