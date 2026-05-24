# ADR-003: SQLCipher encryption-at-rest + Keystore-wrapped DB key

- **Status:** Accepted
- **Date:** 2026-05-24
- **Related:** ADR-002 (local-first)

## Context

Master Brief §2.2 promises "your financial data stays on the device, encrypted." §5.1 specifies SQLCipher. Until Wave 13, Phase 0..Wave 12 shipped with plain Room/SQLite — `DataModule.provideDatabase` had a `TODO(SQLCipher)` note. We must close that gap before any release build leaves a developer machine.

## Decision

1. **Cipher:** SQLCipher 4.6.1 via `net.zetetic:sqlcipher-android`.
2. **Key material:** a freshly-generated 32-byte random DB key (256-bit), unique per install. Not derived from a user passphrase — the user already has biometric/PIN protection at the OS level for the device, and the brief explicitly opts out of an at-launch password prompt (§4.8 "no spinners > 1 second" implies no friction-laden unlock screen).
3. **Key storage:** the raw 32-byte DB key is wrapped with an AES-256-GCM master key resident in `AndroidKeyStore` (alias `athar_master_v1`). The wrapped key sits in `files/db_key.bin`. AndroidKeyStore guarantees the master key never leaves secure hardware on devices with a TEE/StrongBox, and even on devices without one, the OS prevents userland code from extracting the bytes.
4. **First-run flow:** if `db_key.bin` is missing, `DbKeyManager` generates a new DB key, wraps it, persists it. On subsequent launches it unwraps.
5. **Pre-encryption migration:** Wave 1..Wave 12 dev installs have a plain-SQLite `athar.db` file. Wave 13 cannot decrypt it (it isn't encrypted). On first encrypted launch we detect this (db file exists, key file missing) and delete the plain DB. This is destructive but acceptable for an alpha codebase where no real user data lives. A `PRAGMA cipher_migrate` path lands before public alpha.

## Consequences

- **OS lockout = data loss.** Reset of the AndroidKeyStore (factory reset, "clear data" on system settings, or some OEM-specific operations) destroys the master key. The wrapped DB key becomes undecryptable, and SQLCipher can no longer open the database. This is the same threat model as iOS Keychain-backed apps — the brief's §2.2 "your data, on your device" promise inherently couples data lifetime to OS-managed keys. The encrypted JSON backup (M-13) is the escape hatch.
- **Compatibility:** AndroidKeyStore GCM mode requires API 23+. minSdk is 28, so no issue.
- **Performance:** SQLCipher adds ~10-15% query overhead and ~10MB native lib to the APK. Acceptable given the security floor we gain.
- **Wrapped-key file format:** `[iv 12B] [ciphertext 32B + GCM tag 16B]` = 60 bytes total. Versioned implicitly by `MASTER_ALIAS` — incrementing the alias to `athar_master_v2` would let us rotate the master key in a future migration without losing the DB key.

## Alternatives considered and rejected

- **Passphrase prompt at every launch** — friction violates §4.8; encryption is for "at rest" protection from a stolen device, not for active-app threat model.
- **EncryptedSharedPreferences from `androidx.security:security-crypto`** — convenient but the library has been in alpha for years and has known issues across OEMs. The hand-rolled AndroidKeyStore wrap/unwrap is ~70 lines and we own the failure modes.
- **Argon2id-derived key from a user passphrase** — heavier dependency, more friction, and the threat model doesn't justify it (see "passphrase prompt" above).
- **No encryption at rest, rely on full-disk encryption** — Android's FBE/FDE protects when the device is off, but the brief explicitly promises app-level encryption.
