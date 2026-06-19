# G-11 - Notification Insurance Non-Posted Guards

## Problem

The recurring-expense notification parser correctly normalizes posted insurance
premium payments, but insurance apps and banks also send quote, offer, and
estimate notifications with real-looking amounts. Copy such as `Policy premium
quote AED 500.00` contains the same `policy premium` label as a posted payment,
so it can become a false pending expense unless it is filtered before amount
selection.

## Decision

Add a narrow parser-level non-posted guard for insurance and policy-premium
quote/offer/estimate copy:

- Ignore English insurance or policy-premium notifications when quote, estimate,
  offer, promo, discount, coupon, eligibility, coverage-review, or similar
  planning terms appear near the insurance wording.
- Ignore equivalent Arabic insurance-offer and insurance-estimate copy.
- Keep the existing posted paths unchanged: completed premium payments still
  normalize to `Insurance premium`, and named insurers such as `Tawuniya` are
  still preserved.
- Do not add broad seed rules or infer arbitrary insurer names from non-posted
  marketing copy.

## Acceptance

- `Policy premium quote AED 500.00 is ready` is ignored.
- `Insurance offer SAR 650.00 for your vehicle policy` is ignored.
- `Your insurance estimate is USD 900.00` is ignored.
- `عرض تأمين بمبلغ ٩٠٠ ر.س` is ignored.
- `Insurance premium AED 500.00 completed` still parses as an expense with
  merchant `Insurance premium`.
- `Insurance premium paid to Tawuniya AED 500.00` still preserves merchant
  `Tawuniya`.

## Non-goals

- Do not change SMS insurance parsing.
- Do not add new notification package identifiers.
- Do not change seed-rule counts; this is a parser false-positive guard only.
- Do not ignore posted insurer refunds, reimbursements, or credited insurance
  payouts.

## Validation

- `.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`
- `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime `storeSafeDebug` install/launch was attempted. In this resumed shell,
  `adb devices -l` listed no connected devices, `where emulator` could not find
  an Android emulator executable, and `:app:installStoreSafeDebug` failed with
  `No connected devices!`. Evidence is under
  `build/qa/notification-insurance-nonposted-guards-emulator/`.
