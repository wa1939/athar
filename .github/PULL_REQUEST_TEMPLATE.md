## Summary

<!-- One paragraph. What changes, why. Link the ticket if any. -->

## Type

- [ ] feat — new user-facing capability
- [ ] fix — bug fix
- [ ] refactor — no behavior change
- [ ] test — adding/updating tests
- [ ] docs — README, ADR, comments
- [ ] chore — build, CI, deps

## Spec

<!-- Link to docs/specs/{ID}-{slug}.md if this is a non-trivial feature. -->

## Test plan

<!-- Bulleted checklist of what was verified. -->

- [ ] `./gradlew test` passes locally
- [ ] `./gradlew :app:assemblePersonalFullSmsDebug` builds
- [ ] Touched-screen Compose preview renders correctly
- [ ] Manual smoke on a connected device (if UI change)

## Architecture decisions

<!-- If this PR introduces a new ADR or amends an existing one, link it. -->

## Screenshots (UI only)

<!-- Before / after side-by-side if applicable. -->

## Backwards compatibility

- [ ] No database schema change
- [ ] Schema changed: migration in `AtharDatabase.MIGRATION_X_Y` provided and tested

## Privacy

- [ ] No new network call added
- [ ] No new third-party SDK added
- [ ] No new permission requested
<!-- If any of the above are unchecked, link the ADR amendment. -->
