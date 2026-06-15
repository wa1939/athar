# H-25 - Today Repeated Backlog Nudge

## Problem

The private audit and support diagnostics now point repeated uncategorized
merchants toward the History repeated-backlog workflow. History has the cleanup
tools: repeated group filtering, largest-group selection, safe same-merchant
suggestions, multi-group apply, and exact local rule learning.

The remaining UX gap is discoverability from the primary surface. Today shows
pending and dismissed attention banners, but it does not tell the user when the
best next cleanup action is repeated uncategorized groups. A user can have a
large private/local backlog and never know that History already has a focused
cleanup mode.

## Decision

Add a quiet Today nudge when current local transactions contain repeated
uncategorized non-transfer merchant groups. The nudge shows aggregate counts
only:

- total repeated uncategorized rows,
- repeated group count,
- largest group size.

Tapping it opens History directly on the `Repeated backlog` category filter and
enters selection mode so the existing top-group and safe-suggestion chips are
visible immediately.

The nudge reuses the same repeated-backlog definition as History: uncategorized
expense/income rows, specific merchant keys only, and groups of at least two.

## Acceptance

- Today state exposes a repeated-backlog nudge only when repeated specific
  uncategorized groups exist.
- Generic labels such as `payment`, `bank`, `cash`, `كاش`, and `دفع` do not
  trigger the nudge.
- The nudge count matches the same rows History shows under `Repeated backlog`.
- Tapping the nudge opens History with `Repeated backlog` already selected and
  selection mode active.
- English and Arabic strings are localized.

## Non-goals

- Do not auto-categorize anything from Today.
- Do not change History bulk-apply safety rules.
- Do not add another public seed rule batch.
- Do not expose raw private merchant names in the Today nudge.

## Validation

- Focused Today/History unit tests passed:
  `.\gradlew --console=plain --no-daemon --max-workers=1 :feature:today:testDebugUnitTest --tests "com.athar.feature.today.TodayViewModelTest" --tests "com.athar.feature.today.HistoryFilterTest"`.
- `git diff --check` passed.
- Full validation passed:
  `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime install/launch was attempted with the Android emulator QA workflow. The
  built `personalFullSmsDebug` APK was ready, but no online ADB target appeared:
  `emulator -accel-check` reported the Android Emulator hypervisor driver is not
  installed, `AtharPixelQaApi35` exited with code `-1073741819`, and
  `AtharPixelQaApi35Arm` exited because arm64 system images are unsupported on
  this x86_64 host. Evidence is in
  `build/qa/today-repeated-backlog-nudge-emulator/`.
