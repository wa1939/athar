# G-11 - Notification Regional Currency Symbols

## Problem

Store-safe notification parsing already accepts broad ISO currency codes, but many
finance apps use regional symbols instead of codes:

- `S$ 6.40` for Singapore dollars.
- `R$ 19,90` for Brazilian reais.
- `RM12.30` for Malaysian ringgit.
- `Rp 75.000,00` for Indonesian rupiah.
- `₱`, `₩`, `฿`, and `₫` for Philippine peso, Korean won, Thai baht, and
  Vietnamese dong.

Before this slice, some of those markers either fell back to the default SAR or
were partially matched as plain `$`, which stored the wrong currency even when
the amount and merchant were otherwise parsed correctly.

## Decision

Extend `GenericBankNotificationTemplate`'s currency marker list and the shared
`Normalize.currencyCode` mapping for supported regional symbols that correspond
to currencies already accepted by Athar. The longer dollar-prefixed markers are
listed before the generic `$` branch so `S$`, `R$`, `HK$`, and `Mex$` do not get
collapsed to USD.

This does not add arbitrary package access, does not introduce FX conversion,
and does not change false-positive guards. The parser still runs only for
notification events that passed the known finance package gate.

## Acceptance

- Singapore `S$` notification amounts store `SGD`, not `USD`.
- Brazilian `R$` notification amounts store `BRL`, not `USD`.
- `RM`, `Rp`, `₱`, `₩`, `฿`, and `₫` notification amounts store their ISO
  currency codes.
- Regional symbols work with localized comma-decimal and grouped amount formats.
- Existing notification parser guards continue to pass.

## Validation

- 2026-06-14: `:ingestion:sms-parser:test` passed with portable JDK 21,
  `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: `:ingestion:notification-listener:test` passed with portable
  JDK 21, `--no-daemon`, and `--max-workers=1`.
- 2026-06-14: full stack passed with portable JDK 21:
  `test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug
  :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug`.
- 2026-06-14: runtime store-safe install was attempted with
  `AtharPixelQaApi35`; the `storeSafeDebug` APK was ready, but the emulator
  exited before exposing ADB with Windows access-violation code `-1073741819`.
  Stale `hardware-qemu.ini.lock` and `multiinstance.lock` files were removed,
  ADB was restarted, and cleanup finished with no attached ADB device, no
  emulator/qemu/netsim process, and no AVD lock files.
