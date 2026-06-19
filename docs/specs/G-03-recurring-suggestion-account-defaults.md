# G-03 - Recurring Suggestion Account Defaults

## Problem

Recurring suggestions are detected from confirmed transaction history, but
accepting a suggestion still creates the recurring rule on the manual cash
account. For multi-account users, a repeated subscription, salary, or loan
payment that has always arrived on one routed bank account should not produce
future recurring rows in the wrong account. That makes net worth less accurate
and adds avoidable cleanup.

## Decision

Carry a safe account hint on `RecurringSuggestion` only when every detected
occurrence in the merchant+amount+currency group used the same non-blank
`accountId`.

- Use that hint as the ViewModel fallback when accepting a suggestion.
- Preserve the existing manual-account fallback when history is mixed or
  incomplete.
- Keep category defaults, specific-merchant guarding, and recurring detection
  thresholds unchanged.

## Acceptance

- A repeated `Gym Club` expense whose detected occurrences all came from
  `acc-checking` emits a suggestion with `suggestedAccountId = acc-checking`.
- A repeated merchant whose detected occurrences span multiple accounts emits a
  suggestion with no account hint.
- Accepting a suggestion with a safe account hint creates the recurring rule on
  that account when the user does not supply another account path.
- Accepting a suggestion without a safe account hint still creates the rule on
  `MANUAL_ACCOUNT_ID`.

## Non-goals

- Do not add an account picker to the recurring confirmation sheet in this
  slice.
- Do not infer an account from merchant text, sender aliases, or amount.
- Do not change existing manual recurring rule creation.
- Do not change recurring materialization behavior beyond using the rule's
  existing `accountId`.

## Validation

- `.\gradlew --console=plain --no-daemon --max-workers=1 :core:data:testDebugUnitTest --tests "com.athar.core.data.repo.RecurringSuggestionRepositoryImplTest" :feature:settings:testDebugUnitTest --tests "com.athar.feature.settings.RecurringRulesViewModelTest"`
- `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime `personalFullSmsDebug` install/launch was attempted. The built APK
  was ready, `adb devices -l` initially listed no connected devices,
  `emulator -list-avds` found `AtharPixelQaApi35` and `AtharPixelQaApi35Arm`,
  and `emulator -accel-check` reported that the Android Emulator hypervisor
  driver is not installed. The first bounded hidden `AtharPixelQaApi35` launch
  exposed no ADB transport during the wait window, so
  `:app:installPersonalFullSmsDebug` failed with `No connected devices`. A
  short retry started the emulator process but ADB saw only
  `emulator-5554 offline`; launch, screenshot, and logcat capture were skipped
  because no online device was available. Cleanup stopped emulator/qemu/netsim
  child processes, restarted ADB, and ended with no attached devices. Evidence
  is under `build/qa/recurring-suggestion-account-defaults-emulator/`.
