# R-18 - Private Audit Backlog Recommendation

## Problem

The gated private SMS audit now reports parser health, category coverage, and
hashed uncategorized merchant groups without writing raw SMS content. That is
enough evidence to understand the backlog, but it still requires a maintainer to
translate aggregate counts into the next cleanup workflow.

Support diagnostics already makes that translation for opt-in app exports via
`category_backlog_recommendation`. The private audit should expose the same kind
of redacted action signal so future recovery sessions can continue the roadmap
without re-reading private data or guessing whether History repeated-backlog,
bulk CSV categorization, or manual cleanup is the right next step.

## Decision

Add a `categoryBacklogRecommendation` block to the private audit JSON.

The recommendation uses only existing aggregate private-audit data:

- `none` when there are no uncategorized parsed expenses.
- `history_repeated_backlog` when the largest hashed uncategorized merchant
  group has at least three rows.
- `bulk_categorize_export` when the uncategorized parsed-expense backlog has at
  least 25 rows.
- `manual_cleanup` when the remaining backlog is small and not repeated enough
  to justify a bulk workflow.

When both a repeated group and large backlog are present, History cleanup is the
primary action and the bulk CSV export is listed as a secondary action.

## Acceptance

- The gated audit JSON contains `categoryBacklogRecommendation`.
- The block includes `primaryAction`, ordered `recommendedActions`, and stable
  `reasonCodes`.
- The recommendation is derived only from aggregate counts and hashed group
  sizes.
- Synthetic tests cover a repeated backlog and preserve the raw-label redaction
  assertion.
- Private SMS content remains untracked and uncommitted.

## Non-goals

- Do not expose raw merchant labels, amounts, senders, bodies, notes, or rows.
- Do not change parser behavior, seed rules, or production app behavior.
- Do not auto-run cleanup or import any CSV.

## Validation

- 2026-06-15: Focused `PrivateSmsExportAuditTest` passed with JDK 17,
  `ATHAR_PRIVATE_SMS_EXPORT` set, `--no-daemon`, and `--max-workers=1`.
- 2026-06-15: The forced gated private audit wrote repository-root
  `build/private-corpus-audit/latest.json` with 7,887 records, 3,864 parser
  successes, 4,023 ignored messages, 0 parser failures, 0 failed known-bank
  messages, 2,374 parsed expenses, 1,287 categorized expenses, 1,087
  uncategorized expenses, 0 missing-merchant parsed expenses,
  `categoryBacklogRecommendation.primaryAction = history_repeated_backlog`,
  recommended actions `history_repeated_backlog` then `bulk_categorize_export`,
  and `rawBodiesWritten = 0`.
- 2026-06-15: `git diff --check` and the full JVM/build/lint stack passed with
  JDK 17, `--no-daemon`, and `--max-workers=1`: `test`,
  `:app:assemblePersonalFullSmsDebug`, `:app:assembleStoreSafeDebug`,
  `:app:lintPersonalFullSmsDebug`, and `:app:lintStoreSafeDebug`.
- 2026-06-15: Runtime install was attempted with the built
  `personalFullSmsDebug` APK. `emulator -accel-check` reported that the Android
  Emulator hypervisor driver is not installed. A bounded hidden x86
  `AtharPixelQaApi35` software launch exposed only `emulator-5554 offline`
  before cleanup, and `AtharPixelQaApi35Arm` exited because arm64 system images
  are not supported by this x86_64 host. APK install, screenshot capture, UI
  dump, and logcat capture could not run. Evidence was written to
  `build/qa/private-audit-backlog-recommendation-emulator/`, and final cleanup
  left no emulator/qemu/adb/netsim process and no AVD lock files.
