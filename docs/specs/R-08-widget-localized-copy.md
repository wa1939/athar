# R-08 - Widget Localized Copy

## Problem

The Glance widgets live outside the normal Activity locale wrapper, so their
content was partly hardcoded in English or bilingual text even when Athar's
saved app language was Arabic or English. Launcher labels and descriptions were
already localized through resources, but Month, Today, and Pending widget body
copy did not consistently resolve from the user's app-locale preference.

## Decision

Carry the saved `appLocale` value in every widget snapshot and create a
locale-specific `Context` for widget string resolution. The widget data still
comes from the same repositories, and the original application `Context` is
kept for launching Athar and running widget actions.

Replace hardcoded widget labels with `feature:widgets` string resources:

- Month title and Income / Expense / Net worth labels.
- Today title and pending count.
- Pending count, empty state, overflow count, and Confirm / Dismiss /
  Categorize actions.

## Acceptance

- Month, Today, and Pending widgets resolve body copy from Arabic and English
  resources.
- A saved app-language override is honored by widget content, not only Activity
  screens.
- Follow-system behavior still uses the launcher/system `Context`.
- Pending widget Confirm, Dismiss, Categorize, and app-launch behavior stays
  unchanged.
- No repository, schema, or widget refresh behavior changes are introduced.

## Non-goals

- Do not redesign the widget layouts.
- Do not add a new locale picker or preference.
- Do not change widget data freshness, action worker behavior, or financial
  calculations.

## Validation

- Focused widget compilation passed:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :feature:widgets:compileDebugKotlin`.
- `git diff --check` passed.
- Full validation passed:
  `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime install/launch was attempted with the Android emulator QA workflow. The
  built `personalFullSmsDebug` APK was ready, but no online ADB target appeared:
  `emulator -accel-check` reported the Android Emulator hypervisor driver is not
  installed, `AtharPixelQaApi35` exited with code `1` because x86_64 emulation
  requires hardware acceleration, and `AtharPixelQaApi35Arm` exited with code
  `1` because arm64 system images are unsupported on this x86_64 host. Evidence
  is in `build/qa/widgets-localized-copy-emulator/`.
