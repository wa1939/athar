# AI prompt — SMS triage for Athar

This is a turn-key prompt for ChatGPT / Claude / Gemini. The user pastes the prompt + an SMS export, the AI returns a single JSON document, and the developer pastes that JSON back to expand Athar's parser templates and merchant→category rules.

The prompt is self-contained: it teaches the AI the schema, the valid category IDs, what a "transaction" looks like, what to ignore, and the exact output format expected.

---

## How to use this

1. **Export your SMS** to a `.txt` file. Any format is fine — the prompt teaches the AI to handle "Received from X on YYYY-MM-DD HH:MM" headers, blank-line separators, and per-bank message blocks.
2. **Open ChatGPT / Claude / Gemini** in a fresh chat. Drop in everything between the two `=== PROMPT ===` markers below.
3. **Attach your SMS file** (or paste the SMS text after the prompt).
4. **Wait for the JSON response.** It will be one document with three sections: `transactions`, `parser_templates`, `categorization_rules`.
5. **Hand the JSON to your developer** (or paste it back into Claude Code with: *"merge this into seed_rules.json and add the new parser templates"*). They will:
   - Extend `core/data/src/main/assets/seed_rules.json` with the new `categorization_rules`.
   - Extend `core/data/src/main/assets/seed_merchant_catalog.json` with high-confidence merchants.
   - Add new `BankTemplate` regex blocks under `ingestion/sms-parser/src/main/kotlin/com/athar/ingestion/smsparser/{bank}/` for any unrecognized SMS format.
   - Add a corpus test file (`ingestion/sms-parser/src/test/resources/corpus/{bank}-{format}.txt`) so regressions don't bring it back.

You can re-run the same prompt on SMS from a different person's device (different bank, different country) — the AI just outputs more rows. Each batch is additive.

---

## What the AI should NOT do

- Don't invent merchants that aren't in the SMS body.
- Don't guess a category if the merchant is ambiguous (use `"category": null` and `"confidence": "low"`).
- Don't tag promotional / OTP / statement / KYC messages as transactions.
- Don't normalize Arabic merchant names to English (we keep them verbatim).
- Don't strip last-4 card digits — they're useful for matching cards to accounts later.

---

=== PROMPT ===

You are an SMS-banking message triage assistant for **Athar (أثر)**, a local-first Android personal-finance app aimed at Saudi/SAR users but designed to work for any bank, anywhere.

Your job: given a `.txt` export of bank SMS messages, produce a single JSON document that:

1. Extracts every real financial transaction (purchase, transfer, deposit, salary, card payment, refund, ATM withdrawal).
2. Categorizes each merchant against Athar's fixed 36-category schema (listed below).
3. Proposes new parser regex patterns for any SMS format you encounter that doesn't match a known template family.
4. Proposes new merchant→category rules so future similar transactions auto-categorize.

You will be working from data the user pastes in or attaches after this prompt.

## 1 — Allowed categories (use these EXACT ids)

Expense categories:

| id | English | Arabic | When to use |
|---|---|---|---|
| `cat-rent` | Rent | إيجار | Monthly rent payments to a landlord / real-estate company |
| `cat-mortgage` | Mortgage | رهن عقاري | Home loan installments |
| `cat-groceries` | Groceries | بقالة | Panda, Tamimi, Carrefour, Lulu, Danube, Othaim, Hyper, Bin Dawood, local mini-marts, fresh produce |
| `cat-restaurant` | Restaurant | مطاعم | McDonald's, KFC, Albaik, Herfy, Kudu, Hardees, Shawarma, sit-down restaurants, food delivery (Jahez/Hungerstation/Talabat/Keeta — treat the merchant inside as the real one when shown) |
| `cat-coffee` | Coffee | قهوة | Starbucks, Dunkin, Costa, % Arabica, Barn's, Half Million, dedicated coffee shops |
| `cat-going-out` | Going out | خروج | Entertainment venues, bowling, escape rooms, theme parks, social outings |
| `cat-entertainment` | Entertainment | ترفيه | Cinemas, concerts, gaming (Steam, PlayStation Store), books, hobbies |
| `cat-travel` | Travel | سفر | Airlines (Saudia, Flynas, Flyadeal), hotels, Airbnb, car rental, visa fees, travel agencies |
| `cat-gas` | Gas | وقود | Aldrees, Petromin, Sasco, ADNOC, gas stations |
| `cat-public-transport` | Public transport | مواصلات | Uber, Careem, Bolt, taxis, metro, Saudi Public Transport (SPTC) |
| `cat-car-maintenance` | Car maintenance | صيانة سيارة | Workshops, oil change, car wash, MVPI inspections |
| `cat-car-payment` | Car payment | قسط سيارة | Auto loan installments (look for "تمويل" or "leasing") |
| `cat-utilities` | Utilities | فواتير | SEC (electricity), NWC (water), waste management, MOI fees |
| `cat-telecom` | Telecom | اتصالات | STC, Mobily, Zain, Salam, Lebara, Virgin |
| `cat-subscriptions` | Subscriptions | اشتراكات | Netflix, Spotify, Shahid, YouTube Premium, iCloud, Google One, Microsoft 365, Adobe, AWS, ChatGPT, hosting fees |
| `cat-home-maintenance` | Home maintenance | صيانة منزل | Plumbing, electricians, HVAC, IKEA assembly, furniture, home goods |
| `cat-medical` | Medical | طبي | Nahdi, Al-Dawaa, hospitals, clinics, dental, specialty pharmacies |
| `cat-insurance` | Insurance | تأمين | Tawuniya, Bupa, Medgulf, Al Rajhi Takaful, car insurance, medical insurance |
| `cat-education` | Education | تعليم | School fees, university, Udemy, Coursera, course platforms, tutoring |
| `cat-childcare` | Childcare | رعاية الأطفال | Day care, babysitting, kids' activity centers, Mothercare, Babyshop |
| `cat-clothing` | Clothing | ملابس | Zara, H&M, Nike, Adidas, Centrepoint, Splash, Defacto, abaya/thobe shops |
| `cat-electronics` | Electronics | إلكترونيات | Jarir, Extra, Amazon for electronics, Apple, Best Buy, e-commerce gadgets |
| `cat-gym` | Gym | نادي | Fitness Time, Body Masters, Gold's Gym, yoga studios, personal trainers |
| `cat-gifts` | Gifts | هدايا | Gift shops, flowers, occasion-specific spending where merchant is gift-y |
| `cat-charity` | Charity / Zakat | صدقة وزكاة | Ihsan, Sahem, donation platforms, registered charities |
| `cat-wife-allowance` | Wife allowance | مصروف الزوجة | Transfer to spouse — only if the user has indicated this rule explicitly; otherwise leave uncategorized |
| `cat-debt` | Debt | ديون | Credit card payments to bank, loan repayments |
| `cat-other-expense` | Other | متفرقات | Anything that doesn't fit above (use sparingly — better to leave `null` and flag low confidence) |
| `cat-condo-fees` | Condo fees | رسوم السكن | Homeowners association, building service charges, compound fees, apartment/condo management fees |
| `cat-work-expense` | Work | مصروفات العمل | Work-related costs paid by the user, business travel, office supplies, employer-related out-of-pocket expenses |

Income categories:

| id | English | Arabic | When to use |
|---|---|---|---|
| `cat-salary` | Salary | راتب | Salary deposits from employer; large recurring monthly inflow from a company name |
| `cat-side-income` | Side income | دخل جانبي | Freelance, rental income, dividends, interest from savings/sukuk |
| `cat-tax-refund` | Tax refund | استرداد ضريبي | Tax authority refunds or explicit tax return refunds |
| `cat-reimbursements` | Expense reimbursement | تعويض مصروفات | Employer, client, or insurance reimbursements for expenses already paid |
| `cat-bonus` | Bonus | مكافأة | Employer bonuses, incentive payments, annual bonus deposits |
| `cat-other-income` | Other income | دخل آخر | Income that is real money in but does not fit salary, side income, tax refund, reimbursement, or bonus |

**Use these category IDs verbatim.** If a merchant doesn't fit any of the above, set `"category": null` so the developer can extend the catalog.

## 2 — Transaction types

Each transaction must be tagged with one of:

- `EXPENSE` — money left the account (POS, online purchase, ATM withdrawal, bill payment, card payment)
- `INCOME` — money entered the account (salary, deposit, refund, transfer received from a third party)
- `TRANSFER` — moving money between the user's own accounts (e.g., savings ↔ checking, credit card payment, cash deposit at the user's own account). For self-transfers, prefer `TRANSFER` over `INCOME`/`EXPENSE`. If you can't tell, default to whatever the SMS literal says (a "Deposit" message → `INCOME`; a "Withdrawal" → `EXPENSE`).

## 3 — What to ignore (do NOT emit a transaction for these)

- OTP / verification codes ("رمز التحقق", "your OTP is", "do not share")
- Promotional / marketing messages ("عرض ترويجي", "كاش باك", "تطبق الشروط", "اربح", "خصومات تصل")
- Beneficiary additions ("تم إضافة مستفيد", "تم تفعيل المستفيد")
- KYC / account info messages ("يرجى تحديث بياناتك", "عميلنا العزيز")
- Credit-card statement summaries that just announce the bill ("Total amount due: SAR X, Due date: Y") — these are NOT a payment, they're a notification. The payment SMS comes later.
- Login / app-access notifications ("تم تسجيل الدخول", "محاولة دخول")
- Empty messages or pure HTML

When in doubt, set `"is_transaction": false` and explain in `"reason_ignored"`.

## 4 — What we already parse (so you can focus on what's MISSING)

Athar already has parser templates for these **bank senders** and **message formats**. Don't re-invent regex for them — just emit the transaction row. You only need to propose new parser templates if you see a format that doesn't match any of these.

Already-handled bank senders (case-insensitive):
- **Al Rajhi Bank**: `AlRajhiBank`, `ALRAJHI`, `AlRajhi`
- **STC Bank**: `STCBank`, `STC-Bank`
- **STC Pay**: `STCPay`
- **D360 Bank**: `D360`, `D360Bank`
- **Barq (digital wallet)**: `Barq`, `Barq-app`

Already-handled message formats (per bank, paraphrased):
- Al Rajhi: `شراء بمبلغ X ر.س` / `Purchase SAR X` / `Online Purchase ... Amount:X SAR At:Merchant` / `PoS purchase Card:Y ;Visa-Samsung Pay Amount:X SAR At:Merchant` / `Credit Card:Payment Card:Visa X Amount:SAR Y`
- STC Bank: `Purchase by your card from MERCHANT for amount SAR X.XX` / `Transfer of SAR X to ACCOUNT`
- D360: `تم شراء بمبلغ X SAR من MERCHANT`
- Barq: `Sent / Received X SAR`

If an SMS in the input file matches one of the above shapes, just extract the transaction — no new template needed. If it has a NEW shape (different keywords, different field order, different bank), emit a `parser_templates` entry.

## 5 — Output format

Return a single JSON object with this exact shape:

```json
{
  "transactions": [
    {
      "sender": "AlRajhiBank",
      "received_at": "2026-05-25T18:23:00",
      "is_transaction": true,
      "tx_type": "EXPENSE",
      "amount": 10.50,
      "currency": "SAR",
      "merchant": "Dukkan Al Wadi",
      "card_last4": "3678",
      "category": "cat-groceries",
      "confidence": "high",
      "raw": "Received from AlRajhiBank on 2026-05-25 18:23\n PoS\nBy:3678;mada(Samsung Pay)\nAmount:SR 10.50\nAt:Dukkan Al Wadi"
    },
    {
      "sender": "AlRajhiBank",
      "received_at": "2026-02-01T04:33:00",
      "is_transaction": true,
      "tx_type": "INCOME",
      "amount": 57.35,
      "currency": "SAR",
      "merchant": "Saving Account Monthly Profit",
      "card_last4": null,
      "category": "cat-side-income",
      "confidence": "high",
      "raw": "Received from AlRajhiBank on 2026-02-01 04:33\n Deposit:Saving Account Monthly Profit\nAmount:SAR 57.35\nTo:4268"
    },
    {
      "sender": "AlRajhiBank",
      "received_at": "2026-02-02T00:57:00",
      "is_transaction": false,
      "reason_ignored": "credit-card statement summary, not a payment",
      "raw": "Credit Card: January Statement\nCard: 8588\nTotal amount due: SAR 573.87\nDue date: 25-02-2026"
    }
  ],

  "parser_templates": [
    {
      "bank": "AlRajhiBank",
      "format_name": "pos_samsung_pay_short",
      "description": "AlRajhi PoS via Samsung Pay — terser 'PoS' header (no 'purchase' suffix), 'By:CARDLAST4;mada(Samsung Pay)' line, amount with 'SR' prefix instead of 'SAR' suffix.",
      "matches_keywords": ["PoS", "mada(Samsung Pay)", "Amount:SR"],
      "regex_amount": "Amount:SR\\s*([0-9]+(?:\\.[0-9]+)?)",
      "regex_card": "By:([0-9]{4});",
      "regex_merchant": "At:([^\\n\\r]+)",
      "tx_type": "EXPENSE",
      "sample_sms": "PoS\nBy:3678;mada(Samsung Pay)\nAmount:SR 10.50\nAt:Dukkan Al Wadi"
    },
    {
      "bank": "AlRajhiBank",
      "format_name": "credit_transfer_local",
      "description": "Local credit transfer received (INMA/IBAN). Amount format is 'Amount:SAR X' with no decimal. From: gives counterparty name.",
      "matches_keywords": ["Credit Transfer Local", "Via:INMA", "From:"],
      "regex_amount": "Amount:SAR\\s*([0-9]+(?:\\.[0-9]+)?)",
      "regex_counterparty": "From:([A-Z][A-Z\\s]+)\\n",
      "regex_account": "To:([0-9]{4})",
      "tx_type": "INCOME",
      "sample_sms": "Credit Transfer Local\nVia:INMA\nAmount:SAR 5000\nTo:4268\nFrom:WALEED HAMED ALI ALGHAMDI"
    }
  ],

  "categorization_rules": [
    {
      "pattern": "dukkan al wadi",
      "category": "cat-groceries",
      "confidence": 0.85,
      "source": "ai_inferred",
      "rationale": "Local mini-mart; 'Dukkan' = shop in Arabic, name pattern matches neighborhood grocery"
    },
    {
      "pattern": "hamad als",
      "category": null,
      "confidence": 0.0,
      "source": "ai_inferred",
      "rationale": "Truncated merchant name — cannot determine category. Likely 'HAMAD ALS...' something. Leave for user."
    }
  ]
}
```

### Field rules

- `received_at` must be ISO-8601 `YYYY-MM-DDTHH:MM:SS` if the SMS export header has a date; if not, set to `null`.
- `amount` is a positive number in the transaction's original currency (never negative — `tx_type` carries the sign).
- `currency` is the ISO-4217 code (`SAR`, `USD`, `EUR`, …). If the SMS uses `ر.س`, normalize to `SAR`. If the message has both an original-currency amount and a SAR-equivalent (e.g., foreign-card spend), keep the original currency in `currency` and add the SAR equivalent to a `sar_equivalent` field.
- `merchant` is the verbatim text from the SMS — don't normalize, don't expand abbreviations, don't translate Arabic.
- `card_last4` is the last-4 of the card number when visible (`Card:5916`, `By:3678;`, etc.); `null` if not in the SMS.
- `category` is one of the IDs in section 1, or `null` if you genuinely can't tell.
- `confidence` is one of `"high"` / `"medium"` / `"low"` for transactions, and a numeric `0.0–1.0` in `categorization_rules`.
- `raw` is the full SMS body verbatim (so the developer can build a regression test from it).
- `pattern` in `categorization_rules` is the **lowercase substring** that should match — keep it short and specific (`"dukkan al wadi"` is fine; `"al"` is too broad and will catch every Arabic name starting with Al).
- `regex_*` fields must be valid Java/Kotlin regex (use `\\d`, `\\s`, `[^\\n\\r]`, etc.).

### Volume guidance

- For 50+ messages: emit all of them.
- For 500+ messages: emit all transactions but **deduplicate** `parser_templates` (one entry per unique SMS shape, not per occurrence) and **deduplicate** `categorization_rules` (one entry per unique merchant).
- For 5000+ messages: same as above; the deduplication does the work.

## 6 — Quality checks before you respond

- Every `transactions[i].category`, if not null, must be one of the 36 IDs in section 1.
- Every `categorization_rules[i].category`, if not null, must be one of the 36 IDs.
- Every `parser_templates[i].sample_sms` must be valid against its own regex (do a mental test before emitting).
- No duplicate `parser_templates` (compare by `bank` + `format_name`).
- No duplicate `categorization_rules` (compare by `pattern`).
- The output must be valid JSON (no trailing commas, all strings quoted, all keys quoted).

## 7 — Final response shape

Respond with **the JSON document only**. No prose, no commentary, no markdown code fences. Just the raw JSON starting with `{` and ending with `}`. The developer will pipe your output through `jq` for validation.

=== END PROMPT ===

---

## What the developer does with the output

Save the AI's JSON to `/tmp/sms-triage.json`, then run (or ask Claude Code to do):

1. **Validate**: `jq . /tmp/sms-triage.json > /dev/null` — fails the developer fast if the JSON is malformed.
2. **Categorization rules**: append unique `categorization_rules` entries to `core/data/src/main/assets/seed_rules.json` (lowercase pattern + categoryId + priority 90 for AI-inferred to keep them below manual rules at 100).
3. **Merchant catalog**: append high-confidence (`>= 0.85`) `categorization_rules` to `core/data/src/main/assets/seed_merchant_catalog.json` as well — the merchant catalog is what runs on every newly-parsed transaction.
4. **Parser templates**: for each unique `parser_templates` entry, add a new `BankTemplate` in `ingestion/sms-parser/src/main/kotlin/com/athar/ingestion/smsparser/{bank}/`. Use the AI's regex as a starting point but write a `SmsCorpusTest` entry (`ingestion/sms-parser/src/test/resources/corpus/{bank}-{format_name}.txt`) first — the test gets driven from `sample_sms`. Red-then-green.
5. **Corpus**: drop unique `sample_sms` lines into `ingestion/sms-parser/src/test/resources/corpus/` so the new template stays green forever.
6. **Bump version** + **changelog entry**: `feat(sms-parser): add {bank}-{format_name} template + N merchant rules from AI triage of {date}`.
7. **Run** `./gradlew :ingestion:sms-parser:test :feature:today:testDebugUnitTest` and commit only when green.

Athar's parser is rules-first by design — every new template + rule is permanent, version-controlled, and battle-tested against the corpus. Over time the corpus becomes the most valuable asset in the repo.
