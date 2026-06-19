# R-14 Parser Registry Parity

## Problem

Private-corpus parser work depends on tests and temporary aggregate audits
exercising the same built-in SMS parser order the app runs in production. That
order had drift risk because app DI owned one private `builtInTemplates()` list
while `SmsCorpusTest` kept a separate copy. As new wallet, structured-bank, and
notification templates were added, a stale test/audit list could miss production
behavior or report gaps that the app no longer had.

## Decision

Move the built-in SMS template order into the pure parser module:

- add `BuiltInSmsTemplateRegistry.templates()`
- have app DI construct `HybridSmsParser` from that shared registry
- have `SmsCorpusTest` use the same registry
- add registry tests that pin the important ordering invariants:
  `global-bank-ignore` first, `generic-bank-notification` early, and
  `universal-amount` last
- pin inclusion of wallet and structured-bank templates that were easy to miss
  in local audit helpers

This does not parse new message shapes by itself. It makes future private-corpus
and public corpus work safer by ensuring the audit/test harness uses the app's
actual production parser set.

## Acceptance

- App DI no longer owns a second built-in parser list.
- Corpus tests use the shared registry.
- Registry tests pin critical parser ordering and coverage.
- Existing parser corpus tests keep passing.
- Full app build/lint remains green.

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
  AVD lock files were cleaned afterward.
