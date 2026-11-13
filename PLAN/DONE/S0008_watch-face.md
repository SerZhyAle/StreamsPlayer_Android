# S0008 - StreamsPlayer watch face

**Status:** Implemented
**Status note:** Ported and rebranded WFF v4 watch face from FastMediaSorter; min-sdk-watchface 36; 18 files registered in ledger (683 total in step); tests passed; installed on API 37 Wear emulator
**Phase:** 8 of 10 (handoff section 8)
**Verification level:** device - the watch face installs and shows the time with the product branding.

## 0. Source

`docs/BOOTSTRAP_HANDOFF.md` section 8 item 8, section 4.4, section 5 (`watchface/`, resource-only Watch Face
Format, EN/RU/UK strings).

## 1. Goal

A StreamsPlayer-branded watch face under `com.sza.streamsplayer.watchface`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0002 (watch-face module and its signing).
- **4.4 Watch face composition (answered 2026-10-10):** Rebranded clone of the FastMediaSorter WFF watch face (digital clock, battery indicators, complications, EN/RU/UK strings).

## 4. Acceptance

- [x] Handoff section 9 item 3: The StreamsPlayer watch face installs and shows the time with the product branding.

## 5. Implementation and evidence (2026-10-10)

- **Watch Face Format v4 & Platform level:**
  Ported the declarative resource-only WFF v4 (`com.google.wear.watchface.format.version` = 4) watch face from FastMediaSorter's `watchface` module.
  `min-sdk-watchface` raised from 33 to 36 (Wear OS 6 / API 36 required by WFF v4) in `gradle/libs.versions.toml`.
- **Identity & Packaging:**
  Package ID `com.sza.streamsplayer.watchface`, resource-only declarative module (`enableKotlin = false`, `hasCode="false"`, R8 minification stripping empty R class with no DEX in the final APK).
  Debug face name `StreamsPlayer (Debug)` (`watchface/src/debug/res/values/strings.xml`, `values-ru`, `values-uk`); release name `StreamsPlayer`.
- **Visual Composition & Complications:**
  Large digital clock (`SYNC_TO_DEVICE`, size 142 bold), date display, ambient twin mode with sparse outlines (`#FFB9C6D6`, `#FF5C6B7D`).
  Watch battery level indicator (`[BATTERY_PERCENT]`) at top center; paired phone battery bar at bottom center.
  StreamsPlayer brand wave lanes 1-5 (`#FF0B82EB`, `#FF3D9BEF`, `#FF6DB4F3`, `#FF98CFF1`, `#FFC4E3F7`) and tilt parallax particles (`#FFCFE7FB`, `#FFC4E3F7`, `#FFE3F2FE`) drawn with pure vector primitives and `<Transform>`/`<Gyro>`.
  Launch tap target on the dial center opens `com.sza.streamsplayer/com.sza.streamsplayer.wear.MainActivity`.
  Six customizable complication slots (outer left/right, inner left/right, top left/right) and editable metadata in `watch_face_info.xml` (`<Editable value="true" />`).
  Bundled digits font families (`sp_clock_casual.ttf`, `sp_clock_condensed.ttf`, `sp_clock_mono.ttf`, `sp_clock_serif.ttf`) with font notices in `third_party_notices.txt`.
  Localization: English, Russian, Ukrainian strings for complication slot labels and screen reader text; `locales_config.xml`.
- **Port Ledger & Drift Check:**
  18 ported files registered in `docs/port-ledger.jsonl`.
  `tools/port-ledger/check-drift.ps1` passes: `683 row(s) in step, 0 decided, 0 uncarried`.
  `tools/port-ledger/test-port-ledger.ps1` self-test passed: all checks passed.
- **Builds & Automated Tests:**
  `./gradlew :watchface:assembleDebug :watchface:assembleRelease` passed: outputs `watchface-debug.apk` (60,594 bytes) and `watchface-release-unsigned.apk` (56,043 bytes).
  Full test suite `./gradlew test` passed: 233 Wear tests per debug variant, 409 Phone store tests, 411 Phone noLegal tests, all passed with 0 failures.
- **Emulator Verification:**
  Installed onto `Wear_OS_Small_Round` (API 37) emulator via `adb install -r`.
  Verified package registration via `dumpsys package com.sza.streamsplayer.watchface`:
  `versionCode=261010115`, `versionName=2.61.0101.156`, `minSdk=36`, `targetSdk=36`, `installed=true`.
