# H-20 - History Safe Groups Summary

## Problem

H-19 lets a user apply several safe repeated-backlog suggestions in one explicit
action, but the chip only exposes the number of groups. Before making a bulk
category change, the user should also see the transaction impact so the action
is easy to verify and does not feel like a hidden batch edit.

## Decision

Add a compact localized summary line to the History bulk card when the
`Repeated backlog` safe-groups action is available:

- The summary appears only while selection mode is open and at least two visible
  repeated groups have safe same-merchant history suggestions.
- It reports both the safe repeated-group count and the total transaction count.
- It uses the counts already computed by H-19; it does not change grouping,
  suggestion criteria, application behavior, or rule learning.
- The existing `Apply safe groups (<count>)` chip remains the explicit action.

## Acceptance

- When multiple safe repeated groups are available, the bulk card shows the
  transaction count across those groups before the user applies them.
- The summary stays hidden outside `Repeated backlog`, when only one safe group
  exists, or when safe suggestions are absent/conflicting.
- Existing selected-row, top-group, category-picker, and exact-rule-learning
  behavior stays unchanged.
- English and Arabic strings are localized.

## Non-goals

- Do not auto-apply safe suggestions.
- Do not add a confirmation dialog.
- Do not change H-15/H-19 suggestion safety rules.
- Do not expose raw merchant names or private history details in the summary.

## Validation

- 2026-06-15: Focused `HistoryFilterTest` and `HistoryViewModelTest` passed
  with JDK 17, `--no-daemon`, and `--max-workers=1`.
- 2026-06-15: Full `:feature:today:testDebugUnitTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-15: Full JVM/debug-APK/lint stack passed with JDK 17:
  `test`, `:app:assemblePersonalFullSmsDebug`, `:app:assembleStoreSafeDebug`,
  `:app:lintPersonalFullSmsDebug`, and `:app:lintStoreSafeDebug`.
- 2026-06-15: `git diff --check` passed.
- 2026-06-15: Runtime `personalFullSmsDebug` install was attempted with the
  built APK. The local AVD exists, but `emulator -accel-check` reports the
  Android Emulator hypervisor driver is not installed. A bounded hidden
  `AtharPixelQaApi35` software launch exited before exposing an online ADB
  device or `boot_completed=1` with exit code `-1073741819`, so install,
  screenshot, UI dump, and logcat capture could not run. Evidence was written
  to `build/qa/history-safe-groups-summary-emulator/`; cleanup left no
  emulator/qemu/netsim process and no AVD lock files.
