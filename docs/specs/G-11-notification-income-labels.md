# G-11 notification income labels

## Goal

Store-safe notification ingestion already parses posted salary, refund, reversal, and chargeback income, and the category catalog already includes TMOAP parity categories for Tax refund, Expense reimbursement, and Bonus. The missing link was a reusable label. Without it, a notification such as `Tax refund credited USD 600.00 from IRS` could land with a source counterparty that seed rules cannot safely categorize for first-run users.

## Scope

This slice extends the generic known-finance-app notification parser only. It adds exact English/Arabic income labels for:

- `Tax refund` / `استرداد ضريبي`
- `Expense reimbursement` / `تعويض مصروفات`
- `Bonus income` / `دخل مكافأة`

Specific sources are preserved as `Label - source` when the notification includes `from` or `by` fields. Generic refund, reversal, and chargeback income now use the reimbursement label so returned spend maps to the TMOAP reimbursement category instead of remaining arbitrary counterparty text.

## False Positive Boundaries

The parser ignores tax-refund estimates, reimbursement claims/approvals, and bonus promotions with amounts before amount selection. The seed rules intentionally use exact labels such as `bonus income`, not broad patterns like `bonus`, because the rule matcher is not type-aware at category lookup time.

## Tests

Covered by `GenericBankNotificationTemplateTest` for English and Arabic tax refunds, reimbursements, bonuses, existing refund/reversal/chargeback income labeling, and non-posted estimate/claim/promo ignores. `SeedRulesAssetTest` pins the six exact public seed labels and updates the active rule count to 710.

## Validation

Passed focused parser/seed tests, `git diff --check`, and the full JVM/debug-APK/lint stack:

```powershell
.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug
```

Runtime install was attempted, but both local AVDs remain unusable on this host. Evidence is in `build/qa/notification-income-labels-emulator/`.
