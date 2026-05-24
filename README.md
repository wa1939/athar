<p align="center">
  <img src="docs/branding/athar-mark.png" alt="Athar" width="200" />
</p>

<h1 align="center">أثر · Athar</h1>

<p align="center">
  <strong>Trace every riyal you spend. Quietly. On your device. Without spreadsheets.</strong><br/>
  <em>تتبع أموالك. بوضوح.</em>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-blue.svg" alt="License"></a>
  <img src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/min%20SDK-28-3DDC84" alt="Min SDK 28">
  <img src="https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.1.0">
  <img src="https://img.shields.io/badge/Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose Material 3">
  <a href=".github/workflows/build.yml"><img src="https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white" alt="CI"></a>
  <img src="https://img.shields.io/badge/status-beta-orange" alt="status">
</p>

<p align="center">
  <a href="#how-it-works">How it works</a> ·
  <a href="#features">Features</a> ·
  <a href="#screenshots">Screenshots</a> ·
  <a href="#building">Building</a> ·
  <a href="#roadmap">Roadmap</a> ·
  <a href="#privacy">Privacy</a>
</p>

---

> **أثر** is a local-first Android app that reads your bank SMS, parses every transaction, auto-categorizes it against a Saudi merchant dictionary, and replaces a complex Excel budget workbook with three calm screens: **Today** · **Trends** · **Plan**. Arabic-first. SAR-native. Encrypted on-device. No cloud. No ads. No telemetry. Ever.

## Why

You spend in two languages, across multiple banks (Al Rajhi first), with bank SMS arriving in mixed Arabic/English within seconds of the transaction. The information is already in your pocket — it just isn't structured.

Existing tools force a trade-off:
- A spreadsheet that knows your categories but requires manual entry for every transaction
- A finance app like Mint/YNAB that ingests transactions but doesn't speak Saudi banking, Arabic merchant names, or SAR

**Athar bridges that gap**, locally and quietly.

## Features

#### Today (اليوم)
- ✅ One landmark net-flow number with **count-up animation**
- ✅ Pending tray for SMS-captured transactions awaiting your confirmation
- ✅ Swipe right to confirm · swipe left to dismiss
- ✅ FAB to add a manual transaction in seconds

#### Trends (النمط)
- ✅ Period selector: month · 3 months · year
- ✅ Top-8 category bar chart with **comparison-to-previous-period delta**
- ✅ **Tap a bar → 12-month drilldown** for that category
- ✅ Hijri date display optional

#### Plan (الخطة)
- ✅ **Budget targets** per category with variance pills (over/under)
- ✅ **Wishlist** with savings-capacity math (NOW / WAIT until YYYY-MM / INFEASIBLE)
- ✅ **Family investments pool** with proportional share % and return per contributor

#### Ingestion + categorization
- ✅ **Al Rajhi SMS** parser (Arabic + English) — purchase, transfer, deposit, balance alerts
- ✅ **Notification listener** path for Play-Store-safe distribution
- ✅ **90-day historical SMS backfill** on first install
- ✅ Rule engine with **45 Saudi merchant seed rules** (Starbucks, Panda, STC, Saudia…)
- ✅ "Always categorize X as Y?" **learn-from-correction** loop
- ✅ Source of every categorization is **explainable** (rule id, confidence)

#### Settings + ops
- ✅ **AES-256-GCM encrypted backup** (Argon2-equivalent KDF, passphrase-protected)
- ✅ **CSV import + export** for Excel interop
- ✅ **SQLCipher** database encryption at rest, key wrapped via Android Keystore
- ✅ **SMS audit log** — every parsed/failed/ignored SMS retained, never deleted
- ✅ **Activity log** — every transaction edit, with timestamp
- ✅ **Hijri date toggle**
- ✅ Categories management (rename, archive, reorder, custom adds)

## Screenshots

Captured from the `v0.1.0-beta.1` signed APK running on a Pixel 6 / Android 14 emulator.

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/01_onboarding.png" width="220"/><br/><sub>Onboarding · welcome</sub></td>
    <td align="center"><img src="docs/screenshots/02_onboarding2.png" width="220"/><br/><sub>Onboarding · privacy</sub></td>
    <td align="center"><img src="docs/screenshots/03_onboarding3.png" width="220"/><br/><sub>Onboarding · SMS perm</sub></td>
    <td align="center"><img src="docs/screenshots/04_today.png" width="220"/><br/><sub>Today · empty state</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/05_trends.png" width="220"/><br/><sub>Trends · empty</sub></td>
    <td align="center"><img src="docs/screenshots/06_plan_budget.png" width="220"/><br/><sub>Plan · budget targets</sub></td>
    <td align="center"><img src="docs/screenshots/07_settings.png" width="220"/><br/><sub>Settings</sub></td>
    <td align="center"><img src="docs/screenshots/09_settings_about2.png" width="220"/><br/><sub>About · credits</sub></td>
  </tr>
</table>

## How it works

```
┌────────────────────────────────────────────────────────────────────┐
│  SMS broadcast      Notification listener      Manual entry        │
│       │                       │                      │              │
│       └─────────┬─────────────┴──────────┬──────────┘              │
│                 ▼                        ▼                          │
│           RawIngestEvent           Transaction (CONFIRMED)          │
│                 │                        │                          │
│                 ▼                        │                          │
│    SmsIngestionPipeline                  │                          │
│      ┌─ audit log (PARSED/FAILED/IGNORED)│                          │
│      ├─ parser (Al Rajhi templates)      │                          │
│      ├─ categorizer (rules → classifier) │                          │
│      └─ persist (PENDING)                │                          │
│                 ▼                        ▼                          │
│         ╔══════════════════════════════════════╗                    │
│         ║  Encrypted Room DB (SQLCipher)       ║                    │
│         ║  key wrapped via AndroidKeyStore     ║                    │
│         ╚══════════════════════════════════════╝                    │
│                 │                                                   │
│        ┌────────┼────────┬──────────┐                              │
│        ▼        ▼        ▼          ▼                              │
│      Today    Trends   Plan       Settings                          │
└────────────────────────────────────────────────────────────────────┘
```

The architecture intentionally separates each ingestion source from the parse/categorize/persist pipeline so the distribution flavor (sideload vs Play-Store-eligible) is a config change, not a rewrite.

See [`docs/adr/`](docs/adr/) for the decision records:
- **ADR-001** — Stack: Kotlin 2.1 + Compose Material 3 + Hilt + Room + multi-module
- **ADR-002** — Local-first: no backend, no telemetry, ever
- **ADR-003** — Encryption at rest: SQLCipher + Keystore-wrapped DB key
- **ADR-004** — MVP status after 24 build waves

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.1.0 |
| UI | Jetpack Compose · Material 3 |
| DI | Hilt 2.54 |
| Persistence | Room 2.6 · **SQLCipher 4.6** (AES-256-GCM, AndroidKeyStore-wrapped key) |
| Async | Coroutines + Flow |
| Navigation | Compose Navigation 2.8 (single-activity, type-safe routes) |
| Charts | Custom Compose primitives (horizontal bar + monthly vertical bar) |
| Date/time | kotlinx-datetime · `java.time.chrono.HijrahDate` for Hijri |
| Money | `java.math.BigDecimal` — never `Float`/`Double` |
| Build | Gradle 8.10 · convention plugins via `build-logic/` included build · version catalog |
| Tests | JUnit 5 · Truth · Turbine · Paparazzi (scaffolded) · Maestro (10 flows) |
| Typography | [Thmanyah typeface](https://github.com/thmanyah/Thmanyah-Type) — sans + serif-display + serif-text |
| Logging | Timber (debug); no Crashlytics (privacy commitment) |

## Building

```bash
# 1. JDK 17 + Android SDK
#    Either install Android Studio (bundles both), or:
#      JDK:  https://adoptium.net/  (Temurin 17)
#      SDK:  Android command-line tools, then `sdkmanager "platforms;android-35" "build-tools;35.0.1"`

# 2. Point the build at your SDK
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 3. Bootstrap the Gradle wrapper (only first time)
gradle wrapper --gradle-version 8.10.2

# 4. Build
./gradlew :app:assemblePersonalFullSmsDebug

# 5. Install on a connected device
./gradlew :app:installPersonalFullSmsDebug
# or:
adb install app/build/outputs/apk/personalFullSms/debug/app-personalFullSms-debug.apk
```

Two flavors:

| Flavor | Permissions | Distribution path |
|---|---|---|
| **personalFullSms** | RECEIVE_SMS, READ_SMS | Sideload only — your own daily-driver build |
| **storeSafe** | No SMS perms | Play-Store-eligible — uses NotificationListenerService, manual entry, CSV import |

## Tests

```bash
./gradlew test                                   # all JVM unit tests (53 tests across 10 classes)
./gradlew :ml:categorizer:test                   # one module
./gradlew connectedAndroidTest                   # instrumented (needs emulator/device)
maestro test .maestro/flows/                     # 10 E2E flows
```

## Roadmap

**Shipped in v0.1.0-beta** (this build):

- ✅ Phase 0 foundations · 15 Gradle modules · build-logic convention plugins
- ✅ Excel parity for manual entry (M-01..M-13)
- ✅ Al Rajhi SMS parsing + categorization (S-01..S-09)
- ✅ Pending tray with swipe gestures (S-17..S-19)
- ✅ Hijri date toggle (P-07)
- ✅ SQLCipher encryption-at-rest with Keystore-wrapped key (ADR-003)
- ✅ AES-256-GCM JSON backup + restore
- ✅ CSV import + export
- ✅ Wishlist + Family Investments (Phase 4)
- ✅ Trends drilldown chart (M-07)
- ✅ SMS audit + Activity log (S-21, S-23, P-08)

**Deferred** (see [ADR-004](docs/adr/ADR-004-mvp-status.md) for why):

- ⏳ XLSX direct import (CSV path covers the migration today)
- ⏳ TFLite merchant classifier (rule engine handles ~90% of cases)
- ⏳ Locale toggle (Arabic-first per brief)
- ⏳ Paparazzi snapshot baselines (need to record on a real machine)
- ⏳ Macrobenchmarks (need a device)
- ⏳ Per-bank account routing (S-20)

## Privacy

Athar makes three commitments (Master Brief §2.2, enforced in ADR-002 + ADR-003):

1. **No cloud transit of financial data without your explicit action.** The only data that leaves the device is the AES-256-GCM backup file, which you save where you want.
2. **No ads, partnerships, affiliate links, telemetry, or third-party crash reporting.**
3. **No friction that makes you feel bad.** No streaks, no shame notifications, no scream red bars.

The DB is encrypted at rest with a 256-bit key resident in `AndroidKeyStore`. Without the device's secure hardware, the key is undecryptable. See [`SECURITY.md`](SECURITY.md).

## Contributing

PRs welcome. See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the workflow and the spec-first loop the codebase was built with. The single source of truth is [`Athar_Master_Brief.md`](Athar_Master_Brief.md) — please read it before opening a PR that adds a feature.

## License

[Apache License 2.0](LICENSE). Bank-template strings in `ingestion:sms-parser` are derived from observed SMS bodies (anonymized) and are released under the same terms.

## Credits

Developed by **Waleed Alhamed** — [walhamed.com](https://walhamed.com)

Inspired by the [**The Measure of a Plan (TMOAP)**](https://themeasureofaplan.com) budget tracking workbook, whose monthly cash-flow + savings-capacity discipline shaped Athar's Plan screen. Athar carries that spreadsheet's clarity into a phone that lives in your pocket.

---

<p align="center">
  <sub>Built one disciplined wave at a time. The Excel sheet retires when Athar ships its v1.0.</sub>
</p>
