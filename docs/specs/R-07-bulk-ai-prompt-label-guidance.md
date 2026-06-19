# R-07 Bulk AI Prompt Label Guidance

## Problem

The bulk-categorization export/import path is now robust: repeated merchants are ranked, `category_options` are embedded, raw-body context is optional, imports preview impact, and exact local rules are trained only for safe specific merchants.

The copied external-AI prompt still lagged behind the newer seed and notification-label work. It listed category ids and a few Saudi merchant examples, but it did not explicitly map many normalized parser labels that Athar now emits, such as `ATM Withdrawal`, `Bank fees`, `cash deposit`, `Tax refund`, `Work expense`, or `mobile recharge`.

That leaves ChatGPT/Claude more likely to skip rows that already have a reusable seed-backed label, especially in private exports where `raw_body` is intentionally blank.

## Decision

Extend the provider-neutral prompt with compact label-to-category guidance for current seed-backed parser labels:

- cash/debt/admin labels such as ATM withdrawal, bank fees, debt payment, mobile recharge, public-service, and mobility payments;
- TMOAP income labels such as tax refund, reimbursement/refund/reversal/chargeback, bonus, cashback, side income, and cash/check deposits;
- work, life-admin, shopping, travel, and entertainment labels.

This does not add an inline AI API, network calls, automatic categorization, or any new data leaving the device. The user still explicitly exports a CSV, chooses whether raw SMS text is included, reviews the AI-filled file, previews the impact, and taps **Apply import** before writes occur.

## Acceptance

- The prompt still preserves the importer contract: same headers/rows, only `category_id` edited.
- The prompt still tells AI to avoid guesses and respect transaction type.
- Representative exact parser labels map to their current category ids.
- Prompt tests pin debt/admin, income, work/life, and event-ticket mappings.
