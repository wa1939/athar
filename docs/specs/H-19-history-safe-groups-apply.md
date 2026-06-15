# H-19 - History Safe Groups Apply

## Problem

H-18 keeps the repeated-backlog cleanup card open after applying the current top
group, but a user still has to repeat one explicit action per group even when
several visible repeated groups already have conservative local-history
suggestions.

## Decision

Add an explicit `Apply safe groups (<count>)` action to the History bulk card
when more than one visible repeated-backlog group has a valid H-15 suggestion.

- The action appears only in `Repeated backlog` mode while selection mode is
  open.
- It appears only when at least two visible repeated groups have safe
  suggestions, so the existing top-group action remains the single-group path.
- Each group must pass the same conservative H-15 same-merchant history rule:
  one specific merchant key, one active compatible category, and no conflicting
  local category history.
- Tapping the action applies each safe group through the same type-safe bulk
  helper used by selected-row and top-group apply.
- Conflicting, inactive-category, generic-merchant, transfer, and mixed-type
  groups stay untouched.
- The result is still explicit user action, not auto-apply.

## Acceptance

- The bulk card exposes the number of safe repeated groups when more than one
  is available.
- Applying safe groups updates all and only those safe groups.
- Conflicting repeated groups remain visible and uncategorized.
- The aggregated result toast reports the total rows updated.
- Exact local rules are learned for eligible applied groups.
- Selection mode remains open and selected rows are cleared after the apply.

## Non-goals

- Do not auto-apply repeated groups.
- Do not apply groups with conflicting same-merchant category history.
- Do not apply groups whose suggested category is inactive or wrong type.
- Do not replace the single top-group action.
- Do not broaden repeated-backlog grouping or H-15 suggestion criteria.

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
  to `build/qa/history-safe-groups-apply-emulator/`; cleanup left no
  emulator/qemu/netsim process and no AVD lock files.
