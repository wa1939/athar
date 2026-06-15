# G-11 - Notification Condo-Fee Labels

## Problem

The bundled category catalog already has the TMOAP-aligned `Condo fees`
category, but store-safe notification parsing did not normalize posted condo,
HOA, strata, or building service-charge payments into a stable merchant label.
Those rows could parse as generic fees or uncategorized payments, creating
manual cleanup for a recurring housing cost.

Nearby false positives include due reminders, assessments, statements, invoice
notices, and overdue copy with amounts. Those are not posted transactions and
must not enter the pending tray.

## Decision

- Add a conservative condo-fee detector behind the existing known finance-app
  notification gate.
- Normalize generic posted condo/HOA/strata/building service-charge copy to the
  shared label `Condo fees`.
- Preserve specific associations or buildings when a clear merchant is present,
  such as `Marina Heights HOA`.
- Ignore reminders, due notices, assessments, invoices, statements, unpaid, and
  overdue copy before amount parsing.
- Add one exact priority-90 public seed rule for `Condo fees`, mapped to the
  existing `cat-condo-fees` category.

## Acceptance

- Posted `HOA fee payment USD 275.00 posted` parses as an expense with merchant
  `Condo fees`.
- Posted `Building service charge AED 900.00 paid` parses as an expense with
  merchant `Condo fees`.
- Arabic posted housing-fee copy parses as an expense with merchant
  `Condo fees`.
- Specific association/building merchant hints are preserved when present.
- Condo/HOA/building-service due reminders, assessment notices, and Arabic
  due/reminder copy with amounts are ignored.
- Seed rules reference existing categories, stay unique, and include the exact
  shared label.

## Non-goals

- Do not add broad public seed rules for `hoa`, `strata`, `building`,
  `service charge`, or private building/association names.
- Do not parse arbitrary non-finance notifications.
- Do not infer rent, mortgage, ownership state, or property identity from
  notification text.
- Do not treat reminders, due notices, assessments, invoices, statements,
  unpaid, or overdue copy as posted transactions.

## Validation

- Focused parser and seed tests passed with JDK 17:
  `:ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`
  and
  `:core:data:testDebugUnitTest --tests "com.athar.core.data.seed.SeedRulesAssetTest"`.
- Full JVM test/build/lint stack passed with JDK 17:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Forced gated private SMS audit passed with redacted aggregate output only:
  7,887 records, 3,864 parser successes, 4,023 ignored, 0 parser failures,
  0 failed known-bank messages, 2,374 parsed expenses, 1,287 categorized
  expenses, 1,087 uncategorized expenses, 0 missing-merchant parsed expenses,
  20 hashed uncategorized merchant groups, and `rawBodiesWritten = 0`.
- Runtime `storeSafeDebug` install/launch was attempted with the built APK.
  `emulator -accel-check` reported that the Android Emulator hypervisor driver
  is not installed. The bounded hidden `AtharPixelQaApi35` launch stayed offline
  and exited without an online ADB device, so install/screenshot/UI-dump/logcat
  capture could not run. Evidence was written to
  `build/qa/notification-condo-fee-labels-emulator/`, and cleanup finished with
  no emulator/qemu/adb/netsim process and no AVD lock files.
