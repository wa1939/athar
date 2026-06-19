# S-20 User Template Parse Preview

## Goal

Make the "Bank templates" flow safe enough for non-built-in banks. A user should not be able to save a template that cannot parse the sample SMS they pasted. This directly supports the roadmap goal of working across different banks without waiting for code changes.

## Inputs

- A sample SMS body.
- The sender label as shown on the device.
- Transaction type: purchase, transfer, or deposit.
- Anchor text before and optionally after the amount.
- Optional merchant and counterparty anchors.

## Behavior

Settings computes a live preview from the same anchor-matching code used by the runtime parser. The preview shows the amount Athar will read, plus merchant and counterparty when their optional anchors match. If the amount anchor is missing, or the anchor is found but no valid number follows it, the preview shows an inline error and the Save button remains disabled.

## Non-Goals

- No regex authoring UI.
- No AI-assisted template generation in this slice.
- No changes to built-in bank templates or sender allow-lists.

## Acceptance

- Runtime user-template parsing and Settings preview share one matcher implementation.
- Arabic and English samples with Arabic-Indic digits preview correctly.
- A malformed sample cannot be saved.
- Existing user-template parser tests still pass.
