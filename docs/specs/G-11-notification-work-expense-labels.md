# G-11 notification work-expense labels

## Goal

Store-safe notification ingestion can parse generic payment and purchase pushes, and the category catalog already includes the TMOAP Work category. The missing link was a reusable merchant label for work-expense and office-supplies notifications. Without one, a notification such as `Business expense paid USD 42.00 at Office Depot` could land with a vendor that first-run seed rules cannot safely categorize.

## Scope

This slice extends the generic known-finance-app notification parser only. It adds exact English/Arabic labels for:

- `Work expense` / `مصروفات العمل`
- `Office supplies purchase` / `مشتريات مكتبية`

Specific vendors are preserved as `Label - vendor` when the notification includes a named merchant, for example `Work expense - Office Depot` or `مشتريات مكتبية - مكتبة`. The exact labels are seeded to `cat-work-expense` so first-run users get category help without broad `work`, `business`, `office`, or `stationery` substring rules.

## False Positive Boundaries

The parser ignores work-expense claims, submitted reports, approvals, estimates, quotes, invoices, policies, reminders, and office-supplies offers, coupons, carts, shipping, delivery, and order-status copy with amounts before amount selection. Those messages mention amounts but do not prove a posted transaction.

## Tests

Covered by `GenericBankNotificationTemplateTest` for English and Arabic work-expense and office-supplies notifications, merchant preservation, and non-posted claim/order-status ignores. `SeedRulesAssetTest` pins the four exact public seed labels and updates the active rule count to 714.

## Validation

Passed focused parser/seed tests, `git diff --check`, and the full JVM/debug-APK/lint stack:

```powershell
.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug
```

Runtime install was attempted, but both local AVDs remain unusable on this host. Evidence is in `build/qa/notification-work-expense-labels-emulator/`.
