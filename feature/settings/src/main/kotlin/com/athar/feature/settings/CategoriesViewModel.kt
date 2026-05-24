package com.athar.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.domain.model.Category
import com.athar.core.domain.repo.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesState(
    val items: ImmutableList<Category>,
    val isLoading: Boolean,
) {
    companion object {
        fun initial(): CategoriesState = CategoriesState(persistentListOf(), isLoading = true)
    }
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repo: CategoryRepository,
) : ViewModel() {

    val state: StateFlow<CategoriesState> =
        repo.observeAll(kind = null, includeArchived = true)
            .map { CategoriesState(items = it.toImmutableList(), isLoading = false) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesState.initial())

    fun save(category: Category) {
        viewModelScope.launch { repo.upsert(category) }
    }

    fun archive(id: String) {
        viewModelScope.launch { repo.archive(id) }
    }

    fun moveUp(category: Category) {
        viewModelScope.launch { reorder(category, delta = -1) }
    }

    fun moveDown(category: Category) {
        viewModelScope.launch { reorder(category, delta = +1) }
    }

    private suspend fun reorder(category: Category, delta: Int) {
        val all = state.value.items
        val idx = all.indexOfFirst { it.id == category.id }
        if (idx < 0) return
        val target = (idx + delta).coerceIn(0, all.size - 1)
        if (target == idx) return
        val mutable = all.toMutableList()
        val removed = mutable.removeAt(idx)
        mutable.add(target, removed)
        repo.reorder(mutable.map { it.id })
    }
}
