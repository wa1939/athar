# Security policy

Athar handles personal financial data on-device. Security is a feature, not an afterthought.

## Reporting a vulnerability

**Do not file a public issue for security vulnerabilities.**

Email the maintainer at: `employee_experience@elm.sa` (PGP key on request).

Include:
- A description of the vulnerability and its impact
- Steps to reproduce (or a proof-of-concept)
- The affected version (Git commit SHA or release tag)
- Whether the issue is already known publicly

You'll get an acknowledgement within **3 business days** and a status update within **7 days**. We aim to ship a fix within **30 days** for high-severity issues.

## Threat model

Athar's threat model is:

| Threat | Mitigation |
|---|---|
| Stolen device (locked) | OS-level full-disk / file-based encryption + AndroidKeyStore-wrapped DB key. Without the device's unlock factor the DB is unreadable. |
| Stolen device (unlocked) | App-level biometric lock (planned — see [Backlog](Athar_Backlog.md) U-006). Until then, the OS lock is the only barrier. |
| Malicious app installed on the same device | Permissions: `RECEIVE_SMS`/`READ_SMS` are protected; SMS broadcasts use signature-level `BROADCAST_SMS` permission. NotificationListenerService requires user-granted access in system Settings. |
| Network MITM | None — Athar makes no network calls in v1 (ADR-002). The only data that leaves the device is the user-initiated AES-256-GCM backup file, which is encrypted before it touches any I/O stream. |
| Backup file leaked | The backup file is encrypted with AES-256-GCM keyed by a PBKDF2-HMAC-SHA256 derivation (600k iterations) of the user's passphrase. Without the passphrase the ciphertext is unrecoverable. |
| Compromised supply chain | All dependencies pinned in `gradle/libs.versions.toml`. Dependabot configured for security advisories. |

## Cryptographic primitives

- **Database encryption at rest:** SQLCipher 4.6.1 (AES-256-CBC with HMAC-SHA1). DB key is a 32-byte random value wrapped with an AES-256-GCM master key resident in `AndroidKeyStore` (alias `athar_master_v1`). See [ADR-003](docs/adr/ADR-003-encryption-at-rest.md).
- **Backup encryption:** AES-256-GCM with 12-byte random IV, 16-byte authentication tag. Key derived from the user's passphrase via PBKDF2-HMAC-SHA256 with 600,000 iterations (OWASP 2024 recommendation) and a 16-byte random salt.
- **Random:** `java.security.SecureRandom` (uses the device's CSPRNG).

## Build integrity

Beta releases are signed with a keystore generated specifically for this project. The keystore is **not** committed to the repository; the SHA-256 fingerprint of the signing certificate is published with each release tag so users can verify.

## Disclosure timeline

Once a fix is shipped, we publish a security advisory via GitHub's Security tab with:
- CVE number (if applicable)
- Affected versions
- Mitigation steps for users who can't update immediately
- Credit to the reporter (unless they prefer anonymity)
