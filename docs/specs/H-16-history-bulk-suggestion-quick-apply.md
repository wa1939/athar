# H-16 - History Bulk Suggestion Quick Apply

## Problem

H-15 preselects an unambiguous same-merchant category in the category sheet, but
the fastest repeated-backlog path still requires opening the sheet and tapping
Apply. Once the suggestion has already passed the conservative H-15 checks, the
bulk selection card can offer a direct explicit action without guessing.

## Decision

Show an `Apply <category>` chip in the History bulk selection card when the
current selection has a valid suggested category.

- The chip appears only when normal bulk category application is valid.
- The suggested category must still exist in the current active compatible
  category list.
- The chip uses the localized category label.
- Tapping it calls the existing type-safe bulk category application path.
- Exact-rule learning, transfer skipping, mixed-type blocking, and bulk result
  toasts stay unchanged.
- The generic `Category` chip remains available for review or override through
  the category sheet.

## Acceptance

- A selected repeated merchant group with one unambiguous active compatible
  history category can be applied from the bulk card in one explicit tap.
- The quick action is absent for selections without a valid suggestion.
- The quick action is absent when bulk apply itself is invalid.
- Users can still open the category picker and choose a different category.
- Existing conservative suggestion rules from H-15 remain unchanged.

## Non-goals

- Do not auto-apply a category.
- Do not broaden category suggestions beyond H-15.
- Do not change exact-rule learning criteria.
- Do not add a new transaction update path.

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
  written to `build/qa/history-bulk-suggestion-quick-apply-emulator/`; cleanup
  left no new emulator/qemu/netsim process and no AVD lock files.
