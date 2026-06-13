# R-11 - Bank Admin Notice Ignores

## Goal

Reduce SMS audit noise from known bank senders when the message is administrative, not money
movement. The private family-device export still had failed rows after R-10, but the largest
remaining clusters were beneficiary administration, mobile-login security notices, card product
notices, savings-account promotions, app migration notices, document availability notices, and fraud
awareness copy.

## Decision

Extend `GlobalBankIgnoreTemplate` with conservative Arabic and English patterns for bank
administration messages that should never create a transaction:

- Beneficiary add / activation notices, including variants that omit Arabic hamza marks or use
  bank-specific hyphenated wording.
- New-device login, quick-login, and biometric registration/removal notices.
- Credit-card application/product notices, card-replacement terms notices, and inactive-card
  rejected-operation notices.
- Bank savings-account promotional copy and promo-code activation copy that contains no posted
  amount.
- App migration, bank-document availability, brand announcements, card-service recovery notices,
  and fraud-awareness notices.

These patterns run only after the sender is already a known bank/wallet sender. Unknown service
senders remain ignored at the registry boundary rather than being promoted into transactions.

## Acceptance

- Al Rajhi beneficiary add/activation variants are ignored.
- SNB-AlAhli beneficiary add/activation variants are ignored.
- Al Rajhi and SNB mobile-login / quick-login / biometric notices are ignored.
- D360 card terms, inactive-card rejection, savings promo, and promo-code notices are ignored.
- D360 card-service recovery notices are ignored.
- AlJazira app migration, document availability, brand-announcement, and fraud-awareness notices
  are ignored.
- Existing transaction corpus tests still pass, proving the new patterns do not mask supported
  purchase, transfer, income, refund, bill, or wallet confirmations.

## Validation

- 2026-06-13: `:ingestion:sms-parser:test` passed with JDK 17, `--no-daemon`, and
  `--max-workers=1`.
- 2026-06-13: Full JVM test/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- 2026-06-13: Runtime tap-through was blocked because `adb devices -l` returned no attached
  devices.
- 2026-06-13: A temporary local audit over `All Conversations 2026-05-27 175517.txt`
  covered 8,136 exported messages. After this follow-up, the audit reported zero failed
  known-bank messages. The raw export and temporary audit helper were not committed.
- 2026-06-13: Full JVM test/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- 2026-06-13: Runtime tap-through remains blocked because `adb devices -l` returned no
  attached devices and `emulator -accel-check` reports that the Android Emulator hypervisor
  driver is not installed.

## Non-goals

- Do not commit raw private SMS bodies.
- Do not ingest utility/provider notices unless a bank/payment sender confirms posted money
  movement.
- Do not broaden unknown-sender parsing.
