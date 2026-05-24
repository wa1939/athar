# ADR-002: Local-first architecture, no backend in v1

- **Status:** Accepted
- **Date:** 2026-05-24

## Context

Master Brief §5.3 commits to a local-first architecture: no backend in v1, no sync, encrypted SQLite as the only system of record, user-initiated encrypted export as the only escape hatch. §2.2 codifies this as the second of three "Athar will never" promises.

## Decision

- All persistence is local: Room over SQLite, encrypted at rest with SQLCipher, key derived from Android Keystore.
- No network calls except: (a) explicit user-initiated backup upload to a destination they choose (Drive, etc.), and (b) one-shot OTA model downloads if/when a TFLite classifier is shipped with a versioned URL.
- No analytics. No crash reporting to a third party. Crashes write to a local file the user can attach to a bug report manually.
- Backup format: zipped JSON of all tables + raw SMS audit, AES-256-GCM, Argon2id passphrase. Decryption tool published alongside the app so the user is never locked in.

## Consequences

- Multi-device sync becomes a future feature, not a v1 fix. If/when added, it must be opt-in and end-to-end encrypted.
- Customer support cannot fetch logs; users must volunteer them.
- Restore flow becomes a P0 testing target — corrupt or unrecoverable backups violate the privacy promise.
- The `ingestion/` source layer is interchangeable (SMS, notifications, future Open Banking adapter) so distribution flavors do not require core rewrites.

## Alternatives considered and rejected

- Supabase / Firebase backend — fastest path to multi-device, but breaks §2.2.
- Plain (unencrypted) SQLite — acceptable for debug, unacceptable for release. SQLCipher is non-negotiable for v1 release.
