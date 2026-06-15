# R-08 - Widget Preference Refresh

## Problem

Athar widgets refresh after transaction mutations and on the launcher cadence,
but two user preferences directly change widget output without changing any
transaction:

- `appLocale` controls the localized Month, Today, and Pending widget body copy.
- `displayCurrency` controls Month and Today monetary totals.

Without an app-requested refresh, changing either preference in Settings can
leave existing home-screen widgets showing the old language or currency until a
later system refresh or ledger write.

## Decision

Request a debounced widget refresh from `UserPreferencesRepositoryImpl` after
persisting a real `appLocale` or `displayCurrency` change. The repository
already lives in `core:data`, and `core:data` already depends on the domain
`WidgetRefresher` abstraction for transaction writes, so this keeps Glance
details inside `feature:widgets`.

No-op preference writes do not refresh widgets.

## Acceptance

- Changing Athar's app language requests a widget refresh.
- Changing Athar's display currency requests a widget refresh.
- Re-saving the same app language or display currency does not request a
  redundant widget refresh.
- Widget refresh remains debounced by `GlanceWidgetRefresher`.
- Widget data loading, actions, and launcher behavior are unchanged.

## Non-goals

- Do not add new widget content.
- Do not refresh widgets for preferences they do not read.
- Do not change transaction mutation refresh behavior.
- Do not bypass the existing `WidgetRefresher` abstraction.

## Validation

- Focused compile/Hilt validation passed:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :core:data:compileDebugKotlin :app:compilePersonalFullSmsDebugKotlin`.
- `git diff --check` passed.
- Full validation passed:
  `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime install/launch was attempted with the Android emulator QA workflow. The
  built `personalFullSmsDebug` APK was ready, but no online ADB target appeared:
  `emulator -accel-check` reported the Android Emulator hypervisor driver is not
  installed, `AtharPixelQaApi35` exited with code `1` because x86_64 emulation
  requires hardware acceleration, and `AtharPixelQaApi35Arm` exited with code
  `1` because arm64 system images are unsupported on this x86_64 host. Evidence
  is in `build/qa/widget-preference-refresh-emulator/`.
