# G-03 - Recurring Rule Routing Editor

## Problem

Users can now choose the account when creating a recurring rule, and accepted
suggestions can inherit safe account/category defaults. Existing rules still
needed delete-and-recreate cleanup when their account or category was wrong.
That is especially painful because recurring materialization copies the rule's
stored `accountId` and `categoryId` into every future pending transaction.

## Decision

Add a narrow routing editor to the recurring rules list:

- Each rule row shows its current account and category.
- The row exposes an account/category action that opens a bottom sheet.
- The sheet lets the user choose an active account and a type-compatible active
  category, or clear the category.
- Saving updates only `accountId`, `categoryId`, and `updatedAt` on the
  existing rule.

## Acceptance

- A user can inspect a recurring rule row and see which account/category future
  materialized rows will use.
- A user can move an existing recurring rule from `MANUAL_ACCOUNT_ID` to an
  active checking/savings/credit account without deleting the rule.
- A user can change or clear the rule category without changing amount, merchant,
  cadence, next run, active state, or notes.
- Future materialized transactions keep using the rule's stored routing fields.

## Non-goals

- Do not edit amount, cadence, day-of-month, merchant, or schedule dates.
- Do not infer account/category from merchant text.
- Do not broaden recurring suggestion detection.
- Do not auto-confirm future recurring transactions.

## Validation

- `.\gradlew --console=plain --no-daemon --max-workers=1 :feature:settings:testDebugUnitTest --tests "com.athar.feature.settings.RecurringRulesViewModelTest" :feature:settings:compileDebugKotlin`
- `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- `git diff --check`
- Runtime install was attempted on 2026-06-15 with the built
  `personalFullSmsDebug` APK. `emulator -accel-check` reported that the
  Android Emulator hypervisor driver is not installed, the bounded
  `AtharPixelQaApi35` launch exposed only `emulator-5554 offline`, and
  `:app:installPersonalFullSmsDebug` failed with `No online devices found`.
  Evidence is in `build/qa/recurring-rule-routing-editor-emulator/`.
