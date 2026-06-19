# G-03 - Recurring Suggestion Category Defaults

## Problem

Recurring suggestions are detected from confirmed transaction history. The
confirm-before-create sheet already lets the user choose cadence, day, and
category, but it opens with no category selected even when every detected
occurrence already has the same confirmed category. If the user accepts the
suggestion without re-picking that category, the new recurring rule materializes
future rows as uncategorized pending transactions, adding avoidable cleanup.

## Decision

Carry a safe category hint on `RecurringSuggestion` only when every occurrence
in the detected merchant+amount+currency group has the same non-blank
`categoryId`.

- Preselect that category in the recurring suggestion confirm sheet when it is
  still active and valid for the suggestion type.
- Use the same hint as the ViewModel fallback when accepting a suggestion.
- Do not guess from partial history or conflicting categories.
- Keep the specific-merchant guard and existing recurring suggestion detection
  thresholds unchanged.

## Acceptance

- A repeated `Gym Club` expense whose three detected occurrences are all
  categorized as `cat-gym` emits a suggestion with `suggestedCategoryId =
  cat-gym`.
- A repeated merchant with conflicting occurrence categories emits a suggestion
  with no category hint.
- A repeated merchant with incomplete occurrence categories emits a suggestion
  with no category hint.
- The confirmation sheet preselects only active category hints that match the
  suggestion's expense/income kind.
- Accepting a suggestion without changing category preserves the safe hint on
  the created recurring rule.

## Non-goals

- Do not infer a category from merchant text, seed rules, or partial history.
- Do not auto-create recurring rules.
- Do not change recurring materialization behavior or bill reminders.
- Do not change SMS, notification, import, or manual-entry parsing.

## Validation

- `.\gradlew --console=plain --no-daemon --max-workers=1 :core:data:testDebugUnitTest --tests "com.athar.core.data.repo.RecurringSuggestionRepositoryImplTest" :feature:settings:testDebugUnitTest --tests "com.athar.feature.settings.RecurringRulesViewModelTest"`
- `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime `personalFullSmsDebug` install/launch was attempted. The built APK
  was ready, `adb devices -l` initially listed no connected devices,
  `emulator -list-avds` found `AtharPixelQaApi35` and `AtharPixelQaApi35Arm`,
  and `emulator -accel-check` reported that the Android Emulator hypervisor
  driver is not installed. A bounded hidden `AtharPixelQaApi35` launch exposed
  only `emulator-5554 offline` for the full wait window, so
  `:app:installPersonalFullSmsDebug` skipped the offline device and failed with
  `No online devices found`. Cleanup stopped emulator/qemu/netsim processes,
  restarted ADB, and ended with no attached devices. Evidence is under
  `build/qa/recurring-suggestion-category-defaults-emulator/`.
