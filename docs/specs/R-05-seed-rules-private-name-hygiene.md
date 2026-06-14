# R-05 - Seed Rules Private Name Hygiene

## Problem

Athar's public seed file still carried legacy low-priority side-income rules
from the early AI-expanded corpus. Several of those rules were person-name-like
or ambiguous local fragments. They may improve one maintainer's private import,
but they are wrong for a shipped, local-first catalog: private counterparties
should become local exact rules through bulk categorize, repeated-history
learning, or an explicit "Always categorize" action, not public substring seeds
for every user.

The current redacted private audit already has 0 parser failures, 0 failed
known-bank messages, and 0 parsed expense rows with missing merchants. The
remaining backlog is mostly repeated private/local uncategorized merchants, so
shipping more private-looking seed labels would weaken the privacy model.

## Decision

Remove 51 legacy `cat-side-income` seed rules that are either private-person
labels or ambiguous local fragments. Keep public/institution-backed or generic
income labels such as platform, insurance, company, capital, account, profit,
cashback, ministry, and public Arabic company labels.

Add an asset-level guard that requires low-priority `cat-side-income` seeds to
contain a public/institution cue. This prevents the same class of private
counterparty rule from returning through future AI or community seed batches.

## Acceptance

- `seed_rules.json` decreases from 718 to 667 active rules.
- The seed asset still references only existing category IDs.
- No duplicate normalized seed patterns are introduced.
- The asset test rejects low-priority side-income rules without public or
  institution-backed cues.
- The private SMS audit still writes only redacted aggregate output with
  `rawBodiesWritten = 0`.
- Raw SMS exports and private scratch analysis are not committed.

## Audit Result

The forced gated audit over the local private export after the cleanup showed:

- Records inspected: 7,887.
- Parser successes: 3,864.
- Ignored messages: 4,023.
- Failed messages: 0.
- Failed known-bank messages: 0.
- Parsed expenses: 2,374.
- Categorized parsed expenses: 1,276.
- Uncategorized parsed expenses: 1,098.
- Parsed expense rows with missing merchants: 0.
- Raw bodies written to audit output: 0.
- Remaining inactive public-catalog matches: 0.

The lower categorized count is expected: private/local income counterparties now
belong to user-local learned rules instead of the public seed.

## Non-goals

- Do not remove public merchant, institution, platform, cashback, profit, or
  company-like income labels in this slice.
- Do not commit raw private SMS bodies, private merchant samples, account tails,
  phone numbers, or local scratch files.
- Do not replace local learning or bulk categorize with a centralized backend.

## Validation

- 2026-06-14: `:core:data:testDebugUnitTest` passed with JDK 21,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: gated private SMS export audit passed with JDK 21,
  `--rerun-tasks`, `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: full JVM test/build/lint stack passed with JDK 21:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon
  --max-workers=1`.
- 2026-06-14: runtime install/launch was attempted with the built
  `personalFullSmsDebug` APK. `emulator -accel-check` reported that the Android
  Emulator hypervisor driver is not installed, and the bounded hidden
  `AtharPixelQaApi35` software launch did not expose a boot-complete online ADB
  device. Install, launch, screenshot, UI dump, and logcat capture could not
  run. Evidence was written under
  `build/qa/seed-rules-private-name-hygiene-emulator/`, and final cleanup had no
  attached ADB device, no qemu/emulator/netsim process, and no AVD lock files.
