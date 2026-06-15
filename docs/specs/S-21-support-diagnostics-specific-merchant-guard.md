# S-21 - Support Diagnostics Specific-Merchant Guard

## Problem

Support diagnostics now recommend a cleanup path from category backlog metrics.
That recommendation depends on whether uncategorized rows are concentrated in
actionable repeated merchant groups.

Generic labels such as `payment`, `cash`, `bank`, `كاش`, or `دفع` can repeat
often, but they are not safe same-merchant cleanup targets. If support
diagnostics count them as repeated groups, the report can incorrectly recommend
History repeated-backlog cleanup when the user actually needs row-by-row manual
review or bulk CSV categorization.

## Decision

Use the shared `core:domain` specific-merchant guard when building
`uncategorized_merchant_groups`.

- Category backlog totals still count every eligible uncategorized non-transfer
  row.
- Hashed repeated merchant groups include only specific merchant keys.
- Generic merchant labels stay in `other_backlog_transaction_count`.
- `category_backlog_recommendation` uses only those specific repeated groups
  when deciding whether to recommend `history_repeated_backlog`.

The report remains fully redacted: no raw merchant names, amounts, notes, SMS
bodies, senders, or transaction rows are serialized.

## Acceptance

- Repeated specific merchants still produce hashed `uncategorized_merchant_groups`.
- Repeated generic English/Arabic labels do not produce hashed groups.
- Generic-only repeated backlog does not recommend History repeated-backlog
  cleanup.
- Backlog totals and coverage still include generic rows so support can see the
  full amount of remaining cleanup.

## Non-goals

- Do not hide generic rows from the app or from aggregate backlog totals.
- Do not expose raw merchant samples to support diagnostics.
- Do not auto-clean or import categories from the diagnostics file.
- Do not change History or bulk-categorization UI behavior in this slice.

## Validation

- `:core:data:testDebugUnitTest --tests com.athar.core.data.support.SupportDiagnosticsExporterTest`
- Full JVM test, both debug APK builds, and both lint variants with JDK 17.
- `git diff --check`
- Runtime `personalFullSmsDebug` install/launch was attempted on the local AVDs,
  but the x86 AVD exits because hardware acceleration is required and the
  Android Emulator hypervisor driver is not installed; the ARM AVD is unsupported
  on this x86_64 host.
