# R-01 — Queued SMS Ingestion Dispatcher

## Problem

The first large SMS backfill can enqueue thousands of inbox rows in one tight loop. The old
`RawIngestDispatcher` started one coroutine per event, so a 7,000-message import could create
7,000 concurrent `SmsIngestionPipeline.process()` calls. Each call records audit state, parses,
persists a transaction, and triggers Room invalidation. In practice the Today and History flows
could appear stale until the Activity was recreated.

## Decision

Keep `RawIngestDispatcher.enqueue()` non-suspending for SMS receivers and notification listeners,
but route every event through a singleton Channel-backed queue. A single worker drains the queue
on `Dispatchers.IO`, preserving enqueue order and ensuring only one ingestion write chain is active
at a time. Pipeline failures are logged per event and do not kill the worker.

This fixes the damaging concurrency fan-out without changing the source modules, SMS backfill API,
or parser/persistence contracts.

## Acceptance Criteria

- SMS backfill and live ingestion still call `RawIngestDispatcher.enqueue(event)`.
- Enqueue is non-blocking so Android receivers can return promptly.
- Backfilled rows process in content-provider order (`date ASC`).
- A failed parse/persist for one event is logged and the queue continues with later events.
- Large backfills do not start thousands of concurrent `pipeline.process(event)` jobs.
- No private SMS export files are committed.

## Implementation

- `QueuedRawIngestDispatcher` owns `Channel<RawIngestEvent>(Channel.UNLIMITED)` and a singleton
  worker coroutine.
- `IngestionBindingsModule` binds `RawIngestDispatcher` to `QueuedRawIngestDispatcher`.
- The old provider that created a new coroutine for every event is removed.

## Validation

- `:app:testPersonalFullSmsDebugUnitTest` covers queue order, single active pipeline call, and
  continuation after one event fails.
- `:app:assemblePersonalFullSmsDebug` verifies Hilt wiring and APK packaging for the SMS flavor.

## Follow-ups

If real-device traces show the queue drains too slowly, the next optimization is explicit database
batching around the pipeline writes plus visible queue-drain progress in the backfill UI. That is a
performance enhancement; it is not required to remove the Room invalidation saturation bug.
