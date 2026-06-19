# H-21 - History Next Safe Group Apply

## Problem

H-17 lets the largest repeated-backlog group apply its safe local-history
suggestion directly, and H-19 lets several safe groups apply together. A gap
remained when the largest visible repeated group has conflicting history but a
lower repeated group has exactly one safe suggestion. In that state the user had
to manually select rows or open the category sheet even though the conservative
safe apply path already knew what could be changed.

## Decision

Expose a single safe repeated suggestion in the History bulk card when:

- Selection mode is open.
- The active category filter is `Repeated backlog`.
- Exactly one visible repeated group has a valid H-15 same-merchant history
  suggestion.
- The largest visible repeated group does not have its own direct top-group
  suggestion.

The chip shows the safe category name and transaction count, then reuses the
existing H-19 `applySafeRepeatedBacklogSuggestedCategories()` path. The action
keeps cleanup mode open, clears selected rows, learns the same exact local rule
as other safe repeated applies, and leaves conflicting groups untouched.

## Acceptance

- When the top repeated group conflicts but one lower group is safe, the bulk
  card shows a localized `Apply safe <category> (<count>)` action.
- The action applies only that safe lower group and keeps the conflicting top
  group visible.
- The chip stays hidden outside repeated backlog, when no safe group exists,
  when multiple safe groups exist, and when the top group already has the H-17
  direct top apply action.
- Existing selected-row quick apply, category picker, safe-groups summary, and
  exact-rule-learning behavior stays unchanged.
- English and Arabic strings are localized.

## Non-goals

- Do not auto-apply any safe suggestion.
- Do not loosen H-15 suggestion safety rules.
- Do not add a confirmation dialog.
- Do not apply conflicting repeated groups.

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
  to `build/qa/history-next-safe-group-apply-emulator/`; cleanup finished with
  no emulator/qemu/netsim process and no AVD lock files.
