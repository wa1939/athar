# H-17 - History Top Group Suggestion Apply

## Problem

H-16 lets users apply a safe category suggestion after selecting repeated rows.
In the `Repeated backlog` view, the largest group is already identified by the
app. When that top group also has one conservative local-history suggestion, the
user should not need to select the group first just to apply the same explicit
decision.

## Decision

Add an explicit `Apply top <category> (<count>)` action to the History bulk card
when the largest repeated-backlog group has a valid suggestion.

- The action appears only while the History selection card is open.
- It appears only in `Repeated backlog` mode because that is where the top group
  is defined.
- The top group uses the existing largest-visible-group ordering from H-12/H-13.
- The category suggestion uses the same conservative H-15 logic: full local
  same-merchant history must have exactly one active compatible category.
- Tapping the action applies that category to the top group through the existing
  type-safe bulk apply helper.
- Exact-rule learning, transfer skipping, mixed-type blocking, and result toasts
  stay unchanged.
- The existing `Top group`, `Apply <category>`, and `Category` actions remain
  available for selection, review, or override.

## Acceptance

- The largest repeated-backlog group can be categorized with one explicit top
  suggestion action when its suggestion is valid.
- The action is hidden outside `Repeated backlog` mode.
- The action is hidden when the top group's history has conflicting categories.
- Applying the top suggestion reuses the existing bulk category update path.
- Existing selected-row quick apply from H-16 still works independently.

## Non-goals

- Do not auto-apply categories.
- Do not suggest for the second-largest or arbitrary groups.
- Do not broaden H-15 suggestion criteria.
- Do not change repeated-backlog sorting or grouping.

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
  Android Emulator hypervisor driver is not installed, and the bounded hidden
  `AtharPixelQaApi35` launch through
  `C:\Users\waok\Android\Sdk\emulator\emulator.exe` exited before exposing an
  online ADB device or `boot_completed=1` with exit code `-1073741819`.
  Install, screenshot, UI dump, and logcat capture could not run. Evidence was
  written to `build/qa/history-top-group-suggestion-apply-emulator/`; cleanup
  left no new emulator/qemu/netsim process and no AVD lock files.
