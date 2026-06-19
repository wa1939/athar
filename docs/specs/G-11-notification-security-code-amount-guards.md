# G-11 Notification Security-Code Amount Guards

## Problem

Bank authentication notifications can include payment words and amounts even
though no transaction has posted. For example:

- `Use OTP 123456 to authorize payment of SAR 500.00. Do not share it.`
- `رمز التحقق ... لتأكيد عملية شراء بمبلغ ٥٠٠ ر.س`

The notification parser already ignored simple security-code messages, but
payment/purchase wording could make these authorization prompts look like real
expenses.

## Decision

Add a narrow security-code authorization guard to
`GenericBankNotificationTemplate` before amount selection:

- ignore OTP, one-time password, verification/security/auth code, passcode, and
  do-not-share wording
- include Arabic verification/confirmation/security-code phrases
- keep real posted payment notifications parsing when they do not contain
  security-code wording
- keep the known-finance-package gate unchanged

The slice also strips terminal status words such as `confirmed` from merchant
names captured by generic hints.

## Acceptance

- English OTP/payment authorization notifications with amounts are ignored.
- Arabic verification-code purchase authorization notifications with amounts
  are ignored.
- Real posted payment notifications without security-code wording still parse.
- Merchant cleanup removes terminal confirmation words from captured merchants.
- Existing notification parser coverage keeps passing.

## Validation

- 2026-06-14: `:ingestion:sms-parser:test` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: full JVM test/build/lint stack passed:

  ```powershell
  .\gradlew.bat --console=plain test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon --max-workers=1
  ```

- Runtime install/launch could not complete on this PC: `emulator -accel-check`
  reports no Android Emulator hypervisor driver. A bounded
  `AtharPixelQaApi35` software boot emitted startup logs but never exposed an
  online ADB device; stale AVD lock files were cleaned afterward.
