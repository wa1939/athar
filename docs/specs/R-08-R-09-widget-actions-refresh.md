# R-08/R-09 — Pending Widget Actions and Freshness

## Problem

The beta.21 Pending widget showed the top pending transactions, but every tap only opened the app.
That made the widget informative but not useful for the most common pending-tray decisions:
confirming a correctly categorized transaction or dismissing noise. Widgets also refreshed only on
the system's 30-minute cadence, so in-app Confirm/Dismiss could leave stale counts on the home
screen.

## Decision

Add direct Pending-widget actions for the safe decisions:

- **Confirm** sets the transaction status to `CONFIRMED`.
- **Dismiss** sets the transaction status to `DISMISSED`.
- **Categorize** opens Athar, because choosing a category still needs the full edit sheet and
  "Always categorize X" learning flow.

Glance action callbacks enqueue a Hilt `CoroutineWorker` so repository writes run through the same
injected data layer as the app. `AtharApplication` now provides `HiltWorkerFactory` to WorkManager.

For freshness, `TransactionRepositoryImpl` requests a widget refresh after transaction mutations.
The concrete Glance implementation debounces bursts, so SMS imports or bulk actions do not call
`updateAll(context)` for every single row. Widget-originated workers also request an immediate
refresh after the status update.

## Acceptance Criteria

- Pending widget shows action chips beside pending rows.
- Confirm/Dismiss from the widget call `TransactionRepository.setStatus`.
- Action workers refresh Month, Today, and Pending widgets after the mutation.
- In-app transaction mutations request widget refreshes without coupling `core:data` to Glance.
- Burst writes are coalesced before widget refresh.
- Categorize still opens the app rather than guessing a category from the widget surface.

## Implementation

- `PendingWidgetActions.kt` defines Glance callbacks and a Hilt `PendingTransactionActionWorker`.
- `AtharApplication` registers `HiltWorkerFactory`.
- `WidgetRefresher` in `core:domain` is implemented by `GlanceWidgetRefresher` in `feature:widgets`.
- `TransactionRepositoryImpl` calls `WidgetRefresher.requestRefresh()` after mutating operations.

## Validation

- `:feature:widgets:compileDebugKotlin`
- `:app:assemblePersonalFullSmsDebug`

Runtime widget tapping still needs device/emulator validation; no Android device is currently
attached in this environment.
