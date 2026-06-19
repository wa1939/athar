# G-08 - Receipt Image OCR Prefill

## Problem

Athar already stores encrypted receipt images and can parse pasted receipt/OCR
text, but a photographed receipt still requires the user to type the amount and
merchant manually. That leaves a gap for unsupported-bank users, travelers, and
cash purchases where the receipt photo is the best source of transaction data.

## Decision

Run on-device text recognition after an Add Transaction receipt image is copied
into the pending encrypted attachment list:

- Use the bundled ML Kit Android text-recognition dependency documented by
  Google so the recognizer is available at install time instead of relying on a
  first-use model download.
- Keep the receipt attachment workflow local-first: Android Photo Picker,
  copied image bytes, no broad storage permission, no cloud OCR, no telemetry.
- Feed recognized text into the existing conservative receipt text parser from
  `G-08-receipt-text-prefill.md`.
- Fill amount, merchant, date, type, and matching recent-merchant category when
  the recognized text parses as a receipt.
- Do not copy large OCR text into the Quick Entry field.
- Preserve an already selected category when OCR finds a merchant/amount but no
  exact recent-merchant category suggestion.
- Treat OCR as best effort: unreadable/no-total/failed recognition leaves the
  receipt attached and reports a non-blocking receipt status.

The bundled recognizer is the Latin-script ML Kit model. The parser remains
bilingual when text is available, but Arabic-only image recognition is not
claimed for this slice.

## Acceptance

- Picking a receipt image attaches it before OCR runs.
- While OCR is running, receipt attach/remove controls are disabled and the
  receipt row reports that the receipt is being read.
- Recognized receipt text with a total and merchant prefills amount, merchant,
  date, expense type, and matching category without replacing Quick Entry text.
- Recognized text with no parseable total/merchant leaves existing fields
  unchanged and reports that amount/merchant were not found.
- OCR exceptions leave the receipt attached and report that text reading failed.
- No cloud service, telemetry, or broad storage permission is added.

## References

- Google ML Kit Android text recognition docs:
  https://developers.google.com/ml-kit/vision/text-recognition/v2/android

## Validation

- 2026-06-15: Focused Today validation passed with JDK 17:
  `:feature:today:testDebugUnitTest --tests "com.athar.feature.today.ManualEntryPhraseParserTest" :feature:today:compileDebugKotlin`.
- 2026-06-15: `git diff --check` passed.
- 2026-06-15: Full JVM/debug-APK/lint validation passed with JDK 17 and
  `ATHAR_PRIVATE_SMS_EXPORT` set:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- 2026-06-15: Runtime `personalFullSmsDebug` install/launch was attempted with
  the built APK. `AtharPixelQaApi35` exited before exposing an online ADB device
  with exit code `1` because x86_64 emulation requires hardware acceleration and
  the Android Emulator hypervisor driver is not installed. `AtharPixelQaApi35Arm`
  also exited with exit code `1` because arm64 system images are unsupported on
  this x86_64 host. No install, screenshot, UI dump, or logcat capture could run.
  Evidence is in `build/qa/receipt-image-ocr-emulator/`.
