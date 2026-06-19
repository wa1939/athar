package com.athar.ingestion

import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.RawIngestEvent
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueuedRawIngestDispatcherTest {

    @Test
    fun `processes enqueued events in order with one active pipeline call`() = runTest {
        val pipeline = mockk<SmsIngestionPipeline>()
        val processed = mutableListOf<String>()
        var active = 0
        var maxActive = 0
        coEvery { pipeline.process(any()) } coAnswers {
            active += 1
            maxActive = maxOf(maxActive, active)
            processed += firstArg<RawIngestEvent>().rawId
            delay(10)
            active -= 1
        }

        val workerScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val dispatcher = QueuedRawIngestDispatcher(pipeline, workerScope)

        try {
            dispatcher.enqueue(event("sms-1"))
            dispatcher.enqueue(event("sms-2"))
            dispatcher.enqueue(event("sms-3"))
            advanceUntilIdle()

            assertThat(processed).containsExactly("sms-1", "sms-2", "sms-3").inOrder()
            assertThat(maxActive).isEqualTo(1)
        } finally {
            workerScope.cancel()
        }
    }

    @Test
    fun `continues processing after one event fails`() = runTest {
        val pipeline = mockk<SmsIngestionPipeline>()
        val processed = mutableListOf<String>()
        coEvery { pipeline.process(match { it.rawId == "bad-sms" }) } throws IllegalStateException("boom")
        coEvery { pipeline.process(match { it.rawId != "bad-sms" }) } coAnswers {
            processed += firstArg<RawIngestEvent>().rawId
        }

        val workerScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val dispatcher = QueuedRawIngestDispatcher(pipeline, workerScope)

        try {
            dispatcher.enqueue(event("sms-1"))
            dispatcher.enqueue(event("bad-sms"))
            dispatcher.enqueue(event("sms-2"))
            advanceUntilIdle()

            assertThat(processed).containsExactly("sms-1", "sms-2").inOrder()
            coVerify(exactly = 1) { pipeline.process(match { it.rawId == "bad-sms" }) }
        } finally {
            workerScope.cancel()
        }
    }

    private fun event(rawId: String): RawIngestEvent = RawIngestEvent(
        id = rawId,
        source = IngestSource.SMS,
        sender = "AlRajhiBank",
        body = "test",
        receivedAt = Instant.parse("2026-05-27T12:00:00Z"),
        rawId = rawId,
    )
}
