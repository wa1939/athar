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
- Extract currency-aware amounts, merchant/counterparty hints, and ignore OTP/security/marketing
  notifications so offers do not become transactions.
- Teach the notification listener to include `bigText`, `subText`, and `textLines`, not only
  `android.title` and `android.text`.
- Centralize store-safe bank package matching and include Saudi banks/wallets plus common global
  finance apps and wallets.

This is not full notification coverage for every bank app. It is a safe universal baseline that
turns common card-spend push copy into pending/confirmed transactions through the existing
local-first pipeline.

## Acceptance Criteria

- Store-safe notifications from recognized bank packages reach the parser.
- A notification like `You spent SAR 42.00 at Starbucks` becomes an EXPENSE.
- Arabic decimal digits and `ر.س` currency parse correctly.
- Income and transfers are distinguished from purchases.
- Card last-4 numbers are not mistaken for the transaction amount when a currency-marked amount
  appears later in the text.
- OTP, security-code, declined, and marketing/cashback offer notifications are ignored.
- Random non-bank packages do not match the notification template.
- SMS sender text that happens to contain a bank brand does not match the notification template.

## Validation

- JVM parser tests for common notification bodies and false positives.
- Compile `ingestion:notification-listener` and `app:assembleStoreSafeDebug`.
