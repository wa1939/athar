# G-11 - Notification Entertainment Labels

## Problem

Store-safe notification parsing can preserve named venues and merchants, but
some bank apps emit generic posted entertainment copy with no reusable merchant:

- `Cinema ticket payment GBP 18.50 completed`
- `Event ticket payment AED 150.00 completed`
- `Game purchase USD 59.99 completed`

Those rows parse as valid expenses, but without stable shared labels they still
require manual categorization. Entertainment notifications also contain many
non-posted amount-bearing messages: promos, discounts, showtime reminders,
presales, reservations, wishlists, trailers, and game offers.

## Decision

- Add conservative posted entertainment detectors behind the existing known
  finance-package notification gate.
- Normalize generic posted copy to exact shared labels:
  - `Cinema ticket`
  - `Event ticket`
  - `Game purchase`
- Preserve specific venues/events when present, such as `VOX Cinemas` or
  `Riyadh Season`.
- Ignore non-posted entertainment promo/showtime/presale/reservation/game-offer
  copy before amount parsing.
- Add exact priority-90 seed rules for the shared labels so these rows
  categorize without broad `ticket`, `event`, `cinema`, or `game` substring
  rules.

## Acceptance

- Posted cinema/movie/film ticket copy parses as an expense with
  `Cinema ticket`.
- Posted event/concert/theatre/festival ticket copy parses as an expense with
  `Event ticket`.
- Posted game/video-game purchase copy parses as an expense with
  `Game purchase`.
- Specific venues or event names override generic labels when present.
- Entertainment promo, showtime, presale, reservation, wishlist, trailer, and
  game-offer copy with amounts is ignored.
- Seed rules reference existing categories, stay unique, and include the new
  shared labels.

## Non-goals

- Do not add broad public seed rules for words such as `ticket`, `event`,
  `cinema`, `movie`, `game`, `show`, or `festival`.
- Do not parse arbitrary non-finance notifications.
- Do not infer private/local venues or events from private data.
- Do not treat showtimes, presales, reservation reminders, trailers, or game
  offers as posted transactions.

## Validation

- Focused parser plus data tests passed:
  `:ingestion:sms-parser:test --tests "com.athar.ingestion.smsparser.GenericBankNotificationTemplateTest" :core:data:testDebugUnitTest`.
- Full parser and listener tests passed:
  `:ingestion:sms-parser:test :ingestion:notification-listener:test`.
- Forced gated private SMS audit passed with redacted aggregate output only:
  7,887 records, 3,864 parser successes, 4,023 ignored, 0 parser
  failures, 0 failed known-bank messages, and 0 missing-merchant parsed
  expenses.
- Full JVM test/build/lint stack passed:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- Runtime `storeSafeDebug` install/launch was attempted with the built APK.
  `emulator -accel-check` reported that the Android Emulator hypervisor
  driver is not installed, and a bounded hidden `AtharPixelQaApi35` software
  launch did not expose a boot-complete online ADB device, so install,
  screenshot, UI dump, and logcat capture could not run. Evidence was written
  to `build/qa/notification-entertainment-labels-emulator/`; cleanup finished
  with no attached ADB device, no emulator/qemu/netsim process, and no AVD
  lock files.
