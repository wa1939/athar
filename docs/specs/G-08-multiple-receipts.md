# G-08 - Multiple Receipts Per Transaction

## Problem

The first receipt slices made Athar useful for proof capture, but the storage
and edit UI still assume one image per transaction. Real reimbursement, tax,
warranty, and family-review workflows often need more than one proof image:
itemized receipt plus card slip, front/back of an invoice, or multiple purchase
photos for one ledger row.

The current one-to-one model forces the user to replace the existing receipt or
split one real transaction into several ledger rows, both of which weaken the
ledger as a TMOAP replacement.

## Decision

Keep the same local-first encrypted receipt model, but make the relationship
one transaction to many receipt attachments:

- Remove the unique `transaction_receipt(transactionId)` constraint in a v7
  Room migration and keep a non-unique lookup index.
- Repository APIs expose lists of metadata, load one receipt by id, and delete
  one receipt by id while retaining compatibility helpers for legacy callers.
- Add Transaction can attach several pending image receipts before save, each
  capped at 5 MB, and writes all of them after the transaction is created.
- Edit Transaction lists every saved receipt and lets the user view, export, or
  remove each one independently.
- Encrypted backup/export already serializes a list of receipt attachments; the
  restore path should preserve multiple rows for the same transaction.

## Acceptance

- Existing single-receipt databases migrate to v7 without data loss.
- New databases create `transaction_receipt` without a unique transaction index.
- Saving a manual transaction can persist multiple pending receipt images.
- Editing a transaction can show, view, export, and delete individual receipts.
- Deleting a transaction still cascades all its receipts.
- Backup roundtrip preserves multiple receipt rows for one transaction.
- Focused repository/view-model tests cover multiple receipts and per-id delete.

## Non-goals

- OCR or amount extraction from receipt images.
- Non-image attachments.
- Cloud upload, telemetry, or cross-device sync.
- A bulk receipt gallery outside the transaction edit sheet.

## Validation

- 2026-06-14: focused compile passed with JDK 17:
  `:core:data:compileDebugKotlin :feature:today:compileDebugKotlin`.
- 2026-06-14: focused unit tests passed:
  `:core:data:testDebugUnitTest :feature:today:testDebugUnitTest`.
- 2026-06-14: `git diff --check` passed.
- 2026-06-14: full JVM/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug` with JDK 17,
  `ANDROID_HOME=C:\Users\waok\Android\Sdk`, `--no-daemon`, and
  `--max-workers=1`.
- 2026-06-14: runtime install/launch was attempted. `adb devices -l` first
  returned no attached devices, and `emulator.exe -accel-check` reported that
  the Android Emulator hypervisor driver is not installed. A hidden
  `AtharPixelQaApi35` launch with `-accel off -no-window -no-audio -no-snapshot
  -gpu swiftshader_indirect` emitted boot logs but exited before exposing an
  ADB device. `AtharPixelQaApi35Arm` exited with `Avd's CPU Architecture
  'arm64' is not supported by the QEMU2 emulator on x86_64 host`. No APK could
  be installed or launched on this PC.
