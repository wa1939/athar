# G-11 - Notification Salary Income Labels

## Problem

Generic store-safe bank notifications already classify `salary` as income, but
salary/payroll push copy can arrive without a sender label. When that happens,
the ingestion pipeline falls back to the notification package as the transaction
merchant, so the existing shared `salary` seed rule cannot auto-categorize the
income row.

Nearby false positives include loan and financing offers that mention "salary
transfer required" and a large amount. Those are marketing/eligibility notices,
not posted income.

## Decision

Keep the known-finance-package gate unchanged and add parser-level behavior:

- Normalize explicit salary/paycheck/wage income notifications with no
  counterparty to `Salary`.
- Preserve a specific payroll source by prefixing it with the shared label, for
  example `Salary - ACME Payroll`, so the row remains useful and still matches
  the shared salary seed rule.
- Normalize Arabic salary deposits to `راتب`.
- Ignore salary-transfer loan/finance offers before amount parsing.
- Do not treat a generic company name containing `Payroll` as salary unless the
  notification body has explicit salary/payroll transaction wording.

## Acceptance

- `Salary credited SAR 9,000.00` parses as income with counterparty `Salary`.
- `Salary credited USD 9,000.00 from ACME Payroll` parses as income with
  counterparty `Salary - ACME Payroll`.
- `تم إيداع راتب ٩٠٠٠ ر.س` parses as income with counterparty `راتب`.
- Salary-transfer financing offers with amounts are ignored.
- Existing generic income notifications such as `You received ... from ACME
  Payroll`, `Payment from ACME Payroll`, and `ACME Payroll paid you ...` keep
  their original counterparty.

## Non-goals

- Do not add new package identifiers.
- Do not change SMS salary parsing.
- Do not add or change seed rules.
- Do not infer income category for arbitrary employer names without explicit
  salary/payroll transaction wording.

## Validation

- `.\gradlew.bat --console=plain :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest" --no-daemon --max-workers=1`
- `.\gradlew.bat --console=plain :ingestion:sms-parser:test :ingestion:notification-listener:test --no-daemon --max-workers=1`
- `.\gradlew.bat --console=plain test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon --max-workers=1`
- `git diff --check`
- Runtime `storeSafeDebug` install/launch was attempted on `AtharPixelQaApi35`; `emulator -accel-check` still reported that the Android Emulator hypervisor driver is not installed, and the bounded software emulator exposed only `emulator-5554 offline`. APK install, screenshot capture, and logcat capture could not run. Stale emulator/qemu/netsim processes and AVD locks were cleaned afterward.
