# G-11 - Notification Package Coverage Follow-Up

## Problem

The generic notification parser already handles many transaction phrases, but
store-safe users only benefit when the notification sender package is allowed to
enter that parser. The previous allow-list covered Saudi apps plus several US,
UK, EU, and wallet apps, but common MENA, Australian, Indian, and Southeast Asian
finance apps were still ignored before phrase parsing.

This creates avoidable manual entry for users outside the original SMS-first
Saudi path even when the notification copy is already parseable.

## Decision

Extend both known-finance package allow-lists with conservative package
identifiers for:

- MENA banks: Emirates NBD, ADCB, Mashreq, FAB, QNB, Boubyan, KFH, Bank Muscat.
- US cards/banks: Discover, Truist, Citizens Bank.
- Australia: CommBank, Westpac, NAB, ANZ.
- India and Southeast Asia: DBS, OCBC, UOB, Maybank, CIMB, HDFC, ICICI, Axis,
  Kotak.

No new parsing grammar is added. Accepted packages still need the existing
amount, action, and merchant/counterparty checks, and all existing ignores for
security, marketing, statements, limits, declined transactions, requests, and
scheduled payments stay ahead of amount extraction.

This slice covers both gates in the store-safe path:

- `BankNotificationPackageMatcher`, which decides whether Android notification
  events are enqueued at all.
- `GenericBankNotificationTemplate`, which decides whether a notification-shaped
  raw event can enter the transaction parser.

## Acceptance

- Notifications from representative MENA, Australian, Indian, and US package
  identifiers pass the Android notification package filter and parse with the
  existing transaction grammar.
- Random non-finance packages still do not run through the parser.
- Existing notification false-positive guards remain covered by tests.

## Non-goals

- Do not allow arbitrary notification packages.
- Do not parse notification text without money/action evidence.
- Do not add cloud categorization or user-data collection.

## Validation

- 2026-06-13: `:ingestion:notification-listener:test` and
  `:ingestion:sms-parser:test` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: Full gate passed with `test`,
  `:app:assemblePersonalFullSmsDebug`, `:app:assembleStoreSafeDebug`,
  `:app:lintPersonalFullSmsDebug`, and `:app:lintStoreSafeDebug`.
- 2026-06-13: Runtime notification tap-through could not complete locally:
  `adb devices -l` returned no attached devices, `emulator -accel-check`
  reported that the Android Emulator hypervisor driver is not installed,
  `AtharPixelQaApi35Arm` exited because the arm64 system image is not supported
  by this x86_64 host, and the x86_64 AVD still requires hardware
  acceleration.
