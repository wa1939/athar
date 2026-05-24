package com.athar.core.domain.repo

import com.athar.core.domain.model.UserTemplate
import kotlinx.coroutines.flow.Flow

interface UserTemplateRepository {
    fun observeAll(): Flow<List<UserTemplate>>
    suspend fun upsert(template: UserTemplate)
    suspend fun delete(id: String)
    suspend fun get(id: String): UserTemplate?
}
