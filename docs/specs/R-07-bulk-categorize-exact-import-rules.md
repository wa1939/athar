# R-07 - Bulk Categorize Exact Import Rules

## Problem

Bulk categorization import can come from a private SMS export or a local merchant
backlog. Training broad substring rules from that workflow is convenient, but it
can over-match future merchants and can make private/local labels eligible for
community-rule export.

The "Always categorize X" flow is the right place for a user-authored substring
rule. Bulk import should be safer by default.

## Decision

- Bulk categorization import now trains `PatternType.EXACT` rules for each
  unambiguous imported merchant/category group.
- The rule remains priority 200 so it beats shared seed rules for that exact
  normalized merchant.
- Community-rule export now includes only explicit `learnedFromUser=true`
  substring rules. Exact rules created by bulk import stay local.

## Acceptance

- Imported CSV rules are learned as `PatternType.EXACT`.
- Group-propagated rows still update and train one exact rule when the group is
  unambiguous.
- Conflicting groups still update only explicit rows and train no rule.
- Community export includes explicit substring rules and omits exact local rules.
- If only exact local rules exist, community export returns empty.

## Non-goals

- Do not change the explicit "Always categorize X" substring rule behavior.
- Do not change public seed rule matching or priorities.
- Do not add a backend, telemetry, or automatic community submission.

## Validation

- `:core:data:testDebugUnitTest` covers exact import rule creation and community
  export omission of exact local rules.
- The full JVM test/build/lint stack passes with JDK 21:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime install/launch was attempted with the built `personalFullSmsDebug` APK.
  `AtharPixelQaApi35` briefly exposed only `emulator-5554 offline`, then exited
  before install with Windows access-violation code `-1073741819`; cleanup
  finished with no attached ADB device, no emulator/qemu/netsim process, and no
  AVD locks.
