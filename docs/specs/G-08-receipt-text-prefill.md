# G-08 - Receipt Text Prefill

## Problem

Receipt attachments preserve proof, but they do not reduce entry work yet.
Full image OCR needs an Android runtime dependency and device QA. Before adding
that dependency, Athar needs a small deterministic parser that can turn receipt
text into the same Add Transaction fields the user would otherwise type by
hand.

This also helps today when a user already has copied receipt text from another
scanner, email receipt, PDF, or future OCR tool.

## Decision

Extend Add Transaction quick entry so it accepts short phrases or pasted
multi-line receipt text:

- Keep phrase parsing local and dependency-free.
- Detect receipt-shaped text only when it has multiple lines and an explicit
  total/paid keyword.
- Prefer total/grand-total/amount-due lines over VAT, tax, subtotal, quantity,
  receipt-number, and balance lines.
- Choose the first plausible merchant line while skipping invoice, tax, date,
  payment, cashier, total, and mostly numeric lines.
- Extract a receipt date when it has a four-digit year, and prefill the manual
  transaction date.
- Reuse the existing recent-merchant suggestion category when the extracted
  merchant matches local confirmed history.

The parser is intentionally conservative: if it cannot find both merchant and
total, it leaves the existing fields untouched and shows the normal quick-entry
error.

## Acceptance

- Pasted English receipt text such as `STARBUCKS COFFEE ... Total SAR 29.50`
  fills amount, merchant, expense type, and date.
- Pasted Arabic receipt text with Arabic-Indic digits and `الإجمالي` fills
  amount, merchant, expense type, and date.
- VAT/tax/subtotal/quantity lines do not win over the total line.
- Recent-merchant category reuse still works for receipt-derived merchants.
- Existing short English and Arabic quick-entry phrases continue to parse.
- No cloud service, telemetry, image OCR dependency, or photo-library permission
  is added in this slice.

## Validation

- 2026-06-15: Focused receipt/quick-entry parser tests passed with JDK 17:
  `:feature:today:testDebugUnitTest --tests "com.athar.feature.today.ManualEntryPhraseParserTest"`.
- 2026-06-15: Full JVM/debug-APK/lint validation passed with JDK 17 and
  `ATHAR_PRIVATE_SMS_EXPORT` set:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- 2026-06-15: Runtime `personalFullSmsDebug` install/launch was attempted with
  the built APK. `AtharPixelQaApi35` exited before exposing an online ADB device
  with exit code `1` because x86_64 emulation requires hardware acceleration and
  the Android Emulator hypervisor driver is not installed. `AtharPixelQaApi35Arm`
  also exited because arm64 system images are unsupported on this x86_64 host.
  Evidence is in `build/qa/receipt-text-prefill-emulator/`.
