# H-18 - History Top Group Cleanup Mode

## Problem

H-17 lets users explicitly apply a safe suggestion to the largest repeated
backlog group without selecting it first. After the apply succeeds, the bulk
card closed because it reused the normal selected-row bulk apply behavior.
That forces one extra tap before the user can apply the next largest repeated
group.

## Decision

Keep History selection mode open after the direct top-group suggestion action
finishes, but clear the selected row set.

- Normal selected-row bulk apply still exits selection mode.
- Direct top-group suggestion apply keeps the cleanup card visible.
- The applied group still uses the same type-safe bulk helper, exact-rule
  learning, transfer skipping, mixed-type safeguards, and result toast.
- After the transaction list refreshes, the card can expose the next largest
  repeated group and its conservative H-15 suggestion.
- The card shows zero selected rows until the user selects rows or applies the
  next valid top-group suggestion.

## Acceptance

- Applying a top repeated suggestion updates the largest group and learns the
  exact local rule when the group is eligible.
- Selection mode remains open after that direct top-group apply.
- Selected IDs are cleared after the apply.
- The repeated-backlog result advances to the next visible group after the
  repository emits the updated rows.
- Normal selected-row bulk apply still clears selection mode.

## Non-goals

- Do not auto-apply the next group.
- Do not keep normal selected-row bulk apply open.
- Do not broaden suggestion criteria or grouping behavior.
- Do not change the visible chip copy.

## Validation

- 2026-06-15: Focused `HistoryFilterTest` and `HistoryViewModelTest` passed with
  JDK 17, `--no-daemon`, and `--max-workers=1`.
- 2026-06-15: Full `:feature:today:testDebugUnitTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-15: Full JVM/debug-APK/lint stack passed with JDK 17:
  `test`, `:app:assemblePersonalFullSmsDebug`, `:app:assembleStoreSafeDebug`,
  `:app:lintPersonalFullSmsDebug`, and `:app:lintStoreSafeDebug`.
- 2026-06-15: `git diff --check` passed.
- 2026-06-15: Runtime `personalFullSmsDebug` install was attempted with the
  built APK. The local AVD exists, but `emulator -accel-check` reports the
  Android Emulator hypervisor driver is not installed. A bounded hidden
  `AtharPixelQaApi35` launch through
  `C:\Users\waok\Android\Sdk\emulator\emulator.exe` exited before exposing an
  online ADB device or `boot_completed=1` with exit code `1`, so install,
  screenshot, UI dump, and logcat capture could not run. Evidence was written
  to `build/qa/history-top-group-cleanup-mode-emulator/`; cleanup left no
  emulator/qemu/netsim process and no AVD lock files.
