# H-09 - History Exact Rule Learning

## Problem

H-08 makes repeated visible merchants easy to select and categorize, but future
SMS or notification rows for that same normalized merchant could still arrive
uncategorized unless the broader repeated-history learner eventually collected
enough evidence. The private audit shows 0 parser failures and 0 missing
merchant expenses, but 1,087 uncategorized parsed expenses, with repeated
merchant groups driving a meaningful share of the backlog.

## Decision

When History bulk category assignment successfully applies one compatible
category to two or more selected rows that all share the same specific normalized
merchant, create one exact local category rule for that merchant and category.

The rule is deliberately narrow:

- It uses `PatternType.EXACT`, not substring or regex.
- It only learns after compatible selected rows are applied.
- It does not learn from single-row, mixed-merchant, generic-merchant, transfer,
  or mixed-type selections.
- It does not override an existing explicit non-exact user rule.
- It does not export through community-rule sharing, which only shares explicit
  substring rules.

The bulk-result toast reports when future same-merchant rows will
auto-categorize.

## Acceptance

- Bulk-applying a category to repeated selected rows for one normalized merchant
  creates one exact local rule.
- Bulk-applying a category to mixed selected merchants does not create a rule.
- The existing category picker, transfer skip behavior, and selected-row updates
  remain unchanged.
- Users get visible feedback when exact local learning happened.

## Non-goals

- Do not train broad substring rules from History bulk assignment.
- Do not share exact local rules through community-rule export.
- Do not infer categories for mixed merchant groups.

## Validation

- 2026-06-15: Focused `HistoryFilterTest` and `HistoryViewModelTest` passed with
  JDK 17, `--no-daemon`, and `--max-workers=1`.
- 2026-06-15: Full `:feature:today:testDebugUnitTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-15: `git diff --check` passed.
- 2026-06-15: Full JVM/debug-APK/lint stack passed with JDK 17:
  `test`, `:app:assemblePersonalFullSmsDebug`, `:app:assembleStoreSafeDebug`,
  `:app:lintPersonalFullSmsDebug`, and `:app:lintStoreSafeDebug`.
- 2026-06-15: Runtime `personalFullSmsDebug` install was attempted with the
  built APK. The local AVD exists, but `emulator -accel-check` reports the
  Android Emulator hypervisor driver is not installed, and a bounded hidden
  `AtharPixelQaApi35` software launch did not reach an online ADB device or
  `boot_completed=1`. Install, screenshot, UI dump, and logcat capture could not
  run. Evidence was written to
  `build/qa/history-exact-rule-learning-emulator/`; cleanup finished with no
  emulator/qemu/adb/netsim process and no AVD lock files.
