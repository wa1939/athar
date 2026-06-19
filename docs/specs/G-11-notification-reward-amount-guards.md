# G-11 Notification Reward Amount Guards

## Problem

Finance-app notifications can mention cashback or rewards using transaction-like
words such as `purchase` or `card purchase`. When the only currency amount is
the reward, Athar could create a fake expense for the cashback amount instead
of ignoring the notification.

Example risky shapes:

- `Earn 10 SAR cashback on your next card purchase`
- `Your card purchase at Carrefour earned SAR 5 cashback`

Those messages are not posted spending. They should not create pending rows.

## Decision

Extend `GenericBankNotificationTemplate` reward handling:

- detect reward/cashback/points wording near currency amounts
- ignore reward-only notifications when there is no actual credited income
- skip reward-adjacent amounts when selecting the transaction amount
- keep parsing real cashback credits as income
- keep parsing spend notifications that mention a separate cashback amount, but
  use the posted spend amount and clean the merchant suffix

The change remains behind the existing known-finance-package gate and does not
broaden arbitrary notification parsing.

## Acceptance

- Reward-only cashback notifications with card-purchase wording are ignored.
- Card-purchase cashback notifications without a posted spend amount are
  ignored.
- A spend notification with a separate cashback reward amount uses the spend
  amount and merchant.
- Cashback credited as income still parses as income.
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
  `AtharPixelQaApi35` software boot stayed `emulator-5554 offline` in ADB for 3
  minutes; the stuck headless qemu process and AVD lock files were cleaned up
  afterward.
