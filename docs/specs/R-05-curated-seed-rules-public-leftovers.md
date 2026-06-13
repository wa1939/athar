# R-05 - Curated Seed Rules Public Leftovers

## Problem

Athar's active categorizer seed has grown through private aggregate audits and
public catalog review, but a few still-safe public/common patterns remained only
in `seed_merchant_catalog.json` or in obvious public merchant spellings. These
are low-effort wins for first-run users because they reduce the number of
transactions that need manual category review after SMS backfill, notification
ingestion, or statement import.

The remaining catalog backlog is mostly not safe for a shared substring seed:
`pizza`, `hospital`, `clinic`, `pharmacy`, `gym`, `airport`, `metro`,
`university`, `roasting`, and similar words are too broad outside one user's
context.

## Decision

Promote eight priority-90 patterns:

- Groceries: `spar`
- Restaurant: `panificio`, `regoli`, `chili's`, `chilis`
- Subscriptions/software: `claude`
- Travel: `airside`
- Education: `harvard`

For Chili's, the broad catalog pattern `chili` stays excluded; only explicit
chain spellings are added.

## Acceptance

- `seed_rules.json` increases from 678 to 686 active rules.
- Every new pattern references an existing category id.
- No duplicate normalized seed patterns are introduced.
- The asset test pins every new rule at priority 90.
- The raw SMS export is not committed or printed into docs.

## Non-goals

- Do not add broad words such as `pizza`, `hospital`, `clinic`, `pharmacy`,
  `gym`, `airport`, `metro`, `university`, or `roasting`.
- Do not add private-person, account-tail, or unclear local-fragment patterns.
- Do not add cloud/user-data collection for categorization.

## Validation

- 2026-06-13: `:core:data:testDebugUnitTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: full JVM/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug` with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: runtime tap-through was not applicable to this seed-only slice.
  ADB initially showed a stale `emulator-5554` offline transport from local AVD
  attempts; after stopping the emulator/qemu process and restarting ADB,
  `adb devices -l` returned no attached devices. `emulator.exe -accel-check`
  still reports that the Android Emulator hypervisor driver is not installed.
