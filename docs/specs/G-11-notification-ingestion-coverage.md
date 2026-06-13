# G-11 — Notification Ingestion Coverage

## Problem

The Play-Store-safe flavor cannot read SMS. It depends on bank-app push notifications, but the
current listener forwards the Android package name and only the title/body pair into the SMS parser.
Most parser templates expect SMS sender IDs, so many real bank notifications are ignored before any
amount is inspected.

## Decision

Ship a first G-11 slice that makes bank notifications parseable without weakening SMS safety:

- Add a generic bank-notification parser template that matches `notification:<package>` senders
  rather than SMS sender IDs.
- Parse common push formats in English and Arabic: spent/purchase/paid, received/deposit/credited,
  and sent/transferred.
- Cover common wallet/card-app phrasing that does not use the first-slice verbs: card-used,
  card-payment, direct-debit, payment-from, paid-you, and ACH-credit.
- Extract currency-aware amounts, merchant/counterparty hints, and ignore OTP/security/marketing
  notifications so offers do not become transactions.
- Ignore statement-ready, minimum-payment, payment-due, and transfer-limit notices that contain
  amounts but are not actual money movement.
- Teach the notification listener to include `bigText`, `subText`, and `textLines`, not only
  `android.title` and `android.text`.
- Centralize store-safe bank package matching and include Saudi banks/wallets plus common global
  finance apps and wallets, including PayPal, Venmo, Cash App, Amex, Bank of America, Wells Fargo,
  Citi, and USAA.

This is not full notification coverage for every bank app. It is a safe universal baseline that
turns common card-spend push copy into pending/confirmed transactions through the existing
local-first pipeline.

## Acceptance Criteria

- Store-safe notifications from recognized bank packages reach the parser.
- A notification like `You spent SAR 42.00 at Starbucks` becomes an EXPENSE.
- Arabic decimal digits and `ر.س` currency parse correctly.
- Income and transfers are distinguished from purchases.
- Wallet/card phrases such as `Card ending 1234 was used for USD 19.99 at Amazon`,
  `Payment from ACME USD 250.00`, and `ACME paid you $1,200.00` parse with the right type.
- Card last-4 numbers are not mistaken for the transaction amount when a currency-marked amount
  appears later in the text.
- OTP, security-code, declined, marketing/cashback offers, statement/minimum-payment reminders, and
  transfer-limit notifications are ignored.
- Random non-bank packages do not match the notification template.
- SMS sender text that happens to contain a bank brand does not match the notification template.

## Validation

- JVM parser tests for common notification bodies and false positives.
- Compile `ingestion:notification-listener` and `app:assembleStoreSafeDebug`.
- 2026-06-13: `:ingestion:sms-parser:test` and `:ingestion:notification-listener:test` passed
  with `JAVA_HOME=C:\Users\waok\.codex\jdks\jdk-17.0.19+10`.
- 2026-06-13: `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug` passed with `--no-daemon --max-workers=1`.
- 2026-06-13: `adb devices -l` returned no attached devices, so runtime notification tap-through and screenshot UI audit remain blocked under QA-01.
