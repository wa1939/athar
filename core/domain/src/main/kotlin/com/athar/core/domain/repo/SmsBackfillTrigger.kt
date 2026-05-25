package com.athar.core.domain.repo

import kotlinx.coroutines.flow.StateFlow

/**
 * Triggers a one-shot scan of the device SMS inbox into the ingestion pipeline.
 *
 * Implementation lives in `app` (needs ContentResolver). Feature modules depend on this
 * interface so the UI can call `backfill()` and observe progress without pulling Android deps.
 */
interface SmsBackfillTrigger {
    val progress: StateFlow<BackfillProgress>
    /** [daysBack] = null means scan the entire SMS inbox (no cutoff). */
    suspend fun backfill(daysBack: Int? = null)
}

sealed interface BackfillProgress {
    data object Idle : BackfillProgress
    data class Running(val scanned: Int) : BackfillProgress
    data class Done(val scanned: Int, val sentToPipeline: Int) : BackfillProgress
    data class Failed(val reason: String) : BackfillProgress
}
