# Bulk-categorize-via-AI workflow (Issue #5a, shipped in beta.18)

Use this when you have dozens or hundreds of uncategorized transactions sitting in your pending/dismissed trays and you don't want to tap "Always categorize…" on each one.

## The three-step loop

1. **Open Settings → "تصنيف بالذكاء الاصطناعي · مجمّع" / "Bulk categorize with AI"** → tap **Export uncategorized**. Athar writes a CSV of every non-transfer transaction that is PENDING, DISMISSED, or CONFIRMED-without-category, including the valid active category options for each row's type. Default filename: `athar-uncategorized.csv`. Save it somewhere you can reach from a desktop.
2. **Open ChatGPT / Claude / Z.ai** in a fresh chat. Drop in the prompt below, attach (or paste) the CSV, and ask for the filled-in CSV back.
3. **Back in Athar → same Settings card → Import categorized.** Pick the filled CSV. Filled rows update their transactions (status → CONFIRMED, category set). If a repeated merchant group has one unambiguous filled category, blank peers in that imported CSV group inherit it. Athar also records an exact learned `CategoryRule` per unambiguous `merchant → category` pair so future SMS from the same normalized merchant auto-categorize without broad substring matching.

A typical 800-row export takes ChatGPT about 60–90 seconds; import takes a fraction of a second.

## CSV columns

The export looks like:

```
id,stable_key,source_ref_id,merchant,merchant_normalized,merchant_group_count,category_options,amount,currency,type,status,date,raw_body,category_id
3f7a-…,8d9e-…,inbox-4242,Hemmah,hemmah,8,"cat-home-maintenance=Home maintenance / صيانة منزل | cat-other-expense=Other / متفرقات",99.00,SAR,EXPENSE,DISMISSED,2026-04-22,...raw SMS body...,
```

- **`id`** — Athar's internal transaction id. Do not change. The importer tries this first.
- **`stable_key`** — a deterministic fingerprint Athar uses if the transaction id changed after a rescan/backfill. Do not change.
- **`source_ref_id`** — the raw SMS/notification reference when available. Helps Athar rebuild the same stable key after a rescan. Do not change.
- **`merchant`** / **`merchant_normalized`** — as parsed by the ingestion pipeline. Lower-case normalized version is what gets used for rule matching.
- **`merchant_group_count`** — how many exported rows share the same normalized merchant. The export is sorted so repeated merchants appear first and together; assign one consistent category to the group unless the raw body proves otherwise. For human review, filling one representative row is enough when the blank rows in the same group should inherit the same category.
- **`category_options`** — read-only active category choices for this row's `type`, using the user's current category table. Do not edit. Pick one of these IDs for `category_id`; this keeps custom categories visible to ChatGPT/Claude without a separate lookup file.
- **`amount` · `currency` · `type` · `status` · `date`** — context for the AI to disambiguate similar merchants. Do not change.
- **`raw_body`** — the original SMS body (when available). Often the strongest categorization signal.
- **`category_id`** — *blank in the export.* The AI fills this from the row's `category_options`. For older exports without `category_options`, use one of the bundled default IDs from `core/data/src/main/assets/seed_categories.json`:
   - Expense: `cat-rent`, `cat-mortgage`, `cat-groceries`, `cat-restaurant`, `cat-coffee`, `cat-going-out`, `cat-entertainment`, `cat-travel`, `cat-gas`, `cat-public-transport`, `cat-car-maintenance`, `cat-car-payment`, `cat-utilities`, `cat-telecom`, `cat-subscriptions`, `cat-home-maintenance`, `cat-medical`, `cat-insurance`, `cat-education`, `cat-childcare`, `cat-clothing`, `cat-electronics`, `cat-gym`, `cat-gifts`, `cat-charity`, `cat-wife-allowance`, `cat-debt`, `cat-other-expense`
   - Income: `cat-salary`, `cat-side-income`

  Arabic and English display names also work as a fallback (`مطاعم` or `Restaurant`), but the id is unambiguous and survives translation changes — prefer the id shown in `category_options`.

## The AI prompt

Paste this into ChatGPT/Claude/Z.ai, then attach (or paste) the CSV.

```
You are categorizing financial transactions for a Saudi Arabic-first budgeting app called Athar.

Input: a CSV with these columns:
  id, stable_key, source_ref_id, merchant, merchant_normalized, merchant_group_count, category_options, amount, currency, type, status, date, raw_body, category_id

Your job: fill in the `category_id` column for every row you can classify confidently. Prefer one of the ids shown in that row's `category_options` column. For repeated rows with the same `merchant_normalized`, you may fill only the first representative row when every blank peer should inherit the same category. If a row in the same merchant group needs a different category, fill that row explicitly too; Athar will not train a learned rule for conflicting groups. If `category_options` is missing or incomplete, use ONLY these category ids:

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
- Transfers are normally not exported. If an older CSV contains type=TRANSFER, leave category_id blank.
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
filled in. Do not add or remove rows. Do not change `category_options` or any other column. You may leave repeated-group peers blank when the first filled row should apply to the whole group. Use the same RFC-4180
quoting as the input. Wrap your final output in a single ```csv code block.
```

## After the import

- Every filled row's transaction is now CONFIRMED with a category — visible on Today's lists, in Trends, and counted in budget targets.
- Blank rows in the same imported repeated-merchant group inherit the category when the group has exactly one filled category. If the CSV contains conflicting categories for one merchant, only the explicit rows update and the blank peers stay untouched.
- Every unambiguous merchant in the filled rows became an exact `learnedFromUser = true` `CategoryRule` at priority 200. This means:
  - Next time an SMS from that exact normalized merchant arrives, the ingestion pipeline auto-categorizes it before it ever hits the pending tray.
  - The rule wins over Athar's curated seed rules (priority 100) and the 507 AI-seeded rules (priority 60–80), so the user's personal taste always overrides the defaults.
  - The rule is *never* overwritten by future seed-file updates (see `RuleSeed.kt`).
  - Exact bulk-import rules stay local and are omitted from the community-rule export; public seed proposals still come from explicit "Always categorize X" substring rules.

## When the import skips a row

The importer logs the row index and reason to logcat (`Timber.w`). Common skip reasons:

- **`unknown category 'X'`** — the value in `category_id` didn't match a category id, English name, or Arabic name. Check spelling.
- **`no matching transaction for id/stable key`** — Athar could not find the row by id, stable key, source reference, or content fingerprint. This should be rare; it usually means the transaction was deleted or the AI changed matching columns other than `category_id`.
- Blank `category_id` — skipped silently unless another row in the same imported repeated-merchant group has exactly one unambiguous category. Use blanks to mean "AI couldn't tell" or "inherit from the group's filled representative row."

## Why this design

Inline API-key options were considered (`Settings → "Paste your OpenAI key"` + a button that calls the API in batches — Issue #5b in the roadmap). Rejected for v1 because:

1. **No surprise costs.** A 1,000-row OpenAI call at gpt-4o pricing is ~$0.10 — small but non-zero, and surprises break trust. The CSV path leverages what the user already pays for (their ChatGPT Plus / Claude Pro subscription).
2. **No new attack surface.** API keys are sensitive credentials; storing them locally (even encrypted) is a meaningful audit hit. The CSV roundtrip keeps Athar zero-credential.
3. **Higher quality.** Pasting into a chat window lets the user iterate — "you got Hemmah wrong, redo with cat-home-maintenance instead of cat-other-expense" — which is far harder to express in a single API call.
4. **Same local end state.** Both paths update the same transactions and grow the user's permanent merchant library. Bulk import trains exact local rules; explicit "Always categorize X" remains the shareable substring-rule path.

Issue #5b remains in the roadmap as an *opt-in* enhancement if a future user explicitly asks for it — but it is not a blocker.
