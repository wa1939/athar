# G-11 - Notification Essential-Life Non-Posted Guards

## Problem

Essential-life notification labels reduce manual categorization for posted
medical, pharmacy, education, charity, and zakat payments. Some banks, schools,
clinics, pharmacies, and health apps also send quote, estimate, package, result,
or status notifications with real-looking amounts. Copy such as `Clinic visit
estimate SAR 220.00` or `Tuition fee estimate USD 500.00` can contain the same
domain words as posted payments but is not posted money movement.

## Decision

Add a narrow parser-level guard before amount selection:

- Ignore medical, clinic, dental, lab, pharmacy, prescription, school, tuition,
  university, college, and education quote/estimate/offer/package/result/status
  copy with amounts.
- Ignore equivalent Arabic medical, pharmacy, lab, and school-fee
  estimate/quote/status copy with amounts.
- Keep the existing posted label paths unchanged for `Medical payment`,
  `Pharmacy payment`, `Education payment`, `Zakat payment`, and
  `Charity donation`.
- Keep the existing due/reminder/appeal guard in place for bills and donation
  campaigns.

## Acceptance

- `Clinic visit estimate SAR 220.00` is ignored.
- `Dental consultation quote AED 350.00` is ignored.
- `Tuition fee estimate USD 500.00` is ignored.
- `تقدير رسوم مدرسية ١٥٠٠ ر.س` is ignored.
- Posted `Medical payment SAR 220.00 completed` still parses as
  `Medical payment`.
- Posted `Pharmacy purchase AED 75.00 posted` still parses as
  `Pharmacy payment`.
- Posted `Tuition payment USD 500.00 completed` still parses as
  `Education payment`.

## Non-goals

- Do not change SMS essential-life parsing.
- Do not add new notification package identifiers.
- Do not change public seed rules or seed count.
- Do not infer real clinics, pharmacies, schools, or charities from
  non-posted copy.

## Validation

- `.\gradlew --console=plain --no-daemon --max-workers=1 :ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"`
- `.\gradlew --console=plain --no-daemon --max-workers=1 test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`
- Runtime `storeSafeDebug` install/launch was attempted. In this resumed shell,
  `adb devices -l` listed no connected devices, `where emulator` could not find
  an Android emulator executable, and `:app:installStoreSafeDebug` failed with
  `No connected devices!`. Evidence is under
  `build/qa/notification-essential-life-nonposted-guards-emulator/`.
