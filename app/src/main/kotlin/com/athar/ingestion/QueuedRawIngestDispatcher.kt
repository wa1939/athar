package com.athar.ingestion

import com.athar.core.domain.model.RawIngestDispatcher
import com.athar.core.domain.model.RawIngestEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Non-blocking receiver entry point with a single ingestion worker.
 *
 * SMS backfill can enqueue thousands of rows in a tight loop. Running each row in its own
 * coroutine overwhelms Room invalidation and makes UI flows look stale until Activity recreate.
 * The queue preserves enqueue order and guarantees only one pipeline write chain runs at a time.
 */
@Singleton
class QueuedRawIngestDispatcher private constructor(
    private val pipeline: SmsIngestionPipeline,
    private val scope: CoroutineScope,
    capacity: Int,
) : RawIngestDispatcher {

    @Inject
    constructor(
        pipeline: SmsIngestionPipeline,
    ) : this(
        pipeline = pipeline,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        capacity = Channel.UNLIMITED,
    )

    internal constructor(
        pipeline: SmsIngestionPipeline,
        scope: CoroutineScope,
    ) : this(
        pipeline = pipeline,
        scope = scope,
        capacity = Channel.UNLIMITED,
    )

    private val events = Channel<RawIngestEvent>(capacity = capacity)

    init {
        scope.launch {
            for (event in events) {
                processSafely(event)
            }
        }
    }

    override fun enqueue(event: RawIngestEvent) {
        val result = events.trySend(event)
        if (result.isFailure) {
            Timber.e(
                result.exceptionOrNull(),
                "[ingest-queue] failed to enqueue rawId=%s sender=%s",
                event.rawId,
                event.sender,
            )
        }
    }

    private suspend fun processSafely(event: RawIngestEvent) {
        try {
            pipeline.process(event)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Timber.e(error, "[ingest-queue] failed rawId=%s sender=%s", event.rawId, event.sender)
        }
    }
}
