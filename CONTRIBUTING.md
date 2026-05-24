# Contributing to Athar

Thank you for considering a contribution. This project is built with a deliberate, spec-first workflow — the few rules below exist to keep that working as more people show up.

## TL;DR

1. Read [`Athar_Master_Brief.md`](Athar_Master_Brief.md). It's the contract. If you find a real-world reason it must change, update the brief in the same PR.
2. Pick a ticket from [`Athar_Backlog.md`](Athar_Backlog.md). Open an issue if one isn't there yet.
3. Write the spec **before** the code (one Markdown file in `docs/specs/`, 200–500 words).
4. Implement. Add tests (≥80% line coverage for new code).
5. Run `./gradlew build` and `./gradlew test` before opening a PR.

## Repository layout

```
athar/
├─ app/                              # entry point, navigation, theme
├─ core/
│   ├─ common/                       # money, dates, crypto, time helpers
│   ├─ domain/                       # entities, repository interfaces, calc/
│   ├─ data/                         # Room, repositories, mappers, seeds
│   ├─ design-system/                # tokens, components (Athar*)
│   └─ testing/                      # shared fixtures
├─ feature/
│   ├─ today/  trends/  plan/  settings/
├─ ingestion/
│   ├─ sms-parser/  sms-listener/  notification-listener/
├─ ml/
│   ├─ categorizer/  models/
├─ build-logic/                       # convention plugins
└─ docs/
    ├─ adr/                          # architecture decision records
    ├─ specs/                        # per-ticket specs
    └─ branding/
```

Each `feature:*` depends only on `core:*` modules and the ingestion/ml interfaces — never on sibling features. The dependency direction is enforced by the Gradle convention plugins; don't route around it.

## Coding conventions

These are non-negotiable; CI will reject violations on the slow path (and reviewers on the fast one):

- **Kotlin official code style** — let IDE format on save.
- **No `console`-style logs in production code.** Use `Timber.d/i/w/e`.
- **Money is `BigDecimal`** wrapped in `com.athar.core.common.money.Money`. Never `Float` or `Double`.
- **A file > 300 lines is a smell.** Split it. One screen = one ViewModel = one State class = one Event sealed class.
- **Compose previews mandatory** for every reusable composable.
- **Tests live next to code** — `<module>/src/test/kotlin/...`.
- **Conventional Commits** — `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`.
- **No third-party SDKs that send data off-device** without ADR approval. See [ADR-002](docs/adr/ADR-002-local-first.md).

## The spec-first loop

For every non-trivial ticket:

1. **Write `docs/specs/{TICKET-ID}-{slug}.md`** — ~200–500 words covering goal, inputs, outputs, edge cases, acceptance criteria, non-goals.
2. **Implement** the spec. Don't expand scope mid-PR — if you find more work, open a new ticket.
3. **Tests first if you're fixing a bug.** Reproduce, then fix.
4. **PR description summarizes the spec.** Reviewer reads the spec link, then the diff.

If a PR needs more than 3 iteration rounds, the spec was wrong — close, rewrite the spec, reopen.

## Architecture decision records

Anything that affects more than one feature, changes a contract, or violates an existing rule needs an ADR. Number them sequentially in `docs/adr/`. Existing ADRs:

| ADR | Decision |
|---|---|
| ADR-001 | Stack: Kotlin 2.1 + Compose + Hilt + Room + multi-module |
| ADR-002 | Local-first: no backend, no telemetry |
| ADR-003 | Encryption at rest: SQLCipher + Keystore-wrapped DB key |
| ADR-004 | MVP status after 24 build waves |

## Tests

- **Unit tests** — `./gradlew test`. ≥ 80% line coverage on new code.
- **Compose UI tests** — `./gradlew :feature:foo:connectedAndroidTest` (needs a device or emulator).
- **Maestro E2E** — `maestro test .maestro/flows/`.
- **Paparazzi snapshots** — `./gradlew verifyPaparazziDebug` to check; `recordPaparazziDebug` to update baseline (review diff carefully).

When adding a feature, add at least one Maestro flow that exercises the happy path.

## Branch + PR

- Branch off `main`. Branch name: `<type>/<short-slug>` (e.g., `feat/csv-import`).
- Force-pushes to `main` are blocked. Don't try.
- PRs require: build green, tests pass, one review approval.
- Squash-merge by default — keep `main` history linear.

## Filing issues

We have two issue templates:

- **Bug report** — what you expected vs what happened, repro steps, device + OS, app version.
- **Feature request** — what use case it serves, why the existing flow doesn't cover it, ideally a sketch.

For security issues, **do not open a public issue.** See [`SECURITY.md`](SECURITY.md).

## Code of conduct

By contributing you agree to the [Code of Conduct](CODE_OF_CONDUCT.md). The short version: be kind, be specific, assume good faith.

## License

By contributing, you agree your contributions are released under the [Apache License 2.0](LICENSE).
