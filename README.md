# Athar public merchant catalog

This is Athar's separate, data-only merchant-library distribution branch. It is not an app release, an APK update feed, a transaction database or an AI model. Client integration is currently unreleased; publishing this catalog does not update installed apps.

## Contents and privacy

`catalog-v1.json` contains a signed whole-catalog envelope. Version 1 has 58 public merchant-name patterns across 13 expense-category defaults: 54 selected mappings from Athar's already-public bundled dictionary and four maintainer-reviewed Arabic aliases supported by official business sources. This is an initial curated overlay, not a complete merchant database or a claim that every purchase at a retailer belongs to one category. Personal category choices remain authoritative; the bundled dictionary remains the fallback for other merchants.

Entries contain only `pattern`, `patternType`, and `categoryId`. They contain no transaction amounts, messages, dates, account/card identifiers, user identities or personal category history. All entries are public business names. Contributions remain deliberate merchant-only proposals and maintainer review; there are no automatic submissions or per-merchant queries.

The planned full-SMS client's download is opt-in, disabled by default, and requests this same static file for every user. The host/CDN can still see IP address and request timing. A disabled update switch does not remove Android's install-time INTERNET permission; downloaded, verified public knowledge can remain usable offline. StoreSafe remains without INTERNET. Financial information is not sent to this endpoint.

## Signature and client requirements

- Envelope fields: `keyId`, `payload` (standard Base64), `signature` (standard Base64).
- Signed bytes: ASCII `ATHAR-MERCHANT-CATALOG/v1\n` followed by the **exact decoded payload bytes**. Do not re-serialize JSON before verification.
- Signature: SHA-256 with ECDSA P-256, standard ASN.1 DER format, not raw/P1363.
- Key identifier: `p256-3eee06efb538aeb5`. Clients must use the trusted public anchor embedded in their app; a key downloaded with a catalog is not a trust anchor.
- Signed payload fields: `schemaVersion`, `catalogVersion`, `issuedAt`, `entries`. Current schema/version: 1/1. Future data updates must increment catalogVersion.
- Fail closed on invalid signature/schema, excess bounds, unsafe patterns or rollback. Keep the last verified catalog. Unknown/archived/incompatible local categories are skipped, never created.
- Only exact/scoped-literal patterns are allowed. No remote regex, executable code, financial-event rules or URLs in entries.
- Respect personal choices before local learning, this catalog and the bundled fallback. Downloaded suggestions must not determine amount, account, ownership, event kind or posting status, and must not train the engine on its own outputs.

Application updates still use Obtainium/GitHub releases, not this branch. Key rotation or revocation requires a trusted application update. A compromised signing key can damage recommendations, including cached knowledge offline; reset/disable remains available, and a signed catalog cannot authorize its own replacement trust key.

## Provenance and license

Derived from the public Athar merchant mapping asset at commit `65c4ad4bbfea8e4f470e018a0b754d372ad00e89`, filtered to an explicit public-business allowlist and augmented with four reviewed public Arabic aliases. Official sources for those additions:

- https://albaik.com
- https://www.panda.com.sa/ar/about-us
- https://www.othaimmarkets.com/ar
- https://www.flynas.com/ar/about-us

Modified data-only distribution, copyright 2026 The Athar Authors. Apache License 2.0; see LICENSE and NOTICE. Business names describe merchants and do not imply affiliation or endorsement.
