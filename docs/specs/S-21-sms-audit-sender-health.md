# S-21 SMS Audit Sender Health

## Goal

Make the SMS audit log useful when a new user's inbox behaves badly. The user, tester, or maintainer should be able to see which sender labels are failing, which are being ignored as noise, and whether the parser is broadly healthy after a backfill.

## Inputs

- Existing `SmsAuditEntry` rows from the local audit repository.
- Parse status: `PARSED`, `FAILED`, or `IGNORED`.
- Sender label exactly as Android received it.

## Behavior

The SMS audit screen shows the existing parsed, failed, and ignored counts plus a successful parse-rate percentage. It also shows a compact "Sender health" card sorted by highest failed count, then ignored count, then total volume. Each row shows the sender and counts for parsed, failed, and ignored messages.

The visible audit list remains bounded to 200 rows for UI performance, but each
status filter is derived from the full audit history before applying that cap.
After a large backfill, Failed or Ignored rows must still be reachable even when
the most recent 200 All rows are mostly Parsed.

This is diagnostic only. It does not upload SMS data, mutate parsing rules, or change ingestion behavior.

## Acceptance

- Counts and parse rate are derived from the full audit list, not just the visible 200 rows.
- The sender-health card lists at most five senders.
- Failed senders rank above ignored-only senders.
- The SMS log still supports the existing All / Parsed / Failed / Ignored filters.
- Each filter's visible rows are selected from the full audit list before the
  200-row display cap is applied.

## Validation

- 2026-06-13: `:feature:settings:testDebugUnitTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: Full gate passed with `test`,
  `:app:assemblePersonalFullSmsDebug`, `:app:assembleStoreSafeDebug`,
  `:app:lintPersonalFullSmsDebug`, and `:app:lintStoreSafeDebug`.
- 2026-06-13: Runtime tap-through could not complete locally because
  `adb devices -l` returned no attached devices and `emulator -accel-check`
  reported that the Android Emulator hypervisor driver is not installed.
