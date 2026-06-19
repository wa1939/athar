# R-07 - Shared Specific-Merchant Guard

## Problem

Several category-cleanup paths need to know whether a merchant key is specific
enough to safely reuse:

- bulk-categorization grouping, blank-peer inheritance, and exact rule learning;
- Today pending category suggestions;
- History repeated-backlog grouping and same-merchant suggestions;
- local auto-learning from repeated confirmed history;
- support diagnostics repeated-merchant coverage and category-backlog
  recommendations.

Before this slice, each path carried its own generic-merchant list. That creates
drift risk: one feature could treat `cash`, `payment`, or Arabic labels such as
`كاش` as specific while another feature blocks them.

## Decision

Centralize the guard in `core:domain`:

- `specificMerchantKey(merchantNormalized, merchant)` returns a normalized key
  only when it is specific enough.
- `String.isSpecificMerchantKey()` rejects short keys, digit-only/account-like
  keys, and generic English/Arabic labels.

Bulk categorization, Today suggestions, History repeated-backlog cleanup, local
auto-learning, and support diagnostics all use this shared helper. Explicit user
work is still allowed: generic rows can be categorized one by one, but they do
not create same-merchant shortcuts, learned exact rules, or repeated-backlog
recommendations.

## Acceptance

- Specific repeated merchants still group, suggest, inherit, and learn exactly as
  before.
- Generic English labels such as `payment`, `purchase`, `cash`, `merchant`, and
  `bank` remain row-by-row only.
- Generic Arabic labels such as `كاش`, `شراء`, and `دفع` do not produce repeated
  backlog groups, Today suggestions, bulk inherited rows, or local auto-learned
  exact rules.
- Support diagnostics still count generic rows in total backlog, but they do not
  report them as repeated merchant groups or recommend History repeated cleanup
  because of them.
- The shared helper has direct domain tests, and each consumer keeps regression
  coverage for the safe/unsafe cases it owns.

## Non-goals

- Do not remove generic rows from History or bulk exports; users can still
  classify them explicitly.
- Do not broaden matching to substrings or fuzzy merchant names.
- Do not add cloud categorization or telemetry.
- Do not promote private/local merchant labels into public seed rules.

## Validation

- `:core:domain:test --tests com.athar.core.domain.model.MerchantSpecificityTest`
- `:core:data:testDebugUnitTest --tests com.athar.core.data.csv.MerchantBulkCsvTest --tests com.athar.core.data.rules.LocalCategoryRuleLearnerTest`
- `:feature:today:testDebugUnitTest --tests com.athar.feature.today.TodayViewModelTest --tests com.athar.feature.today.HistoryFilterTest --tests com.athar.feature.today.HistoryViewModelTest`
- Full JVM test, both debug APK builds, and both lint variants with JDK 17.
- Runtime install/launch was attempted on the local AVDs but blocked by emulator
  configuration: x86_64 requires hardware acceleration and the Android Emulator
  hypervisor driver is not installed; the ARM AVD is unsupported on this x86_64
  host.
