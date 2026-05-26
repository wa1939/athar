# Bulk-categorize-via-AI workflow (Issue #5a, shipped in beta.18)

Use this when you have dozens or hundreds of uncategorized transactions sitting in your pending/dismissed trays and you don't want to tap "Always categorize…" on each one.

## The three-step loop

1. **Open Settings → "تصنيف بالذكاء الاصطناعي · مجمّع" / "Bulk categorize with AI"** → tap **Export uncategorized**. Athar writes a CSV of every transaction that is PENDING, DISMISSED, or CONFIRMED-without-category. Default filename: `athar-uncategorized.csv`. Save it somewhere you can reach from a desktop.
2. **Open ChatGPT / Claude / Z.ai** in a fresh chat. Drop in the prompt below, attach (or paste) the CSV, and ask for the filled-in CSV back.
3. **Back in Athar → same Settings card → Import categorized.** Pick the filled CSV. Each row updates its transaction (status → CONFIRMED, category set) **and** records a learned `CategoryRule` per unique `merchant → category` pair so future SMS from the same merchant auto-categorize.

A typical 800-row export takes ChatGPT about 60–90 seconds; import takes a fraction of a second.

## CSV columns

The export looks like:

```
id,merchant,merchant_normalized,amount,currency,type,status,date,raw_body,category_id
3f7a-…,Hemmah,hemmah,99.00,SAR,EXPENSE,DISMISSED,2026-04-22,...raw SMS body...,
```

- **`id`** — Athar's internal transaction id. Do not change. The importer matches rows on this.
- **`merchant`** / **`merchant_normalized`** — as parsed by the ingestion pipeline. Lower-case normalized version is what gets used for rule matching.
- **`amount` · `currency` · `type` · `status` · `date`** — context for the AI to disambiguate similar merchants. Do not change.
- **`raw_body`** — the original SMS body (when available). Often the strongest categorization signal.
- **`category_id`** — *blank in the export.* The AI fills this. Use one of the valid IDs from `core/data/src/main/assets/seed_categories.json`:
   - Expense: `cat-rent`, `cat-mortgage`, `cat-groceries`, `cat-restaurant`, `cat-coffee`, `cat-going-out`, `cat-entertainment`, `cat-travel`, `cat-gas`, `cat-public-transport`, `cat-car-maintenance`, `cat-car-payment`, `cat-utilities`, `cat-telecom`, `cat-subscriptions`, `cat-home-maintenance`, `cat-medical`, `cat-insurance`, `cat-education`, `cat-childcare`, `cat-clothing`, `cat-electronics`, `cat-gym`, `cat-gifts`, `cat-charity`, `cat-wife-allowance`, `cat-debt`, `cat-other-expense`
   - Income: `cat-salary`, `cat-side-income`

  Arabic and English display names also work as a fallback (`مطاعم` or `Restaurant`), but the id is unambiguous and survives translation changes — prefer it.

## The AI prompt

Paste this into ChatGPT/Claude/Z.ai, then attach (or paste) the CSV.

```
You are categorizing financial transactions for a Saudi Arabic-first budgeting app called Athar.

Input: a CSV with these columns:
  id, merchant, merchant_normalized, amount, currency, type, status, date, raw_body, category_id

Your job: fill in the `category_id` column for every row. Use ONLY these category ids:

EXPENSE:
  cat-rent · cat-mortgage · cat-groceries · cat-restaurant · cat-coffee · cat-going-out
  cat-entertainment · cat-travel · cat-gas · cat-public-transport · cat-car-maintenance
  cat-car-payment · cat-utilities · cat-telecom · cat-subscriptions · cat-home-maintenance
  cat-medical · cat-insurance · cat-education · cat-childcare · cat-clothing · cat-electronics
  cat-gym · cat-gifts · cat-charity · cat-wife-allowance · cat-debt · cat-other-expense

INCOME:
  cat-salary · cat-side-income

Rules:
- If type=INCOME, pick cat-salary if the merchant looks like a known employer / "salary"
  / "راتب", otherwise cat-side-income.
- If type=TRANSFER, leave category_id blank (transfers don't have categories).
- If you cannot tell, leave category_id blank — do not guess. Better to skip than mis-categorize.
- Use `raw_body` aggressively — Arabic SMS often spells the merchant differently than
  the parsed `merchant` field. Look for keywords ("مطعم", "صيدلية", "محطة", "اتصالات").
- "Hemmah" / "هيمة" / "Maharah" / "مهارة" → cat-home-maintenance (domestic-worker apps).
- "Yaqoot" / "ياقوت" → cat-utilities (water delivery subscription).
- "SAUDI ELECTRIC" / "SEC" / "الكهرباء" → cat-utilities.
- "STC" / "Mobily" / "Zain" → cat-telecom.
- "ARAMCO" / "PETROMIN" / "ALDREES" / "SASCO" → cat-gas.
- "ALDAWAA" / "NAHDI" / "DALLAH" → cat-medical.
- "FOODICS" / "JAHEZ" / "TALABAT" / "HUNGERSTATION" / restaurant chain names → cat-restaurant.
- "PANDA" / "OTHAIM" / "CARREFOUR" / "LULU" / "TAMIMI" → cat-groceries.

Output: emit the CSV BACK with the same headers and rows in the same order, only `category_id`
filled in. Do not add or remove rows. Do not change any other column. Use the same RFC-4180
quoting as the input. Wrap your final output in a single ```csv code block.
```

## After the import

- Every filled row's transaction is now CONFIRMED with a category — visible on Today's lists, in Trends, and counted in budget targets.
- Every unique merchant in the filled rows became a `learnedFromUser = true` `CategoryRule` at priority 200. This means:
  - Next time an SMS from that merchant arrives, the ingestion pipeline auto-categorizes it before it ever hits the pending tray.
  - The rule wins over Athar's curated seed rules (priority 100) and the 507 AI-seeded rules (priority 60–80), so the user's personal taste always overrides the defaults.
  - The rule is *never* overwritten by future seed-file updates (see `RuleSeed.kt`).

## When the import skips a row

The importer logs the row index and reason to logcat (`Timber.w`). Common skip reasons:

- **`unknown category 'X'`** — the value in `category_id` didn't match a category id, English name, or Arabic name. Check spelling.
- **`no transaction with id …`** — the row's `id` doesn't exist in the DB. Happens if the user ran `Rescan SMS` between export and import, which can re-create rows with new UUIDs. Re-export and try again.
- Blank `id` or blank `category_id` — skipped silently. Use blanks to mean "AI couldn't tell".

## Why this design

Inline API-key options were considered (`Settings → "Paste your OpenAI key"` + a button that calls the API in batches — Issue #5b in the roadmap). Rejected for v1 because:

1. **No surprise costs.** A 1,000-row OpenAI call at gpt-4o pricing is ~$0.10 — small but non-zero, and surprises break trust. The CSV path leverages what the user already pays for (their ChatGPT Plus / Claude Pro subscription).
2. **No new attack surface.** API keys are sensitive credentials; storing them locally (even encrypted) is a meaningful audit hit. The CSV roundtrip keeps Athar zero-credential.
3. **Higher quality.** Pasting into a chat window lets the user iterate — "you got Hemmah wrong, redo with cat-home-maintenance instead of cat-other-expense" — which is far harder to express in a single API call.
4. **Same end state.** Both paths produce the same `learnedFromUser=true` rules and the same updated transactions. The user's permanent merchant library grows identically.

Issue #5b remains in the roadmap as an *opt-in* enhancement if a future user explicitly asks for it — but it is not a blocker.
