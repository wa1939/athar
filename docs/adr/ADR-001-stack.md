# ADR-001: Technology stack

- **Status:** Accepted
- **Date:** 2026-05-24
- **Supersedes:** —
- **Superseded by:** —

## Context

Athar is a local-first Android personal finance app for Saudi/SAR users (see `Athar_Master_Brief.md` §1). It must replace a complex Excel workbook, ingest bank SMS, run an on-device categorizer, and feel premium and calm. One developer working with Claude Code in a spec-first loop will build it across ~12 weeks of evenings.

## Decision

The stack is locked as specified in Master Brief §5.1. Summary:

- Kotlin 2.x + Jetpack Compose + Material 3 Expressive
- Min SDK 28, Target SDK = latest stable
- Hilt for DI; Room (+SQLCipher) for persistence; kotlinx-datetime; BigDecimal for money
- Compose Navigation 2.x (single-activity)
- Vico for charts
- TensorFlow Lite + ML Kit Entity Extraction on-device
- Gradle 8.x with `libs.versions.toml` version catalog; convention plugins via included `build-logic/`
- Paparazzi (snapshot), Maestro (E2E), JUnit 5 + Truth + Turbine (unit), Compose UI Test (interaction)
- Multi-module: `app`, `core:*`, `feature:*`, `ingestion:*`, `ml:*`, `build-logic`

## Rationale

1. **Kotlin/Compose** — native Android gives the best premium UX, and Compose is heavily represented in LLM training data, which matters for the AI-driven development workflow.
2. **Multi-module from day one** — supports parallel build, clear ownership, and lets the pure-Kotlin parser/categorizer live in JVM modules that test on the JVM without an emulator.
3. **No backend in v1** — privacy commitment (§2.2 "Three things Athar will never do") and avoids surface area we cannot operationally support.
4. **Hilt + Room** — Google-standard, idiomatic, LLM-friendly.

## Consequences

- We accept the AGP/KGP/Kotlin compatibility-matrix coupling. Versions are pinned in `gradle/libs.versions.toml`; bumps follow the official matrix at `https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin`.
- SQLCipher integration is deferred until first Keystore-backed key gen lands (planned alongside S-13 / SMS receiver work). Until then, Room runs unencrypted in debug — release builds must not ship without SQLCipher wiring.
- No `Crashlytics` / `Firebase` / cloud SDK. If a future v2 changes that, this ADR is superseded.

## Alternatives considered and rejected

- React Native / Flutter — SMS, NotificationListener, foreground services, and Android-platform polish are first-class concerns; the cross-platform tax is not worth paying for v1.
- KMP with iOS target now — adds source-set ceremony for a target we do not ship. Add only when iOS is on the roadmap.
- Crashlytics — sends data to Google; violates §2.2 privacy commitment.
