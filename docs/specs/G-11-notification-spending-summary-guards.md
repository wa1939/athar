# G-11 Notification Spending-Summary Guards

## Problem

Finance apps often send activity summaries that look like transactions because
they contain a spend verb and a currency amount:

- `Weekly recap: You spent USD 500.00 this week`
- `You spent SAR 1,200.00 this month`
- `ملخص الإنفاق: دفع ٥٠٠ ر.س هذا الشهر`

Those notifications summarize many transactions and should not create one fake
pending expense. The generic notification parser previously treated `spent`
plus an amount as enough evidence for an expense, even when the copy was a
weekly or monthly recap.

## Decision

Add a conservative summary guard in `GenericBankNotificationTemplate` before
amount selection:

- ignore spending summaries, recaps, reports, insights, snapshots, and budget
  summary/update wording
- ignore English `you spent ... this week/month` recap copy
- ignore Arabic spending-summary/report phrases
- keep real merchant spend notifications parsing, such as
  `You spent SAR 42.00 at Starbucks`

## Acceptance

- Weekly recap notifications with amounts are ignored.
- Monthly `you spent ... this month` summary notifications are ignored.
- Arabic spending-summary notifications with amounts are ignored.
- Existing real spend notification coverage keeps passing.
- Existing notification parser coverage keeps passing.

## Validation

- 2026-06-14: `:ingestion:sms-parser:test` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: full JVM test/build/lint stack passed:

  ```powershell
  .\gradlew.bat --console=plain test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon --max-workers=1
  ```

- Runtime install/launch could not complete on this PC. `adb devices -l`
  showed no online device, `emulator -accel-check` reported that the Android
  Emulator hypervisor driver is not installed, and a bounded
  `AtharPixelQaApi35` software boot logged `Failed to load opengl32sw`, opened
  an emulator crash dialog, and never exposed an online ADB device. The stale
  AVD lock files were cleaned afterward.
