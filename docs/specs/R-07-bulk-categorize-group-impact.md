# R-07 - Bulk Categorize Group Impact

## Problem

Support diagnostics can now show whether category cleanup is concentrated in a
few repeated merchant groups, but the bulk-categorization CSV only exposed the
raw group count. A long export still made a human or external AI infer which
groups mattered most.

The cleanup path should make the highest-impact groups obvious without changing
the privacy model or asking Athar to guess categories automatically.

## Decision

Add read-only group-impact columns to the bulk-categorization CSV:

- `merchant_group_rank` is the 1-based rank of the repeated merchant group
  after sorting by group size, then merchant key.
- `merchant_group_share_permille` is the group's share of all exported backlog
  rows.
- `merchant_group_cumulative_share_permille` is the cumulative share through
  that group rank.

These columns sit after `merchant_group_count`. They are context for a human or
external AI and must be preserved on import. The importer continues resolving
rows by `id`, `stable_key`, source-aware keys, and content fingerprints, so older
CSV files and CSV files with extra read-only columns remain compatible.

## Acceptance

- Bulk-categorization exports include group rank, share, and cumulative share.
- Repeated merchant rows share the same rank/share values.
- The copied AI prompt tells the model to prioritize low-rank, high-share groups
  while preserving all non-`category_id` columns.
- Existing import behavior remains compatible with old and new CSV headers.
- Settings copy and workflow docs describe the group-impact context.

## Non-goals

- Do not add inline AI APIs, network calls, or API-key storage.
- Do not infer categories from group impact.
- Do not change the rule-learning safety gates.

## Validation

- 2026-06-15: Focused `MerchantBulkCsvTest` and
  `MerchantBulkAiPromptTest` passed with JDK 17, `--no-daemon`, and
  `--max-workers=1`.
- 2026-06-15: Full JVM/debug-APK/lint stack passed with JDK 17:
  `test`, `:app:assemblePersonalFullSmsDebug`, `:app:assembleStoreSafeDebug`,
  `:app:lintPersonalFullSmsDebug`, and `:app:lintStoreSafeDebug`.
- 2026-06-15: `git diff --check` passed.
- 2026-06-15: Runtime `personalFullSmsDebug` install was attempted, but the
  local x86_64 AVD exited before exposing an online ADB device. Evidence in
  `build/qa/bulk-categorize-group-impact-emulator/` shows
  `emulator -accel-check` reporting the Android Emulator hypervisor driver is
  not installed, and the bounded hidden `AtharPixelQaApi35` launch exiting with
  code `1` after `x86_64 emulation currently requires hardware acceleration`.
  The stale AVD lock left by the failed launch was removed after confirming no
  emulator/qemu/netsim process was running.
