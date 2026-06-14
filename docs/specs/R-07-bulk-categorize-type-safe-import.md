# R-07 - Bulk Categorize Type-Safe Import

## Problem

Bulk categorization CSVs are often filled by an external AI or a fast human pass.
The export now includes row-level `category_options`, but older CSVs and manual
edits can still put an income category on an expense row, an expense category on
an income row, or a category on an older transfer row. Before this slice, the
importer accepted any active category id/name and could also propagate one bad
category to blank peers in a repeated merchant group.

That is risky because transaction rows and learned merchant rules do not encode
category kind. A single mismatched import could make the ledger inconsistent and
train a local exact rule from mixed evidence.

## Decision

- Resolve the filled `category_id` to candidate categories, but apply only a
  candidate whose `CategoryKind` matches the matched transaction `TxType`.
- Treat transfers as incompatible with category import.
- Keep id, stable-key, source-aware, and content-fingerprint matching unchanged.
- Keep blank repeated-merchant propagation, but apply it only when the inherited
  category is compatible with the blank row's transaction type.
- Do not train an exact bulk-import rule for a merchant group that showed any
  incompatible transaction type during explicit import or group propagation.

## Acceptance

- An expense row filled with an income category is skipped and remains pending.
- An income row filled with an expense category is skipped and remains pending.
- Older transfer rows with a filled category are skipped.
- A repeated merchant group with an expense representative and a blank income
  peer updates only the compatible expense row, leaves the income row unchanged,
  and trains no exact rule for that mixed group.
- Existing successful expense and income imports still work.
- Category ids remain preferred, while English and Arabic names continue to work
  as fallbacks when they resolve to a compatible category.

## Non-goals

- Do not change the CSV headers or require a new export.
- Do not add inline AI APIs or API-key storage.
- Do not make global category rules type-aware in this slice.
- Do not promote private/imported merchant labels into shared seed rules.

## Validation

- `:core:data:testDebugUnitTest` covers explicit incompatible category skips,
  mixed-type group propagation safety, existing group propagation, exact import
  rule learning, stable import matching, and export category options.
- The full JVM test/build/lint stack passes with JDK 21 after one retry of an
  unrelated transient Android Lint FIR resolver crash:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime install/launch was attempted with the built `personalFullSmsDebug` APK.
  `emulator -accel-check` reported that the Android Emulator hypervisor driver is
  not installed. A bounded hidden `AtharPixelQaApi35` software launch reached
  only `emulator-5554 offline`, so install/screenshot/logcat capture could not
  run. Cleanup stopped the stuck qemu/ADB processes, removed AVD locks, and ended
  with no attached ADB device and no emulator/qemu/netsim process.
