# Getting started with Athar

Phase 0 is built and tested. The first APK is in `app/build/outputs/apk/personalFullSms/debug/`.

## Current state of this machine (as of 2026-05-24)

| Tool | Location | Source |
|---|---|---|
| **JDK 17.0.19** (Temurin) | `C:\Users\walghamdi\Android\jdk-17` | Installed by Claude |
| **Android SDK** (platforms 34/35/36, build-tools 35.0.1) | `C:\Users\walghamdi\Android\Sdk` | Pre-existing (cmdline-tools install) |
| **Gradle 8.10.2** | wrapper cache `~/.gradle/wrapper/dists/` | Auto-downloaded by `./gradlew` |
| **Android Studio** | not installed | Optional — only needed for the IDE + emulator |

## Daily build commands

From a bash shell at the repo root:

```bash
export JAVA_HOME="/c/Users/walghamdi/Android/jdk-17"
export ANDROID_HOME="/c/Users/walghamdi/Android/Sdk"
export PATH="$JAVA_HOME/bin:$PATH"

# build the personal-use APK (with SMS perms)
./gradlew :app:assemblePersonalFullSmsDebug

# or the Play-store-eligible variant
./gradlew :app:assembleStoreSafeDebug

# unit tests across all modules
./gradlew test

# just the pure-JVM modules (~30 seconds)
./gradlew :core:common:test :ml:categorizer:test :ingestion:sms-parser:test
```

To persist the environment across shell sessions, add the three `export` lines to your `~/.bashrc` or use direnv with a `.envrc` file (not committed; `.gitignore`d).

## Want the IDE + emulator?

Install Android Studio: https://developer.android.com/studio
It will reuse the existing SDK at `~/Android/Sdk`. Point Studio at JDK 17 in `File → Project Structure → SDK Location → Gradle Settings`.

To run on a real phone via `adb`:
```bash
adb install app/build/outputs/apk/personalFullSms/debug/app-personalFullSms-debug.apk
```

## Build artifacts (Phase 0)

| Artifact | Size | Variant |
|---|---|---|
| `app-personalFullSms-debug.apk` | 66 MB | Sideload — RECEIVE_SMS + READ_SMS |
| `app-storeSafe-debug.apk` | 66 MB | Play-Store-eligible — no SMS perms |

Both APKs install on Android 9+ (minSdk 28).

## Tests that pass today

| Test class | Module | # tests |
|---|---|---|
| `MoneyTest` | `core:common` | 6 |
| `NormalizeTest` | `ingestion:sms-parser` | 3 |
| `RuleEngineTest` | `ml:categorizer` | 6 |

Coverage will grow with each ticket per the Master Brief's 80% requirement.

## Verify the structure

```bash
./gradlew projects
```

Expected: 15 modules.

```
:app
:build-logic:convention            (included build)
:core:common  :core:data  :core:design-system  :core:domain  :core:testing
:feature:today  :feature:trends  :feature:plan  :feature:settings
:ingestion:sms-parser  :ingestion:sms-listener  :ingestion:notification-listener
:ml:categorizer  :ml:models
```

## What is NOT in this scaffold

These intentionally land in later tickets — do not add them in Phase 0:

| What | Where it lands | Why |
|---|---|---|
| SQLCipher database key | S-13 prep | Needs a Keystore-backed key gen flow + first-run UX |
| Real Al Rajhi parser templates | S-03..S-06 | Phase 2; corpus comes first (S-01) |
| TFLite classifier model | S-10..S-11 | After rule engine ships; trained offline |
| GitHub Actions CI | F-02 | Skipped until repo is on GitHub |
| Paparazzi snapshot baselines | F-06 | Needs first real `recordPaparazziDebug` run |
| Wishlist / Investments UI | Phase 4 (W-01..W-09) | Model + repo skeleton exists; UI later |
| App icon wordmark | TBD design | Placeholder "three ember dots" drawable shipped |

## Build issues resolved during the first build

A few problems surfaced and were fixed; left here so future bumps don't re-introduce them:

1. **`kotlinx.datetime.YearMonth` doesn't exist in 0.6.1** — use `java.time.YearMonth` until kotlinx-datetime 0.7+. Already done in `core:common`, `core:domain`, `feature:today`, `core:data` mappers.
2. **Hilt < 2.54 cannot read Kotlin 2.1.0 metadata** — pinned to 2.54 in the version catalog. Don't downgrade.
3. **`org.tensorflow:tensorflow-lite-support` uses a separate 0.4.x version line** — wire it in only when the classifier adapter lands (S-10).
4. **`Json.encodeToString(value)` overload-resolution conflict** — pass an explicit `ListSerializer(String.serializer())` for collections. Pattern documented in `core:data/.../OtherMappers.kt`.
5. **`RawIngestDispatcher` needs a Hilt binding** — stubbed in `app/src/main/.../di/IngestionModule.kt` (logging only); real queue impl ships in S-13.
6. **`BankPackageFilter` lives in the notification-listener module** — only compiled into storeSafe flavor. Binding sits in `app/src/storeSafe/...` so personalFullSms doesn't break.

## Reference

- `Athar_Master_Brief.md` — the brief. Edit before code.
- `Athar_Backlog.md` — ticket execution order.
- `CLAUDE.md` — guidance for Claude Code.
- `docs/adr/` — accepted decisions.
