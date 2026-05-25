# ADR-007: Localization architecture (per-app language + Compose layout direction)

- **Status:** Accepted
- **Date:** 2026-05-25
- **Supersedes:** —
- **Superseded by:** —

## Context

Athar is Arabic-first (Master Brief §2). v1 shipped with Arabic literals scattered across ~280 `AtharText(text = "...")` calls. To go global (per `docs/ROADMAP_GLOBAL.md` G-5), the app needs:

1. A per-app language toggle (not relying on the device's system locale alone — Saudi users in the US often run Arabic apps on English phones, and vice-versa).
2. Both visible text AND layout direction (RTL/LTR) must flip together when the locale changes.
3. ViewModels must remain Context-free to keep unit tests pure-Kotlin.
4. Architecture must extend naturally to French, Hindi, Urdu, Turkish later without rework.

Naïve approaches we considered and rejected:

- **AppCompat's `AppCompatDelegate.setApplicationLocales()`** — pulls in the AppCompat dependency just for the locale API. Athar is Material-3-only and uses Compose end-to-end; adding AppCompat for one API is bloat.
- **Per-language string maps in Kotlin objects** — testable but reinvents `Resources.getString`, loses per-screen-density and pseudo-locale support, and fights `Locale.getDefault()` formatting helpers.
- **Inject `@ApplicationContext` into every ViewModel** — testable only with Robolectric or Hilt test fakes. Adds Context coupling to logic that should stay platform-free.

## Decision

Five-layer architecture, each layer addresses a concrete gap that surfaces if you only do the previous layers:

### 1. Locale picker UI + persistence
- `SettingsScreen` exposes a 3-option language card: Follow system / Arabic / English.
- `UserPreferencesRepository.setAppLocale(tag)` persists the BCP-47 tag (`""` = follow system, `"ar"`, `"en"`) in DataStore.
- A SharedPreferences mirror runs alongside DataStore so `LocaleHelper.wrap()` can read the tag *synchronously* during `attachBaseContext` (before any coroutine can run).

### 2. Configuration override at activity entry
- `MainActivity.attachBaseContext(newBase)` calls `LocaleHelper.wrap(newBase)` which:
  - Reads the persisted tag.
  - Creates a `Locale.forLanguageTag(tag)`, calls `Locale.setDefault(locale)` (so `Locale.getDefault()` reads correctly everywhere later).
  - Builds a fresh `Configuration` with both `setLocale(locale)` **and** `setLayoutDirection(locale)`.
  - Returns `base.createConfigurationContext(config)`.
- The explicit `setLayoutDirection(locale)` matters: on API 28+, programmatic `setLocale()` does not propagate layoutDirection on its own. We learned this the hard way when text rendered in English but the dot-indicator stayed on the right.

### 3. Compose LocalLayoutDirection provider
- Even with the Configuration override, Compose reads layout direction from a `CompositionLocal`, not the wrapped Configuration. Without this layer, English text renders correctly but the FAB stays on the bottom-left (RTL anchor).
- `AtharApp` wraps everything in `CompositionLocalProvider(LocalLayoutDirection provides if (Locale.getDefault().language == "ar") Rtl else Ltr)` alongside the existing `LocalHijriEnabled` / `LocalDisplayCurrency` providers.

### 4. String extraction
- ~300 strings across `app/`, `core/design-system/`, `feature/today/`, `feature/trends/`, `feature/plan/`, `feature/settings/`.
- Each module owns its own `res/values/strings.xml` (Arabic default — primary language) + `res/values-en/strings.xml` (English overrides).
- Helper functions called from `@Composable` scopes (`accountTypeLabel`, `cadenceLabel`, `typeLabel`, `categoryLabel`, `periodLabel`, `statusLabel`) become `@Composable` so they can call `stringResource()`. This is fine because all call sites are inside composables.

### 5. ViewModel state types (no Context dependency)
- Replaced `errorMessage: StateFlow<String?>` with `error: StateFlow<AccountError?>` where `AccountError` is a sealed enum (`SAVE_FAILED`, `UPDATE_FAILED`, `ARCHIVE_FAILED`, `DELETE_HAS_TRANSACTIONS`). The Composable does `when(error)` to resolve via `stringResource()`.
- Collapsed `BackupStatus.Success(message)` / `Failure(reason)` into typed variants (`ExportSuccess`, `ImportSuccess`, `ExportFailure(detail?)`, `ImportFailure(detail?)`). Failure variants still carry the underlying exception message as an optional `detail` field so debugging information isn't lost.
- For VM operations that write strings to disk (e.g. `RecurringRulesViewModel.acceptSuggestion`'s `notes` field), the signature now takes the resolved string as a parameter: `acceptSuggestion(suggestion, notes: String?)`. The Composable resolves `R.string.settings_recurring_auto_detected_notes` and passes it in. This keeps the VM pure while still letting the user's locale shape what gets persisted.

## Consequences

### Positive
- Adding a new locale = drop in `res/values-fr/strings.xml`. No code changes.
- ViewModels are still pure-Kotlin testable with no Robolectric / Context fakes.
- Layout direction (RTL/LTR) is reactive — change locale → activity recreates → CompositionLocal flips → everything updates.
- Bilingual category names (`nameAr` + `name`) and bilingual currency labels remain visible to users who want both — only the *layout* and the *system text* respect the picked locale.

### Negative
- Persisted user data isn't retroactively translated. If a user creates transactions in Arabic and later switches to English, those transactions still display their original Arabic merchant strings. This is the correct behavior (user data is theirs), but it means new self-transfer SMS ingestion happening in English mode would emit English merchant strings, while old ones remain Arabic. To localize new persisted records we'd need to inject `@ApplicationContext` into `SmsIngestionPipeline` (deferred — see "Residual debt" below).
- The activity must `recreate()` on language switch. There's a brief flash. Alternative (don't recreate, just update CompositionLocal) doesn't work because Compose can't re-read `Resources.getString()` for already-rendered AtharText nodes without recomposition triggered by a Configuration change.

### Residual debt
Three categories of strings remain in code (intentional):

1. **Seed data** — `AccountSeed` cash account name "النقدي", `TmoapSeed` investment seed names. These run once on first launch and the user can rename via the Accounts screen. Acceptable.
2. **SMS ingestion** — `SmsIngestionPipeline` writes `effectiveMerchant = "تحويل داخلي · ادخار محتمل"` and `notes = "تحويل بين حساباتك ..."` directly to the DB for self-transfers. To localize new records, inject `@ApplicationContext context` and use `context.getString()`. Old records stay as-is (user data is sacred).
3. **SMS parser regexes** — `IgnorePatterns.kt`, `Normalize.kt`, AlRajhi/STC templates. These are Arabic *because the SMS text is Arabic* — they're parsers, not UI strings, and must stay literal Arabic.

## Alternatives considered

| Approach | Why rejected |
|---|---|
| `AppCompatDelegate.setApplicationLocales` | Pulls in AppCompat for one API; Athar is Compose+M3-only |
| Pure Kotlin string maps | Reinvents `Resources`; loses pluralization helpers, RTL hints, pseudo-locale testing |
| `@ApplicationContext` in every VM | Couples logic layer to platform; complicates testing |
| Compose `Locale` parameter threading | Requires every composable to take a `locale: Locale` parameter; viral |
| System-locale-only (no in-app picker) | Saudi diaspora running English phones can't get Arabic UI; fails one of the brief's stated user segments |

## Why this matters for future work

- **Adding a 6th language is mechanical.** Drop `values-fr/`, `values-hi/`, etc. Layout direction picks itself up from `Locale.getDefault().language == "ar"` (already wired). For other RTL locales (Hebrew/Urdu) extend the check: `language in setOf("ar", "he", "ur", "fa")`.
- **Don't reintroduce hardcoded strings.** Every `AtharText(text = "...")` with non-trivial literal text is now a regression. The visual QA loop on emulator (switch to English, sweep every screen) is the failsafe.
- **Don't make ViewModels Context-aware.** The sealed-state pattern is intentional. If you need a new error type, add it to the existing sealed hierarchy.
- **For data that must be locale-aware at write time** (like new SMS self-transfer transactions): inject `@ApplicationContext`, but resolve strings at the boundary (the ingestion entry point) rather than threading Context through the whole pipeline.
