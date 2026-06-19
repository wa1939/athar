# R-05 - Curated Seed Rules Private Audit Batch

## Problem

After R-12 preserved more merchant labels from the private family-device SMS export,
the remaining manual cleanup shifted from parser failures to categorization coverage.
An aggregate-only audit showed many uncategorized rows were private people, account
tails, or ambiguous local fragments that must not be shipped as public seed rules.
A smaller set were recognizable public services or merchant/platform names.

## Decision

Promote only concrete, reusable priority-90 patterns into `seed_rules.json`:

- Debt / services: `tabby`, `المخالفات المرورية`, `خدمات المقيمين`
- Groceries / gas: `farm supe`, `petrofas`, `fuelax`
- Travel / transport: `gathern`, `ryanair`, `wizz air`, `woqoof`
- Clothing / electronics / telecom: `giordano`, `saco`, `airalo`

The batch intentionally skips private-person strings, account-number fragments,
unclear local merchants, and broad words such as `market`, `station`, or `vehicle`.

## Acceptance

- `seed_rules.json` increases from 644 to 657 active rules.
- Every new pattern references an existing category id.
- No duplicate normalized seed patterns are introduced.
- The asset test pins every new rule at priority 90.
- The raw SMS export and temporary audit helper are not committed.

## Private Audit Result

Temporary local audit over the private export, using the R-12 parser state:

- Before this batch: 888 categorized parsed expenses, 915 uncategorized parsed
  expenses, 460 parsed expenses with missing merchants.
- After this batch: 947 categorized parsed expenses, 856 uncategorized parsed
  expenses, 460 parsed expenses with missing merchants.

Missing-merchant rows remain a parser-quality backlog, not a seed-rule problem.

## Validation

- 2026-06-13: `:core:data:testDebugUnitTest` passed with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: full JVM/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug` with JDK 17,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-13: runtime install/tap-through could not run because
  `adb devices -l` returned no attached devices. The SDK contains
  `AtharPixelQaApi35`, but `emulator -accel-check` reports that the Android
  Emulator hypervisor driver is not installed, and a bounded software-acceleration
  boot attempt did not expose an ADB target.
