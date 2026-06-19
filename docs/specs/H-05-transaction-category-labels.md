# H-05 Transaction Category Labels

## Goal

Make Today and History transaction lists readable for normal users by showing localized category names instead of raw internal IDs such as `cat-coffee`.

## Inputs

- Existing `Transaction.categoryId` values.
- Existing `CategoryRepository` category names (`name`, `nameAr`).
- Existing Today pending/today/recent rows and History row subtitles.

## Behavior

Today observes all categories, including archived categories, and stores a compact ID-to-label map in `TodayState`. History exposes the same label map from `HistoryViewModel`.

Rows render:

- Arabic category name when the app locale is Arabic.
- English category name otherwise.
- `today_uncategorized` when `categoryId` is null, empty, or blank.
- The raw category ID only as a fallback when a transaction references an unknown category.

History row subtitles now include date, type marker, status, source, and category label.

## Acceptance

- Today pending rows, today rows, and recent rows show localized category names.
- History rows show localized category names in the subtitle.
- Archived category labels are still available for older transactions.
- Unknown category IDs remain visible as fallbacks instead of silently becoming "Uncategorized".
- Unit tests cover category-label state generation including archived categories.

## Validation

- `:feature:today:testDebugUnitTest`
- `.\gradlew.bat --console=plain test :app:assemblePersonalFullSmsDebug :app:assembleStoreSafeDebug :app:lintPersonalFullSmsDebug :app:lintStoreSafeDebug --no-daemon --max-workers=1`
- Runtime QA was attempted on 2026-06-13 with the Android Emulator QA flow. `adb devices -l` returned no attached devices; the installed `AtharPixelQaApi35` AVD is present, but `C:\Users\waok\Android\Sdk\emulator\emulator.exe -accel-check` reported that the Android Emulator hypervisor driver is not installed, and a bounded headless launch exited with `x86_64 emulation currently requires hardware acceleration`. Install/launch testing remains under QA-01 until a physical device or accelerated emulator is available.
