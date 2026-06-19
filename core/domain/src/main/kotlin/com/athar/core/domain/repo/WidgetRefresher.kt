package com.athar.core.domain.repo

/**
 * Requests home-screen widget refreshes after ledger mutations.
 *
 * The implementation lives in the widgets feature so core:data does not know about Glance.
 * Regular repository writes should call [requestRefresh] so bursts can be coalesced; direct
 * widget actions can call [refreshNow] after their write has completed.
 */
interface WidgetRefresher {
    fun requestRefresh()
    suspend fun refreshNow()
}
