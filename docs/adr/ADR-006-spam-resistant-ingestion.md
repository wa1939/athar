# ADR-006 — Spam-resistant SMS ingestion (a sustainable defence against ads, OTPs, and promos)

**Status:** Accepted, 2026-05-25
**Context window:** v0.1.0-beta.3

## Problem

Athar's first beta ingested any SMS body that *looked* like a transaction — promotional shortcodes ("Earn 10,000 SAR cashback!"), OTP messages ("FoodicsOTP: 179996"), and bank-marketing affiliates ("AlRajhiB-AD") were all silently added to the pending tray. New users had no defence: their tray filled with 800+ spurious entries before they reached real transactions. That's the headache the user reported, and it's the headache every Saudi user will hit on day one if the architecture doesn't change.

## Design principles

The fix has to work **without per-user training** — a new user installing Athar at minute zero must not see promos in the pending tray. We rely on structural signals already present in Saudi SMS conventions:

1. **The `-AD` suffix is law.** CITC (Saudi telecom regulator) requires marketing senders to register shortcodes with an `-AD` / `-Ad` suffix. `AlRajhiB-AD`, `eXtra-AD`, `JARIR-AD` are by definition advertising channels, even when the prefix looks like a bank. **Any sender ending in `-AD` is hard-blocked, no exceptions.**
2. **Banks use a fixed sender alphanumeric ID.** `AlRajhiBank`, `STC Bank`, `D360 Bank`, `barq app` — these strings are stable across users. We maintain an explicit allow-list in `BankSenders.builtIn`. Anything not in the list is `Ignored`, period.
3. **Loyalty programs are not banks.** `mokafaa` (Al Rajhi rewards) sends Shukrans/points/BOGO offers and looks bank-ish enough to fool a naive matcher. It lives in `hardBlocked`, alongside `FoodicsOTP` and similar.
4. **Banks themselves send marketing.** AlRajhiBank's own sender ID emits "Tasaheal installments" and "personal financing" pitches. We can't blocklist the sender, so we blocklist the *content*: `GlobalBankIgnoreTemplate` carries ~40 regexes for offer-language patterns (`Earn X cashback`, `Buy 1 Get 1`, `Tasaheal`, `جوائز`, `موافقة فورية`, …).

## Layered filter pipeline

Every SMS event passes through this gauntlet, in order:

```
┌─────────────────────────────────────────────────────────────────┐
│  Layer 1: Sender allow-list (BankSenders.isKnown)               │
│    • Sender in builtIn?  → continue                              │
│    • Sender ends in -AD? → Ignored                               │
│    • Sender in hardBlocked? → Ignored                            │
│    • Otherwise → Ignored                                         │
└─────────────────────────────────────────────────────────────────┘
                            │ pass
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  Layer 2: GlobalBankIgnoreTemplate (content-based)              │
│    • OTP / verification code? → Ignored                          │
│    • Beneficiary added/activated? → Ignored                      │
│    • System maintenance? → Ignored                               │
│    • Marketing language (Earn/Win/Tasaheal/Shukrans/jawaiz)?    │
│                                                  → Ignored      │
└─────────────────────────────────────────────────────────────────┘
                            │ pass
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  Layer 3: Bank-specific templates (real corpus formats)         │
│    AlRajhiOnlinePurchaseReal / AlRajhiPosPurchaseReal / …       │
│    StcBankIncomingTransfer / D360OnlinePurchase / BarqXxx       │
│    → Success (confidence 0.9-0.95)                              │
└─────────────────────────────────────────────────────────────────┘
                            │ no template matched
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  Layer 4: User-defined templates (Settings → User Templates)    │
│    → Success (confidence 0.6-0.8)                               │
└─────────────────────────────────────────────────────────────────┘
                            │ still no match
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  Layer 5: UniversalAmountTemplate (RESTRICTED to known banks)   │
│    Last-resort capture for new bank message shapes.             │
│    → Success (confidence ≤ 0.55) — user always confirms.        │
└─────────────────────────────────────────────────────────────────┘
                            │ still no match
                            ▼
                       Failed (audit log only,
                       never shown as pending)
```

## Sustainability for future users

A user installing Athar tomorrow gets:

| What they get for free | Mechanism |
|---|---|
| Promo SMS ignored from day one | Sender allow-list + `-AD` suffix block |
| OTPs ignored from day one | `GlobalBankIgnoreTemplate` content patterns |
| Loyalty/Shukrans/jawaiz spam blocked | Content patterns (Arabic + English) |
| ~300 well-known merchants auto-categorized | `seed_merchant_catalog.json` |
| New bank format → user defines once | Settings → User Templates |
| Wrong category → fix once, learns globally | CategoryRule learning (existing in `CategoryRuleRepository.learnFromCorrection`) |

If a future user discovers a new spam pattern, the fix is one regex line in `IgnorePatterns.kt`. If a new bank arrives, one entry in `BankSenders.builtIn` plus a template file. **The work is centralized: every user benefits from every contribution.**

## What we deliberately did NOT do

- **No cloud telemetry.** We don't ship merchant→category corrections to a backend. Each user's CategoryRule learning stays local. The merchant catalog updates ship in the APK, not over the network. (Master Brief §2.2: "Sell, share, or transmit financial data without explicit user action.")
- **No ML-based spam classifier.** A regex+allow-list pipeline is auditable: a user can read `IgnorePatterns.kt` and predict what gets filtered. An ML model would be opaque and unfixable. We may add an on-device ML *transaction parser* later (ADR-005) but not for spam filtering.
- **No "auto-confirm" on high-confidence parses.** Even at 95% confidence, every transaction lands in the pending tray for one-tap confirmation. This is Master Brief §2.2 verbatim: "Never auto-confirm a transaction."

## How a new bank's format gets added

The path of least resistance:

1. User receives an SMS Athar doesn't parse — it shows in "Failed" in the audit log.
2. User opens Settings → User Templates → paste sample → mark anchors around amount/merchant → save.
3. Next message from that sender parses correctly.
4. If many users hit the same bank, we promote that template to `builtIn` in the next release (manual review).

## How a new spam pattern gets blocked

1. User sees a promotional SMS that slipped through. They report it (or future versions: tap "This is spam" on the pending entry).
2. We extract the unique phrase (`"Pay over 24 months"`, `"5X Shukrans"`, etc.) and add a regex to `IgnorePatterns.kt`.
3. Every user gets the fix in the next release.

## Tests that pin these guarantees

`ingestion/sms-parser/src/test/kotlin/com/athar/ingestion/smsparser/SmsCorpusTest.kt`:
- `any sender ending in -AD is ignored regardless of body`
- `mokafaa loyalty program is blocked even though it talks about money`
- `FoodicsOTP and similar OTP-only senders are blocked`
- `Tasaheal installment marketing from real bank sender is ignored`
- `prize contest with monetary value is ignored`
- `Arabic personal financing offer is ignored`
- `unknown sender with money figure is Ignored — NOT a transaction`

These tests are the contract. If a future change reintroduces the bug, the test goes red.

## Trade-offs

- **False negatives.** A bank could send a legitimate message we mis-classify as marketing (e.g., a real "Your loan was approved, instalment SAR 1000" notification). The user sees it in the audit log as Ignored. They can ask us to relax the pattern, or add a User Template to capture it. We bias toward over-filtering because a missed transaction is annoying; a false transaction is destructive (it skews the budget).
- **Allow-list rot.** New banks open every year. The list needs maintenance. The risk is manageable because (a) Saudi banking is highly concentrated — 15 commercial banks, 4 digital — and (b) users can add unknown senders via User Templates without waiting for a release.
- **`-AD` suffix isn't universal.** Some marketing senders don't use the suffix (e.g., `mokafaa`). That's why we have a separate `hardBlocked` list. We keep both layers and expand `hardBlocked` as we observe new patterns.
