# G-11 notification deposit-income labels

## Goal

Store-safe notification ingestion already treats generic `deposit` copy as income, but cash and check deposits still produced weak rows because they had no reusable counterparty label. The same generic deposit wording also made amount-bearing limit, hold, pending, fee, and rate notices risky: those messages can mention money without proving a posted income transaction.

## Scope

This slice extends the known-finance-app generic notification parser only. Posted deposit notifications now normalize to exact labels:

- `Cash deposit` / `إيداع نقدي`
- `Check deposit`
- `Cheque deposit`
- `إيداع شيك`

The labels route to `cat-other-income`, matching the bundled TMOAP-compatible `Other income` category. Check/cheque deposit sources are preserved as `Label - source` when the notification includes a clear `from ...` hint, for example `Check deposit - Mobile Deposit`.

## False Positive Boundaries

The parser still does not add broad `deposit`, `cash`, `check`, or `cheque` public seed rules. Deposit labels require cash/ATM/check/cheque wording plus posted-credit evidence such as `posted`, `credited`, `deposited`, `completed`, `successful`, or Arabic deposit terms. Deposit limits, fees, holds, pending availability, rejected/failed/returned deposits, rate copy, statements, reminders, appointments, and Arabic equivalents are ignored before amount selection.

## Tests

Covered by `GenericBankNotificationTemplateTest` for English cash deposits, check deposits with source preservation, Arabic cash deposits, and cash-deposit limit notices that must stay ignored. `SeedRulesAssetTest` pins the five exact public labels and updates the active rule count to 729.

## Validation

Focused parser and seed tests passed:

```powershell
.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest" :core:data:testDebugUnitTest --tests "com.athar.core.data.seed.SeedRulesAssetTest"
```

The full JVM/debug-APK/lint stack also passed with JDK 17 and `ATHAR_PRIVATE_SMS_EXPORT` set:

```powershell
.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug
```

`git diff --check` passed.

Runtime install was attempted, but both local AVD paths remain unusable on this host. `emulator -accel-check` reported that the Android Emulator hypervisor driver is not installed. `AtharPixelQaApi35` exited before install with code `1` because x86_64 emulation currently requires hardware acceleration, `AtharPixelQaApi35Arm` exited with code `1` because arm64 system images are unsupported on this x86_64 host, and final `adb devices` listed no attached devices. Evidence is in `build/qa/notification-deposit-income-labels-emulator/`.
