# R-07 - Bulk Categorize Audit Raw Context

## Problem

The bulk-categorize workflow asks the user or an external AI to classify
uncategorized transactions from a CSV. The full-context export includes a
`raw_body` column, but SMS and notification transactions normally keep the
original message body in the ingestion audit table, not in `Transaction.notes`.
That makes the highest-context export weaker exactly where Athar needs help:
private/local bank-message backlogs whose parsed merchant can be abbreviated or
ambiguous.

## Decision

- For `FULL_CONTEXT` bulk exports, populate `raw_body` from the linked
  `SmsAuditEntry.body` when an audit row has `parsedTransactionId` equal to the
  exported transaction id.
- Keep `Transaction.notes` as the fallback for imported/manual statement rows
  or older transactions without an audit link.
- Keep `NO_RAW_BODY` exports unchanged: `raw_body` remains blank even when a
  linked audit body exists.
- Do not copy raw SMS bodies into transaction notes or seed-rule files.

## Acceptance

- A parsed SMS/notification transaction with no notes exports the original audit
  body in full-context mode.
- A transaction without an audit link still exports notes in full-context mode.
- Private/no-raw-body export omits both notes and linked audit bodies.
- Existing stable-key, category-options, repeated-group, and import behavior is
  unchanged.

## Non-goals

- Do not change the CSV schema.
- Do not add network AI APIs, API keys, telemetry, or cloud categorization.
- Do not store raw bodies in public docs, public seed rules, or community-rule
  exports.
- Do not change SMS audit retention or database schema.

## Validation

- `:core:data:testDebugUnitTest` covers audit-backed full-context export,
  fallback behavior, and private export redaction.
- Forced private parser audit passed with 7,887 records, 3,864 successes, 4,023
  ignored, 0 parser failures, 0 failed known-bank messages, and 0
  missing-merchant parsed expenses; the audit output remains redacted aggregate
  JSON only.
- The full JVM test/build/lint stack passed with JDK 21:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime install/launch was attempted with the built `personalFullSmsDebug` APK.
  `emulator -accel-check` reported that the Android Emulator hypervisor driver is
  not installed, and the bounded hidden `AtharPixelQaApi35` software launch
  exited before exposing an online ADB device with Windows access-violation code
  `-1073741819`; cleanup ended with no attached ADB device, no
  emulator/qemu/netsim process, and no AVD locks.
