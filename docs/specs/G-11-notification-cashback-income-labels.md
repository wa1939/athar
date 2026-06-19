# G-11 notification cashback-income labels

## Goal

Generic notification parsing already keeps reward-only cashback copy from becoming fake expenses, and posted English cashback credits could parse as income. The remaining gap was categorization quality: rows such as `Cashback credited SAR 10.00 from Rewards` preserved only the source, so first-run users could still see uncategorized income unless a source-specific seed happened to match.

## Scope

This slice extends the known-finance-app generic notification parser only. Posted/credited/deposited cashback copy now gets an exact reusable income label:

- `Cashback income`
- `دخل كاش باك`

Named sources are preserved as `Cashback income - source` when the notification includes a payer/reward-source hint. The exact labels are seeded to `cat-side-income` so users get category help without broad `cashback`, `reward`, or points substring seed rules.

## False Positive Boundaries

Cashback remains a high-risk word because it appears in offers, reward balances, points copy, and card-purchase summaries. This slice keeps existing reward-only and marketing guards in front of amount selection, and the new cashback label only applies when the copy has posted-credit evidence such as `credited`, `deposited`, `received`, `paid`, `posted`, or Arabic deposit/incoming terms.

## Tests

Covered by `GenericBankNotificationTemplateTest` for English credited cashback with source preservation, Arabic credited cashback without a source, and cashback-income offer copy that must stay ignored. `SeedRulesAssetTest` pins the two exact public seed labels and updates the active rule count to 724.

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

Runtime install was attempted, but both local AVD paths remain unusable on this host. `emulator -accel-check` reported that the Android Emulator hypervisor driver is not installed. `AtharPixelQaApi35` exited before install with code `1` because x86_64 emulation currently requires hardware acceleration, `AtharPixelQaApi35Arm` exited with code `1` because arm64 system images are unsupported on this x86_64 host, and final `adb devices` listed no attached devices. Evidence is in `build/qa/notification-cashback-income-labels-emulator/`.
