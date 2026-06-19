# G-11 Notification Admin-Notice Guards

## Problem

Known finance apps send many operational notifications that are useful to the
user but are not money movement:

- `Your card ending 1234 has been activated.`
- `Terms and conditions updated for your card ending 1234.`
- `New device linked to your account. Review if this was not you.`
- `تم تفعيل بطاقتك الرقمية بنجاح`

Without an explicit ignore path, these notifications can become failed parse
rows in the audit because they have no amount/action, or can look risky when
future wording adds card/account numbers. Store-safe notification ingestion
should stay quiet unless the notification is plausibly a posted transaction.

## Decision

Add a conservative admin-notice guard in `GenericBankNotificationTemplate`
before amount selection:

- ignore card activation/block/lock/freeze/issue/ship/delivery/expiry/renewal
  notices when no transaction action is present
- ignore PIN, linked-device, biometric/quick-login, beneficiary/payee,
  terms/privacy/document, maintenance, service-interruption, and app-migration
  notices when no transaction action is present
- include Arabic card activation/admin, linked-device, beneficiary, terms,
  maintenance, and app-update phrases
- keep posted card-use notifications parsing because they contain real expense
  action wording

## Acceptance

- English card activation notifications are ignored.
- English card terms notices are ignored.
- English linked-device notices are ignored.
- Arabic card activation notices are ignored.
- Existing posted card-use notification coverage keeps passing.
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
