# G-03 - Recurring Rule Account Picker

## Problem

Recurring suggestions can now carry a safe account default when every detected
occurrence used the same account, but the confirmation UI still gives users no
way to review or override that account. Manual recurring rules also still land
on the manual cash account. Multi-account users then have to clean up future
salary, subscription, loan, or bill rows after materialization.

## Decision

Expose active account selection in the recurring-rule creation paths:

- Manual rule creation loads active accounts and lets the user choose the
  account before saving.
- The suggestion confirmation sheet preselects the safe suggested account only
  when that account is still active.
- User-selected account wins over the safe suggestion default.
- If no account is selected or available, the rule still falls back to
  `MANUAL_ACCOUNT_ID`.

## Acceptance

- Creating a manual recurring rule with `acc-rent` selected stores
  `accountId = acc-rent`.
- Creating a manual recurring rule without a selected account keeps the existing
  manual-account fallback.
- Accepting a suggestion with `acc-credit` selected stores
  `accountId = acc-credit` even when the suggestion had a different safe
  account hint.
- Accepting a suggestion without an explicit account still uses the safe
  suggested account when present.
- Accepting a suggestion with no account hint still stores `MANUAL_ACCOUNT_ID`.

## Non-goals

- Do not infer accounts from merchant text or amount.
- Do not add account editing to existing recurring-rule rows.
- Do not change recurring materialization behavior beyond using the rule's
  stored `accountId`.
- Do not broaden recurring suggestion detection.

## Validation

- `.\gradlew --console=plain --no-daemon --max-workers=1 :feature:settings:testDebugUnitTest --tests "com.athar.feature.settings.RecurringRulesViewModelTest" :feature:settings:compileDebugKotlin`
- `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime `personalFullSmsDebug` install/launch was attempted. The built APK
  was ready, `emulator -list-avds` found `AtharPixelQaApi35` and
  `AtharPixelQaApi35Arm`, and `emulator -accel-check` reported that the Android
  Emulator hypervisor driver is not installed. The bounded hidden
  `AtharPixelQaApi35` launch exposed only `emulator-5554 offline`, so
  `:app:installPersonalFullSmsDebug` failed with `No online devices found`.
  Cleanup stopped emulator/qemu children, restarted ADB, and ended with no
  attached devices. Evidence is under
  `build/qa/recurring-rule-account-picker-emulator/`.
