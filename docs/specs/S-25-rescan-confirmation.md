# S-25 Rescan Confirmation

## Goal

Make the SMS rescan-and-clean action safer for real users. The action is useful after parser updates, but it deletes every pending transaction before rereading the SMS log. A single accidental tap should not clear the user's review queue.

## Inputs

- Current pending transaction count from `TransactionRepository.observePending()`.
- Existing `RescanStatus` state.
- Existing `SmsBackfillTrigger.backfill()` operation.
- Existing `TransactionRepository.clearPending()` operation.

## Behavior

Settings shows the current number of pending transactions on the rescan card. Tapping "Rescan and clear pending" opens a confirmation dialog instead of immediately running the operation.

The dialog explains that pending transactions will be cleared, confirmed transactions stay untouched, and the SMS log will be read again with the current templates. Confirming closes the dialog and runs the existing `clearPending()` then `backfill()` sequence. Dismissing leaves all data unchanged.

## Acceptance

- The destructive rescan operation cannot start from one tap.
- The user sees how many pending items are at risk before confirming.
- Confirmed transactions remain untouched by the flow.
- Unit tests cover pending-count state and the clear-before-backfill sequence.
