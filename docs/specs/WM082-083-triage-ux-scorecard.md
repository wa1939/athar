# WM082/WM083 — Triage UX + 4/4 Scorecard Closure

## Goal
Close the five WM081 scorecard dimensions to 4/4 and remove the three triage
pain points: finding/selecting unknown merchants, one-gesture delete, and
one-shot bulk classification.

## Changes
- **History triage (WM082)**: classification-first filter architecture — one
  category chip row leads; status/type/source moved behind «تصفية»
  (ModalBottomSheet). `BulkSelectionCard` gained destructive delete («حذف (N)»,
  crimson). `TxRow` rebuilt on `combinedClickable`: long-press = quick single
  delete (no editor), real `Checkbox` in selection mode, ≥48dp rows. Both
  deletes confirm via AlertDialog («لا يمكن التراجع عن الحذف.»).
- **ViewModel**: `deleteSelected()` + `DeleteEvent(count)` + toast state.
- **Performance (WM083)**: two root causes of the startup main-thread storm
  fixed: (1) encrypted DB (sqlcipher native load + Keystore key + Room build)
  now prewarmed on a dedicated thread at Application.onCreate — main joins
  Dagger's memoized singleton instead of executing it; (2) the user-facing
  debug-update channel now builds with R8 (`-PminifyDebugUpdate=true`, default
  off for QA speed) — dex 50→17MB, class-load storm eliminated (0 skipped
  frames at tablet geometry; was 205-258).

## Verification (live, emulator-5554, vc13 minified)
- Triage walkthrough 8/8 effective (chip copy corrected).
- Long-press single delete + bulk delete: PASS with corpus-protection gates
  (throwaway SMS rows only; abort on count mismatch).
- One-tap classify-all-same-merchant from «متكرر بلا تصنيف»: PASS.
- HistoryViewModelTest incl. new deleteSelected test: green.
- Dark mode visually audited: proper surfaces, readable text (hallucinated
  low-contrast claim refuted by pixel zoom).
- Tablet 1920x1200: renders, no jank, no crash.
- A11y sweep: 0 unlabeled clickables on Today/Trends/Plan/History.

## Known residue (documented, non-blocking)
- Trends legend row measures 36dp text cluster (full-width 347dp row;
  `heightIn(min=48dp)` in source since beta.26 — uiautomator subtree artifact).
- Tablet first-frame still slow on emulator after density change (host-load
  dependent); zero jank once settled.
