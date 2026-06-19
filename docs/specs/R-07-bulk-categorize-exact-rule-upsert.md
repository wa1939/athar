# R-07 - Bulk Categorize Exact Rule Upsert

## Problem

Bulk categorization import trains exact local merchant rules so future SMS or
notification rows for the same normalized merchant can auto-categorize without a
cloud service. The current correction API creates a fresh rule id every time.
That means re-importing the same filled CSV can add duplicate exact rules, and a
later corrected CSV can leave two priority-200 exact rules for one merchant with
different categories. Future categorization then depends on rule ordering rather
than the user's latest decision.

## Decision

- Before training an exact rule from bulk import, inspect existing local category
  rules for the same normalized merchant and `PatternType.EXACT`.
- If exactly one existing user-learned exact rule already points to the imported
  category, do not add another rule.
- If existing user-learned exact rules for that merchant are duplicated or point
  to a different category, delete those exact local rules and train one fresh
  exact rule for the imported category.
- Do not delete seed/system exact rules, auto-local history rules, or explicit
  substring rules from the "Always categorize" flow.

## Acceptance

- Re-importing a CSV for a merchant that already has the same exact local rule
  updates transactions but reports `rulesAdded = 0`.
- Importing a corrected category for a merchant with an older exact local rule
  removes the stale exact rule and adds one replacement rule.
- Seed rules and explicit substring rules for the same merchant stay intact.
- Conflicting or mixed-type merchant groups still train no exact rule.
- Community-rule export remains unchanged: exact local rules stay private.

## Non-goals

- Do not change public seed matching or priorities.
- Do not change the CSV schema.
- Do not make substring "Always categorize" rules idempotent in this slice.
- Do not add backend, telemetry, or automatic community sharing.

## Validation

- `:core:data:testDebugUnitTest` passed with JDK 21.
- Forced gated private SMS parser audit passed with redacted aggregate output
  only: 7,887 records, 3,864 parser successes, 4,023 ignored, 0 parser
  failures, 0 failed known-bank messages, and 0 missing-merchant parsed
  expenses.
- Full JVM test/build/lint stack passed with JDK 21:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime `personalFullSmsDebug` install was attempted against
  `AtharPixelQaApi35`, but the emulator could not expose an online ADB device.
  The built APK was present at
  `app/build/outputs/apk/personalFullSms/debug/app-personalFullSms-debug.apk`.
  A bounded `-accel off` software boot stayed `emulator-5554 offline`, and a
  follow-up automatic-acceleration launch exited with
  `x86_64 emulation currently requires hardware acceleration` because the
  Android Emulator hypervisor driver is not installed. Evidence was written to
  `build/qa/bulk-categorize-exact-rule-upsert-emulator/`, and cleanup finished
  with no attached ADB device, no emulator/qemu/netsim process, and no AVD lock
  files.
