# G-11 - Notification Package Coverage Global Follow-Up

## Problem

The store-safe notification listener only helps when a finance app package passes
both local gates: the Android listener package matcher and the generic bank
notification parser sender matcher. The parser already understands conservative
money/action/party notification grammar, but some common Canadian, German, and
Saudi wallet packages still stop before parsing.

That leaves avoidable manual entry for users whose bank notification copy is
already parseable.

## Decision

Extend the finance-package gates with verified package identifiers for:

- Canada: CIBC, BMO Canada/US, Desjardins, Tangerine, Wealthsimple, EQ Bank, and
  KOHO.
- Germany: Deutsche Bank, ING Deutschland, and Sparkasse mobile banking.
- Saudi Arabia: SAB Mobile, urpay, and Mobily Pay.

Short or ambiguous brand names stay exact-package only where needed. For example
`ca.koho`, `com.sabb.mobilebanking`, and `com.es.mobily` are accepted, but random
restaurant, study, or telecom packages containing similar words are still
rejected. No new parsing grammar is added; accepted packages must still contain
the existing amount/action evidence, and security, marketing, statement,
scheduled-payment, request, reward-only, and authorization-hold guards remain in
front of amount extraction.

## Acceptance

- Representative package IDs from the new regions pass
  `BankNotificationPackageMatcher`.
- Representative notifications from the same packages parse through
  `GenericBankNotificationTemplate` with the existing transaction grammar.
- Ambiguous non-finance packages such as KOHO restaurant, Sabbath School, and the
  regular Mobily telecom app remain rejected.
- Existing random non-finance package rejection remains covered.

## Non-goals

- Do not allow arbitrary notification packages.
- Do not add new transaction phrase grammar in this slice.
- Do not parse notification text without money/action evidence.
- Do not add backend, telemetry, or cloud categorization.

## Validation

- `:ingestion:notification-listener:test` passed with JDK 21.
- `:ingestion:sms-parser:test --tests
  "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest"` passed
  with JDK 21.
- Full `:ingestion:sms-parser:test` passed with JDK 21.
- Forced gated private SMS parser audit passed with redacted aggregate output
  only: 7,887 records, 3,864 parser successes, 4,023 ignored, 0 parser
  failures, 0 failed known-bank messages, and 0 missing-merchant parsed
  expenses.
- `git diff --check` passed.
- Full JVM test/build/lint stack passed with JDK 21:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime `storeSafeDebug` install was attempted against `AtharPixelQaApi35`.
  The built APK was present at
  `app/build/outputs/apk/storeSafe/debug/app-storeSafe-debug.apk`, but
  `emulator -accel-check` reported that the Android Emulator hypervisor driver
  is not installed. A bounded hidden software boot stayed `emulator-5554
  offline`, so install/screenshot/UI-dump/logcat capture could not run.
  Evidence was written to
  `build/qa/notification-package-coverage-global-followup-emulator/`, and
  cleanup finished with no attached ADB device, no emulator/qemu/netsim process,
  and no AVD lock files.
- A follow-up runtime retry also attempted `AtharPixelQaApi35Arm` and
  `AtharPixelQaApi35`. The ARM AVD exited because arm64 system images are not
  supported on this x86_64 host, and the x86_64 AVD emitted startup logs but
  exited before any online ADB device appeared. Evidence was written to
  `build/qa/notification-package-coverage-global-followup-emulator-retry/`.
