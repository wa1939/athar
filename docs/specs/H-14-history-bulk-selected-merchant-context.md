# H-14 - History Bulk Selected Merchant Context

## Problem

H-13 makes it fast to select a repeated merchant group, but the category picker
still opened with only a generic `Choose category` title. That left room for a
mistake: the user could forget which merchant group was selected before applying
one category to multiple rows.

## Decision

Show selected-merchant context in the History bulk selection card and category
sheet when the selected visible rows share one specific merchant.

- Context is shown only when all selected visible rows share one specific
  normalized merchant key.
- The displayed merchant name uses the first visible selected row's merchant
  label, falling back to its normalized merchant.
- Mixed-merchant selections and generic keys such as `unknown` do not show a
  merchant context line.
- The context appears before category application, but does not change selection,
  eligibility, category options, exact-rule learning, or row filtering.

## Acceptance

- Same-merchant selections show a localized merchant/count line in the bulk card.
- The category sheet repeats the same merchant/count context before the category
  picker.
- Mixed selections do not show a misleading merchant context.
- Generic merchant selections do not show context.
- Existing top-group, same-merchant, select-shown, and type-safe category
  assignment behavior remains unchanged.

## Non-goals

- Do not infer merchant identity from raw private SMS bodies.
- Do not add a merchant detail screen or persistent group model.
- Do not auto-apply a category or learn a rule without explicit user
  confirmation.

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
  built APK. The local AVD exists and ADB starts, but `emulator -accel-check`
  reports the Android Emulator hypervisor driver is not installed, and the
  bounded hidden `AtharPixelQaApi35` software launch exited before exposing an
  online ADB device or `boot_completed=1` with exit code `-1073741819`.
  Install, screenshot, UI dump, and logcat capture could not run. Evidence was
  written to `build/qa/history-bulk-selected-merchant-context-emulator/`; final
  cleanup left no emulator/qemu/adb/netsim process and no AVD lock files.
