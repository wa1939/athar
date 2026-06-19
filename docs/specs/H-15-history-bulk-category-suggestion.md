# H-15 - History Bulk Category Suggestion

## Problem

H-14 makes bulk category application safer by showing which merchant is selected,
but the category sheet still starts on the first category every time. If a user
already categorized the same merchant before, the app can reduce taps by using
that local history without applying anything automatically.

## Decision

Preselect one category in the History bulk category sheet only when local
history makes the decision unambiguous.

- The suggestion is available only when selected visible rows share one specific
  normalized merchant key.
- Full local transaction history is used, not just the current filtered rows, so
  repeated-backlog cleanup can benefit from previous categorized rows.
- Only active categories compatible with the selected expense/income kind can be
  suggested.
- A suggestion appears only when matching same-merchant history has exactly one
  active compatible category.
- Conflicting categories, inactive categories, mixed selected merchants, and
  generic merchant keys do not produce a suggestion.
- The sheet shows a localized suggestion line with the category name and history
  count, then preselects that category. The user still must tap Apply.
- Category assignment, transfer skipping, and exact-rule learning behavior stay
  unchanged.

## Acceptance

- Same-merchant bulk selections preselect the one active compatible category
  already used in local history.
- The suggestion uses full local history even when the current visible filter
  only shows uncategorized rows.
- Conflicting same-merchant category history does not guess.
- Inactive or type-incompatible category history does not suggest.
- Mixed or generic merchant selections do not suggest.
- Applying remains explicit and uses the existing type-safe bulk category path.

## Non-goals

- Do not auto-apply categories.
- Do not create new rules before the user confirms Apply.
- Do not infer categories from raw SMS bodies, private merchant names, or cloud
  services.
- Do not relax category/type compatibility.

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
  written to `build/qa/history-bulk-category-suggestion-emulator/`; cleanup left
  no new emulator/qemu/netsim process and no AVD lock files.
