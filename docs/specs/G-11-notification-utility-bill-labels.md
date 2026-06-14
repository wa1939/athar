# G-11 - Notification Utility Bill Labels

## Problem

Generic store-safe bank notifications already parse many bill-payment shapes, but
some utility bill copy reaches categorization with a raw Arabic/generic merchant
such as `فاتورة الكهرباء` or no merchant at all. The public seed rules already
categorize shared utility labels such as `electric company`, `water company`,
and `bill payment`, so notification rows should use those labels when the bank
copy is generic.

Nearby false positives are bill due, reminder, scheduled, or upcoming notices
that contain amounts but are not posted payments.

## Decision

Keep the existing known-finance-package gate and add only parser-level behavior:

- Normalize generic electricity bill notifications to merchant
  `Electric company`.
- Normalize generic water bill notifications to merchant `Water company`.
- Normalize generic bill-payment notifications with no better merchant to
  `Bill payment`.
- Preserve specific useful biller labels, such as `Saudi Electricity`, when the
  notification provides them.
- Ignore utility bill due, reminder, scheduled, and upcoming notices before
  amount parsing.

## Acceptance

- Arabic trailing `تم دفع ١٢٥ ر.س فاتورة الكهرباء` parses as an expense with
  merchant `Electric company`.
- Arabic labeled `المفوتر: شركة الكهرباء` parses as an expense with merchant
  `Electric company`.
- `Water bill paid USD 80.00` parses as an expense with merchant
  `Water company`.
- `Bill payment AED 225.00 completed` parses as an expense with merchant
  `Bill payment`.
- `Biller: Saudi Electricity` keeps merchant `Saudi Electricity`.
- Utility bill due/reminder notifications with amounts are ignored.
- Existing random-package, scheduled-payment, security, balance, reward,
  authorization-hold, ATM, bank-fee, and debt-payment guards keep passing.

## Non-goals

- Do not add new package identifiers.
- Do not change SMS parser utility behavior.
- Do not add new seed rules.
- Do not infer utility categories outside the existing shared seed-backed
  labels.

## Validation

- `.\gradlew.bat --console=plain :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest" --no-daemon --max-workers=1`
- `.\gradlew.bat --console=plain :ingestion:sms-parser:test :ingestion:notification-listener:test --no-daemon --max-workers=1`
- `.\gradlew.bat --console=plain test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon --max-workers=1`
- `git diff --check`
- Runtime `storeSafeDebug` install/launch was attempted on `AtharPixelQaApi35`; the software emulator stayed `emulator-5554 offline`, so APK install, screenshot capture, and logcat capture could not run. Stale emulator/qemu/netsim processes and AVD locks were cleaned afterward.
