# R-05 - Curated Seed Rules Arabic Public Labels

## Problem

The active seed already covered many English public merchant labels, but
aggregate-only private-corpus review still showed repeated Arabic public
platform labels landing uncategorized or matching older broad AI rules.

One existing unaccented Arabic Ejar platform phrase was especially weak: it was
seeded below the reviewed R-05 batch and pointed at side income, while the later
reviewed English `ejar` rule points at rent. That means Arabic Ejar rows could
miss the intended rent category even when the platform label was preserved by
the parser.

## Decision

Promote or correct only concrete public/common Arabic labels at priority 90:

- Rent: `منصة ايجار`, `منصة إيجار`
- Telecom: `موبايلي`
- Insurance: `جونشور`, `تأميني`

The unaccented `منصة ايجار` rule already existed, so this slice changes it to
`cat-rent` at priority 90 instead of adding a duplicate. The accented Ejar
variant is added separately because the rule engine does direct lowercase
substring matching and does not normalize Arabic hamza variants.

Aggregate-only review of the private SMS export showed these public labels were
repeated enough to be useful: Ejar platform variants, Mobily, Gonsure, and
Tameeni. No raw SMS bodies, private-person labels, account tails, or unclear
local fragments are committed or documented.

## Acceptance

- `seed_rules.json` increases from 686 to 690 active rules.
- The existing unaccented Arabic Ejar platform rule is corrected to `cat-rent`
  at priority 90.
- Every new pattern references an existing category id.
- No duplicate normalized seed patterns are introduced.
- The asset test pins every reviewed pattern at priority 90.
- The raw SMS export remains untracked and uncommitted.

## Non-goals

- Do not change the broad older `ايجار` AI rule in this slice.
- Do not add private-person, account-tail, or unclear local-fragment patterns.
- Do not add broad Arabic category words that could create false positives.
- Do not add cloud/user-data collection for categorization.

## Validation

- 2026-06-14: `:core:data:testDebugUnitTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: `git diff --check` passed.
- 2026-06-14: full JVM/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug` with JDK 17,
  `ANDROID_HOME=C:\Users\waok\Android\Sdk`, `--no-daemon`, and
  `--max-workers=1`.
- 2026-06-14: runtime install/launch was attempted. `adb devices -l` first
  returned no attached devices. `emulator.exe -accel-check` reported that the
  Android Emulator hypervisor driver is not installed. A bounded hidden
  `AtharPixelQaApi35` software launch with `-accel off -no-window -no-audio
  -no-snapshot -gpu swiftshader_indirect` exposed `emulator-5554` only as
  `offline`; ADB reconnect and additional polling did not reach `device`, so
  the APK could not be installed or launched. The emulator/qemu processes were
  stopped afterward, and `adb devices -l` returned no attached devices.
