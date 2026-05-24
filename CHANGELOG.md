# Changelog

All notable changes to Athar will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

- Nothing yet.

## [0.1.0-beta] — 2026-05-24

The first build that's actually usable as a daily driver. Built across 24 disciplined waves of spec → code → test → verify.

### Added

- **Foundations** — 15 Gradle modules with convention plugins, version catalog, included `build-logic` build. Multi-flavor: `personalFullSms` (sideload) + `storeSafe` (Play-Store-eligible).
- **Today screen** — landmark net-flow number with count-up animation, pending tray, FAB add manual transaction, recent feed with list animations.
- **Trends screen** — period selector (month/3m/year), comparison-to-previous arrow + delta %, top-8 category bar chart, **tap-to-drilldown 12-month sheet** for any category.
- **Plan screen** — segmented sub-tabs: Budget (target/actual/variance pills), Wishlist (savings-capacity math + NOW/WAIT/INFEASIBLE status), Family Investments (proportional share % + return).
- **Onboarding** — 3-page flow (welcome → privacy → SMS perm) with dot-indicator nav.
- **SMS ingestion** — BroadcastReceiver + NotificationListenerService, source-agnostic `RawIngestEvent` pipeline, 90-day historical backfill, idempotent re-delivery.
- **Al Rajhi parser** — Arabic + English templates for purchase / transfer-out / deposit / balance-alert, Arabic-Indic digit normalization, confidence scoring.
- **Categorizer** — 5-tier rule engine (exact → substring → regex → classifier → unknown), 45 Saudi merchant seed rules, learn-from-correction loop with priority-200 user rules.
- **Encryption at rest** — SQLCipher 4.6.1 with 256-bit DB key wrapped by an `AndroidKeyStore`-resident AES-256-GCM master key (ADR-003).
- **Backup + restore** — AES-256-GCM file format, PBKDF2-HMAC-SHA256 600k-iteration KDF, JSON snapshot of all tables, gzip-compressed.
- **CSV import + export** — RFC-4180-quoting parser, category matching by Arabic or English name, multi-format date parsing.
- **Settings** — SMS permission card, backfill trigger, encrypted backup buttons, CSV exchange, Hijri date toggle, categories CRUD (rename / archive / reorder), SMS audit log, Activity log.
- **SMS audit log** — every parsed/failed/ignored SMS retained forever, filterable, never deleted (§4.6 Master Brief promise).
- **Activity log** — every transaction CREATE/UPDATE/DELETE/CONFIRM/DISMISS auto-recorded with timestamp.
- **Hijri date toggle** — landmark caption gains "· 1447/11 هـ" alongside Gregorian when enabled.
- **Brand** — Athar wordmark adaptive icon, Thmanyah typeface (sans + serif-display + serif-text), brand gold accent (#B8893C) replacing the placeholder ember.
- **Tests** — 53 pure-JVM unit tests across `MoneyTest`, `PeriodShiftTest`, `HijriDateTest`, `AtharCryptoTest`, `NormalizeTest`, `AlRajhiTemplatesTest`, `RuleEngineTest`, `WishlistCalcTest`, `InvestmentsCalcTest`, `BudgetCalcTest`.
- **E2E** — 10 Maestro flows covering onboarding, add-tx, edit-with-learn, set-target, add-wishlist, toggle-Hijri, sms-to-pending, backfill, export, smoke-nav.
- **Documentation** — `README.md`, `GETTING_STARTED.md`, `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, four ADRs.

### Known limitations

See [ADR-004](docs/adr/ADR-004-mvp-status.md). Notably:

- XLSX direct import deferred (CSV path is the migration route today).
- TFLite merchant classifier deferred (rule engine covers ~90% of cases).
- Locale toggle deferred (Arabic-first per brief).
- Paparazzi snapshot baselines need a first record run.
- Macrobenchmarks need a real device.

[Unreleased]: https://github.com/wa1939/athar/compare/v0.1.0-beta...HEAD
[0.1.0-beta]: https://github.com/wa1939/athar/releases/tag/v0.1.0-beta
