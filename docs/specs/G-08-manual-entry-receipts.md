# G-08 - Manual Entry Receipt Attachments

## Problem

Unsupported-bank users and cash-heavy users still enter some transactions manually. A receipt photo
is often the only proof or detail for warranty, reimbursement, tax, or family review. Athar should
support that proof without asking the user to upload financial images to a cloud service.

## Decision

Add one optional image receipt to the Add Transaction sheet. The follow-up
multiple-receipts slice generalizes the same encrypted table and UI to several
images per transaction without changing the privacy model.

- The sheet uses Android's system image picker, so Athar does not request broad photo-library
  permissions.
- The selected image is copied immediately into memory while the picker URI permission is still
  valid.
- On save, the image bytes are stored in a `transaction_receipt` Room table linked to
  the created transaction.
- Receipt payloads are BLOBs in the SQLCipher database, so they inherit Athar's encrypted local
  at-rest storage.
- Encrypted `.athar` backup/restore includes receipt payloads as Base64 inside the gzipped,
  AES-GCM-encrypted snapshot.
- Maximum receipt size is 5 MB to avoid turning a finance ledger into an unbounded photo archive.

Follow-up receipt polish exposes saved receipts from the existing transaction edit sheet:

- The edit sheet loads receipt metadata without loading the BLOB payload.
- Users can preview a saved image receipt in-app.
- Users can export a saved copy through Android's system document picker.
- Users can remove the receipt without deleting the transaction.

## Acceptance

- Manual add sheet can attach, replace, and remove a pending receipt before save.
- Non-image selections are rejected.
- Images over 5 MB are rejected.
- A saved manual transaction writes the receipt payload, MIME type, original display name, size,
  and created timestamp.
- Deleting the transaction cascades the receipt row.
- Encrypted backup export/import preserves receipt attachments.
- Editing a transaction with a receipt shows the original name and size.
- Viewing the receipt loads the encrypted payload only on demand and renders it read-only.
- Exporting writes the stored payload to a user-chosen document destination.
- Removing the receipt clears only the receipt row, not the transaction.

## Out Of Scope

- OCR / amount extraction from receipts.
- Multiple receipts per transaction in the first receipt slice; see
  [G-08 multiple receipts](G-08-multiple-receipts.md) for the shipped follow-up.
