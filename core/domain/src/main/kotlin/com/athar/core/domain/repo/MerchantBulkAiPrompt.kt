package com.athar.core.domain.repo

/**
 * Prompt copied from Settings for the external bulk-categorization workflow.
 *
 * It intentionally stays provider-neutral and asks the model to return the same
 * CSV shape, because the importer is strict about preserving matching columns.
 */
val merchantBulkAiPrompt: String = """
You are categorizing financial transactions for a Saudi Arabic-first budgeting app called Athar.

Input: a CSV with these columns:
  id, stable_key, source_ref_id, merchant, merchant_normalized, merchant_group_count, category_options, amount, currency, type, status, date, raw_body, category_id

Your job: fill in the category_id column for every row you can classify confidently. Prefer one of the ids shown in that row's category_options column. For repeated rows with the same merchant_normalized, you may fill only the first representative row when every blank peer should inherit the same category. If a row in the same merchant group needs a different category, fill that row explicitly too; Athar will not train a learned rule for conflicting or incompatible-type groups. If category_options is missing or incomplete, use only these category ids:

EXPENSE:
  cat-rent, cat-mortgage, cat-groceries, cat-restaurant, cat-coffee, cat-going-out,
  cat-entertainment, cat-travel, cat-gas, cat-public-transport, cat-car-maintenance,
  cat-car-payment, cat-utilities, cat-telecom, cat-subscriptions, cat-home-maintenance,
  cat-medical, cat-insurance, cat-education, cat-childcare, cat-clothing, cat-electronics,
  cat-gym, cat-gifts, cat-charity, cat-wife-allowance, cat-debt, cat-other-expense

INCOME:
  cat-salary, cat-side-income

Rules:
- Pick only a category id that is compatible with the row's type. EXPENSE rows must use expense category ids, and INCOME rows must use income category ids.
- If type=INCOME, pick cat-salary if the merchant looks like a known employer, salary, payroll, راتب, or رواتب; otherwise use cat-side-income.
- Transfers are normally not exported. If an older CSV contains type=TRANSFER, leave category_id blank.
- If you cannot tell, leave category_id blank. Do not guess. It is better to skip than mis-categorize.
- Use raw_body aggressively. Arabic SMS often spells the merchant differently than the parsed merchant field. Look for keywords such as مطعم, صيدلية, محطة, اتصالات, كهرباء, تأمين, إيجار, رسوم, فاتورة, and اشتراك.
- Hemmah, همة, Maharah, and مهارة usually mean cat-home-maintenance.
- Yaqoot and ياقوت usually mean cat-telecom unless the raw text clearly describes a water delivery subscription.
- Saudi Electric, SEC, and الكهرباء mean cat-utilities.
- STC, Mobily, and Zain mean cat-telecom.
- Aramco, Petromin, Aldrees, and Sasco mean cat-gas.
- Aldawaa, Nahdi, and Dallah mean cat-medical.
- Foodics, Jahez, Talabat, HungerStation, and restaurant chain names mean cat-restaurant.
- Panda, Othaim, Carrefour, Lulu, Tamimi, Costco, and Whole Foods mean cat-groceries.

Output: emit the CSV back with the same headers and rows in the same order, only category_id filled in. Do not add or remove rows. Do not change category_options or any other column. You may leave repeated-group peers blank when the first filled row should apply to the whole group. Use the same RFC-4180 quoting as the input. Wrap your final output in a single ```csv code block.
""".trimIndent()
