# H-03 Category Backfill Feedback

## Goal

Make the "Always categorize X as Y" learning flow visibly trustworthy from both Today and History. When the app applies the learned category to matching pending or dismissed rows, the user should know whether existing rows were fixed or only future rows will benefit.

## Inputs

- Existing `TodayViewModel.lastBackfill` and `HistoryViewModel.lastBackfill` events.
- Existing `TransactionRepository.applyCategoryToMatching(pattern, categoryId)` behavior.
- Existing `AtharCard` transient feedback pattern used elsewhere in Settings.

## Behavior

After the user saves an edited transaction with the "Always" option:

- The transaction is confirmed and the user-learned category rule is stored.
- Matching PENDING and DISMISSED rows are backfilled by the existing repository path.
- Today and History show a tappable feedback card for about four seconds.
- If at least one existing row was changed, the card shows the number of other matching rows categorized.
- If no existing row matched, the card says future matching transactions will be categorized automatically instead of showing "0 updated".

## Acceptance

- Today observes `lastBackfill`, shows the localized feedback card, and auto-clears it.
- History observes `lastBackfill`, shows the same localized feedback card, and auto-clears it.
- The feedback card can be dismissed manually.
- Arabic and English strings cover both existing-row and future-only outcomes.
- A focused `TodayViewModel` test verifies learned-rule save, backfill invocation, event emission, and clearing.

## Validation

- `:feature:today:testDebugUnitTest`
- Full JVM/build/lint gate: `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime install/launch remains blocked on this PC: no ADB device is attached, and `AtharPixelQaApi35` exits because x86_64 emulator images require hardware acceleration.
