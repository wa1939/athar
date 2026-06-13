# G-08 - Manual Entry Receipt Attachments

## Problem

Unsupported-bank users and cash-heavy users still enter some transactions manually. A receipt photo
is often the only proof or detail for warranty, reimbursement, tax, or family review. Athar should
support that proof without asking the user to upload financial images to a cloud service.

## Decision

Add one optional image receipt to the Add Transaction sheet.

- The sheet uses Android's system image picker, so Athar does not request broad photo-library
  permissions.
- The selected image is copied immediately into memory while the picker URI permission is still
  valid.
- On save, the image bytes are stored in a `transaction_receipt` Room table linked one-to-one to
  the created transaction.
- Receipt payloads are BLOBs in the SQLCipher database, so they inherit Athar's encrypted local
  at-rest storage.
- Encrypted `.athar` backup/restore includes receipt payloads as Base64 inside the gzipped,
  AES-GCM-encrypted snapshot.
- Maximum receipt size is 5 MB to avoid turning a finance ledger into an unbounded photo archive.

## Acceptance

- Manual add sheet can attach, replace, and remove a pending receipt before save.
- Non-image selections are rejected.
- Images over 5 MB are rejected.
- A saved manual transaction writes the receipt payload, MIME type, original display name, size,
  and created timestamp.
- Deleting the transaction cascades the receipt row.
- Encrypted backup export/import preserves receipt attachments.

## Out Of Scope

- OCR / amount extraction from receipts.
- Multiple receipts per transaction.
- Receipt viewer/export from the edit sheet.
