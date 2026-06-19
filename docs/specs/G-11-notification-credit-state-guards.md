# G-11 Notification Credit-State Guards

## Problem

Credit-card apps can send account-state notifications with currency amounts
that are not transactions:

- `Available credit USD 5,000.00 on your card ending 1234`
- `Your credit limit was updated to AED 20,000.00`
- `الحد الائتماني المتاح ٥٠٠٠ ر.س على بطاقتك`

These notifications can contain amount tokens plus card/location hint wording.
Without a dedicated guard, the generic notification parser could treat them as
expenses through the merchant-hint fallback, or leave them as noisy failed
parses instead of deliberate ignores.

## Decision

Treat credit-card state as non-transaction account context when no real
spend/income/transfer action is present:

- ignore available-credit, credit-available, remaining-credit, credit-limit,
  cash-advance-limit, and available-cash-advance wording
- include Arabic credit-state phrases such as `الحد الائتماني`,
  `الحد المتاح`, and `الائتمان المتاح`
- broaden the existing limit guard to include credit and cash-advance limits
- preserve real credited-income copy such as `Credit of USD 250.00 from ACME`

## Acceptance

- Available-credit notifications with card hints are ignored.
- Credit-limit update notifications with amounts are ignored.
- Arabic available-credit notifications with amounts are ignored.
- Real credited-income notification coverage keeps passing.
- Existing notification parser coverage keeps passing.

## Validation

- 2026-06-14: `:ingestion:sms-parser:test` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: full JVM test/build/lint stack passed:

  ```powershell
  .\gradlew.bat --console=plain test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon --max-workers=1
  ```

- Runtime install/launch could not complete on this PC. `adb devices -l`
  showed no online device, `emulator -accel-check` reported that the Android
  Emulator hypervisor driver is not installed, and a bounded
  `AtharPixelQaApi35` software boot logged `Failed to load opengl32sw`, opened
  an emulator crash dialog, and never exposed an online ADB device. The stale
  AVD lock directory/file were cleaned afterward.
