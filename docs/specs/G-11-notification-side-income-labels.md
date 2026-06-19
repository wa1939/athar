# G-11 notification side-income labels

## Goal

Store-safe notification ingestion already normalizes salary, tax refund, reimbursement, bonus, and work-expense rows to seed-backed labels. Side-income notifications were still weaker: copy such as `Freelance payment received USD 450.00 from Upwork` or `Dividend credited USD 32.10 from Vanguard` could preserve the source but miss the shared `Side income` category unless a vendor/platform seed happened to match.

## Scope

This slice extends the generic known-finance-app notification parser only. It adds exact English/Arabic labels for:

- `Freelance income` / `دخل عمل حر`
- `Rental income` / `دخل إيجار`
- `Dividend income` / `دخل توزيعات`
- `Interest income` / `دخل فوائد`

Specific sources are preserved as `Label - source` when the notification includes a named payer or platform, for example `Freelance income - Upwork` or `Dividend income - Vanguard`. The exact labels are seeded to `cat-side-income` so first-run users get category help without broad `freelance`, `rent`, `dividend`, or `interest` substring rules.

## False Positive Boundaries

The parser ignores side-income invoices, proposals, quotes, estimates, submitted or pending freelance milestones, rental-income projections/statements/applications, dividend announcements/record-date/rate/yield notices, interest-rate updates, and Arabic equivalents with amounts before amount selection. Those messages can include money values but do not prove a posted deposit.

## Tests

Covered by `GenericBankNotificationTemplateTest` for English freelance, rental, dividend, and interest income notifications, Arabic freelance and interest income notifications, source preservation, and non-posted invoice/estimate ignores. `SeedRulesAssetTest` pins the eight exact public seed labels and updates the active rule count to 722.

## Validation

Passed focused parser/seed tests, `git diff --check`, and the full JVM/debug-APK/lint stack:

```powershell
.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug
```

Runtime install was attempted, but both local AVD paths remain unusable on this host. `AtharPixelQaApi35` exited with Windows access-violation code `-1073741819` after `emulator -accel-check` reported the Android Emulator hypervisor driver is not installed, `AtharPixelQaApi35Arm` exited because arm64 system images are unsupported on this x86_64 host, and final `adb devices` listed no attached devices. Evidence is in `build/qa/notification-side-income-labels-emulator/`.
