# S-21 - Support Diagnostics Category Coverage

## Problem

S-21 support diagnostics already include category-backlog counts and top hashed
uncategorized merchant groups, but support still cannot tell whether cleanup is
concentrated in a few repeated merchants or scattered across many one-off rows.

That distinction changes the recommendation: concentrated backlog points users
toward repeated-history cleanup or bulk categorization, while scattered backlog
points toward parser/seed coverage or normal manual review.

## Decision

Add redacted category-coverage metrics to `athar-support-diagnostics.json`:

- `category_coverage` summarizes non-transfer category-eligible rows,
  categorized rows, backlog rows, categorized/backlog coverage permille,
  top-group coverage, remaining rows outside top groups, and largest-group
  coverage.
- Each `uncategorized_merchant_groups` entry includes a hashed specific merchant
  key plus its share of the category backlog and cumulative share, as permille
  integers. Generic merchant labels remain counted in backlog totals but stay
  outside the repeated-group list.

The report continues to omit raw merchant names, raw transaction rows, notes,
amounts, balances, senders, SMS bodies, card numbers, and account numbers.

## Acceptance

- Support diagnostics include `category_coverage`.
- Top hashed uncategorized merchant groups include per-group and cumulative
  backlog share.
- Generic merchant labels do not appear as top repeated groups.
- Transfer rows remain excluded from category-eligible and backlog coverage.
- Unit tests prove raw merchant names, amount values, and private notes are not
  serialized.
- Existing parser diagnostics and category-backlog groups remain present.

## Non-goals

- Do not export raw merchant names or transaction rows.
- Do not infer categories from diagnostics.
- Do not change the bulk-categorize CSV workflow.

## Validation

- 2026-06-15: Focused `SupportDiagnosticsExporterTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-15: Full `:core:data:testDebugUnitTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-15: Full JVM/debug-APK/lint stack passed with JDK 17:
  `test`, `:app:assemblePersonalFullSmsDebug`, `:app:assembleStoreSafeDebug`,
  `:app:lintPersonalFullSmsDebug`, and `:app:lintStoreSafeDebug`.
- 2026-06-15: `git diff --check` passed.
- 2026-06-15: Runtime `personalFullSmsDebug` install was attempted, but the
  local x86_64 AVD exited before exposing an online ADB device. Evidence in
  `build/qa/support-diagnostics-category-coverage-emulator/` shows
  `emulator -accel-check` reporting the Android Emulator hypervisor driver is
  not installed, and the bounded hidden `AtharPixelQaApi35` retry exiting with
  code `1` after `x86_64 emulation currently requires hardware acceleration`.
