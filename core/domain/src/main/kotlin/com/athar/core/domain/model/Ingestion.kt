package com.athar.core.domain.model

import kotlinx.datetime.Instant

/**
 * The single ingestion event type for every source — SMS, notification, future Open Banking.
 *
 * Per Master Brief §5.4, the parser/categorizer/persistence layers consume only this shape
 * and are deliberately source-agnostic so distribution flavors are a config change, not a rewrite.
 */
data class RawIngestEvent(
    val id: String,
    val source: IngestSource,
    val sender: String,
    val body: String,
    val receivedAt: Instant,
    val rawId: String,
)

/**
 * Result of categorizing a single transaction. Master Brief §4.7 contract.
 */
data class CategorySuggestion(
    val categoryId: String?,
    val confidence: Float,
    val source: CategorySource,
    val ruleId: String?,
)

/**
 * The sink that any ingestion source (SMS receiver, notification listener,
 * future Open Banking adapter) hands a [RawIngestEvent] to.
 *
 * Implementation lives in the app or a service module that owns the parse/persist queue.
 */
fun interface RawIngestDispatcher {
    fun enqueue(event: RawIngestEvent)
}
