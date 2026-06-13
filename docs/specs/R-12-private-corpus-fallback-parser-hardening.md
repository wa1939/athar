# R-12 - Private Corpus Fallback Parser Hardening

## Problem

After R-11, the private family-device SMS export no longer had failed known-bank
messages, but a second local audit showed a different reliability issue: many
successfully parsed expense rows had no merchant extracted. In the app, those rows
would appear as low-quality pending items using the bank sender as the fallback
merchant, which blocks seed rules, local category learning, and useful review.

## Decision

Harden only conservative parser paths observed in the aggregate audit:

- Treat Arabic Al Rajhi purchase headers such as `شراء`, `شراء عبر نقاط البيع`,
  `شراء دولي`, and `شراء إنترنت` as purchase markers so the existing `لدى:` merchant
  extraction runs before the low-confidence generic amount fallback.
- Treat Arabic `حوالة` transfer shapes as transfers in the Al Rajhi, structured-bank,
  and universal fallback parsers, so transfers do not inflate expense review.
- Preserve explicit Arabic biller/party labels such as `الجهة`, `الخدمة`,
  `مكان السحب`, and `مفوتر` in fallback rows instead of leaving merchant blank.
- Ignore temporary-code, card-statement, digital-wallet provisioning, monthly-statement,
  and loyalty-points-expiry notices before template parsing.

The raw export remains private. Only aggregate counts and sanitized structural tests
are committed.

## Acceptance Criteria

- Arabic labeled Al Rajhi purchase SMS parse as `EXPENSE` and preserve merchant.
- Arabic labeled Al Rajhi internal transfers parse as `TRANSFER`; incoming variants
  parse as `INCOME`.
- Arabic temporary-code messages with amounts are ignored.
- Arabic card-statement, wallet-provisioning, and loyalty-expiry notices are ignored.
- Al Rajhi generic amount fallback preserves Arabic biller labels while staying
  low-confidence for user review.
- Existing parser corpus tests continue to pass.

## Private Audit Result

Temporary local audit over `All Conversations 2026-05-27 175517.txt`:

- Records: 8,136.
- Before this slice: 4,136 parsed expenses, 79 categorized expenses, 3,800 expense
  rows with missing merchant.
- After this slice: 2,605 parsed expenses, 945 categorized expenses, 474 expense
  rows with missing merchant.

The remaining uncategorized rows now contain more real merchant strings, making them
better input for future R-05 seed review and local user learning.

## Non-goals

- Do not commit raw SMS bodies, sender histories, or the temporary audit helper.
- Do not auto-categorize private merchant names into public seed rules.
- Do not broaden unknown-sender parsing.

## Validation

- 2026-06-13: `:ingestion:sms-parser:test` passed with JDK 17, `--no-daemon`, and
  `--max-workers=1`.
- 2026-06-13: full JVM/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug` with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: runtime install/tap-through could not run because
  `adb devices -l` returned no attached devices. The SDK contains
  `AtharPixelQaApi35`, but `emulator -accel-check` reports that the Android
  Emulator hypervisor driver is not installed, and a bounded software-acceleration
  boot attempt did not expose an ADB target.
