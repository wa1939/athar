# R-05 - Curated Seed Rules Private Follow-up

## Problem

After R-18, the private SMS audit had zero parser failures, zero failed
known-bank messages, and zero parsed expenses with missing merchants. The
remaining measurable backlog is categorization coverage: repeated parsed
expense merchants can still land in Pending when no active seed rule matches.

The top uncategorized groups are visible only as hashes in the committed audit
report. A local-only scratch review can identify whether any repeated group is
a reusable public merchant/service label, but most high-count leftovers are
private people, account-like fragments, or ambiguous local abbreviations that
must not be shipped as public seeds.

## Decision

Promote only three priority-90 patterns that are public/reusable and narrow
enough for substring matching:

- Gas: `liter m`
- Gas: `albayan station`
- Medical: `united pharmacies`

The `liter m` pattern intentionally keeps the station-code prefix instead of
adding a broad `liter` rule, which would overmatch ordinary unit or unrelated
merchant text.

## Acceptance

- `seed_rules.json` increases from 706 to 709 active rules.
- Every new pattern references an existing category id.
- No duplicate normalized seed patterns are introduced.
- The asset test pins every new rule at priority 90.
- The private audit test still writes only aggregate redacted output with
  `rawBodiesWritten = 0`.
- The raw SMS export and local scratch analysis are not committed.

## Private Audit Result

The local audit over `All Conversations 2026-05-27 175517.txt` before this
slice showed:

- Records inspected: 7,887.
- Parser successes: 3,864.
- Ignored messages: 4,023.
- Failed messages: 0.
- Failed known-bank messages: 0.
- Parsed expenses: 2,374.
- Categorized parsed expenses: 1,262.
- Uncategorized parsed expenses: 1,112.
- Parsed expense rows with missing merchants: 0.
- Raw bodies written to audit output: 0.

After adding the three public/reusable seed rules, the redacted report showed:

- Records inspected: 7,887.
- Parser successes: 3,864.
- Ignored messages: 4,023.
- Failed messages: 0.
- Failed known-bank messages: 0.
- Parsed expenses: 2,374.
- Categorized parsed expenses: increased from 1,262 to 1,297.
- Uncategorized parsed expenses: reduced from 1,112 to 1,077.
- Parsed expense rows with missing merchants: 0.
- Raw bodies written to audit output: 0.
- Remaining inactive public-catalog matches: 0.

## Non-goals

- Do not add private-person, account-tail, sender, or raw SMS-body values to
  public seed files.
- Do not add broad words such as `liter`, `station`, `pharmacy`, `restaurant`,
  `market`, or `cafe` as shared substring rules.
- Do not add cloud/user-data collection for categorization.

## Validation

- 2026-06-14: gated private SMS export audit passed with JDK 21,
  `--rerun-tasks`, `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: `:core:data:testDebugUnitTest` passed with JDK 21,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: full JVM test/build/lint stack passed with JDK 21:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon
  --max-workers=1`.
- 2026-06-14: runtime install/launch was attempted with the built
  `personalFullSmsDebug` APK. `emulator -accel-check` reported that the Android
  Emulator hypervisor driver is not installed. Two bounded hidden software
  launches of `AtharPixelQaApi35` (`-gpu swiftshader_indirect -accel off` and
  `-gpu off -accel off`) emitted emulator startup logs but never exposed an
  online ADB device, so APK install, launch, screenshot, and logcat capture
  could not run. Cleanup finished with no attached ADB device, no
  emulator/qemu/netsim process, and no AVD lock files.
