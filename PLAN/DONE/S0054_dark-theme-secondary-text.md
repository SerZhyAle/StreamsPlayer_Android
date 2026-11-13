# S0054 - Secondary text unreadable in the dark theme: `text_color_secondary` has no night value

**Status:** Implemented
**Status note:** Night value of text_color_secondary ported from FastMediaSorter (values-night/colors.xml, ledger row); not built, not tested until the release - the build and the recapture of the broadcast help pictures are RELEASE items
**Complexity:** complex - confirmed 2026-10-29: one new resource file forces the rung above primitive; one colour value, no code, no screen, no DI
**Phase:** phone UI; parked from S0035 on 2026-10-29 (found by the help screenshot capture, not fixed there)

## 1. Symptom

In the dark theme the secondary line of a settings row is nearly invisible: dark grey text on the dark surface. Seen on
Broadcast Control's settings card ("Enable broadcasting", "Loop protection", "Auto-open the send screen", "Publish
through the exchange server", "Announce through Google Drive") in every language.

## 2. Evidence

- `phone/src/main/assets/help/img/broadcast-ru-dark.jpg` and `broadcast-settings-*-dark.jpg`, captured 2026-10-29 by
  `tools/help/capture-help-screenshots.ps1` on Pixel_9 API 35 with the system night mode on.
- `phone/src/main/res/values/colors.xml`: `text_color_secondary` is `#99000000` (60 % black); there is no
  `values-night` folder, so the dark theme uses the same colour.
- `phone/src/main/res/layout/view_settings_toggle_row.xml` line 84: the subtitle `str_subtitle` sets
  `android:textColor="@color/text_color_secondary"`. Other layouts may use the same colour; the inventory is part of
  this ticket.

## 3. Open questions

All resolved without the owner on 2026-10-29, by the records below.

- Theme attribute or night value? **Night value.** FastMediaSorter Android carries the same colour with a night
  value: `app_v2/src/main/res/values-night/colors.xml` line 219, `text_color_secondary` = `#99FFFFFF`. The port of
  `values/colors.xml` took the day value and missed the night one. `CLAUDE.md` section 2 (full port, never a rewrite)
  settles it: the night value is ported; switching the rows to a theme attribute would be a rewrite that diverges
  from the source. `UNIFIED-UI` (pointer, contracts reviewed 2026-10-27) binds sizes, buttons, boolean rows,
  collapsible groups and settings navigation; it states no colour token, and its contrast evidence belongs to S0049.
- Does the source have the defect, and how is the ledger handled? **The source does not have it** (night value
  present). The ported file gets its own ledger row, `app_v2/src/main/res/values-night/colors.xml` ->
  `phone/src/main/res/values-night/colors.xml`, recorded by `tools/port-ledger/record-port.ps1`. Like the day file it
  is a partial port: only the colours this product defines are carried, and of those only `text_color_secondary` has
  a night value that differs (`black` and `white` are the same in both files; `color_icon_plate_teal` has none).

## 4. Definition of done

- Every use of `text_color_secondary` is readable in both themes (contrast checked on the dark captures).
- The help pictures of the affected pages are recaptured (`tools/help/capture-help-screenshots.ps1 -Help
  broadcast,broadcast-settings`). The freshness gate of S0035 notices a change of the row layouts it fingerprints,
  but not a change of `colors.xml` alone.

## 5. Shared-contract boundary

None. A colour resource of the phone app is read by no other product; behaviour stays in step with FastMediaSorter,
which already has this value.

## 6. Plan

Coverage: the inventory and the readable colour map to phase 1, the ledger rule (`CLAUDE.md` section 2) to phase 2,
the contrast check to phase 3 (static now, on the captures at release), the recapture to the RELEASE items.

Inventory of `text_color_secondary` (phone; wear and watchface have no use), all through `@color/text_color_secondary`,
so one night value covers every one:

| Layout | Use |
| --- | --- |
| `view_settings_toggle_row.xml` | subtitle text colour (line 84), info icon tint (line 66) |
| `view_settings_dropdown_row.xml` | subtitle text colour (line 79), info icon tint (line 50) |
| `view_settings_input_row.xml` | info icon tint (line 34) |
| `view_collapsible_section_header.xml` | chevron tint (line 62) |

- [x] Phase 1 - add `phone/src/main/res/values-night/colors.xml` with `text_color_secondary` = `#99FFFFFF`, the
  source's value. Check: the file exists and holds that value.
- [x] Phase 2 - record the port. Check: `record-port.ps1` exits 0 and the ledger has the row with the source hash
  `50915b573a27..`.
- [x] Phase 3 - static contrast. Check: the WCAG ratio of the blended colour against the Material 3 baseline dark
  surface is at least 4.5:1.

## Last Audit

**Date:** 2026-10-29 | **Mode:** pre-release (between releases, fast checks only) | **Verdict:** Implemented - not
built, not tested until the release

**Proven by fresh commands (2026-10-29):**

- `phone/src/main/res/values-night/colors.xml` read back: expected `#99FFFFFF` | actual `#99FFFFFF`; the theme
  `Theme.StreamsPlayer` derives from `Theme.Material3.DayNight.NoActionBar`, so the `night` qualifier is resolved by
  the system night mode.
- `pwsh -NoProfile -File tools/port-ledger/record-port.ps1 -Source app_v2/src/main/res/values-night/colors.xml
  -Destination phone/src/main/res/values-night/colors.xml`: exit 0, `at 50915b573a27`; ledger line 309 holds the row.
- `check-drift.ps1 -Json`: the new row is not drift (0 matches for `values-night`); the 143 uncarried rows it reports
  predate this ticket and are not touched.
- WCAG contrast, computed in PowerShell from the colours (60 % alpha blended over the surface): new night value on
  `#141218` 7.15:1 and on `#2B2930` 6.14:1 (expected >= 4.5 | actual 7.15 / 6.14); the old value on `#141218` was
  1.08:1. The day value is unchanged (5.64:1 on `#FEF7FF`).
- `pwsh -NoProfile -File tools/help/assert-help-map.ps1`: exit 0, `assert-help-map: PASS (30 surfaces, 29 rows,
  1 baseline, 33 pages, 8 picture sets checked ..)`.

**Findings:** none at P0-P3. The surfaces are the M3 baseline; the app does not apply dynamic colour (no
`DynamicColors` call in `phone/src/main/java`).

**Hygiene:** the change adds a resource file and a ledger row; nothing became dead.

### Release items

- [ ] RELEASE: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` - the phone build packages the new
      resource (no Android build runs between releases, `CLAUDE.md` section 5a).
- [ ] RELEASE: `pwsh -NoProfile -File tools/help/capture-help-screenshots.ps1 -Serial emulator-NNNN -Help
      broadcast,broadcast-settings`, then `generate-help-pages.ps1` and `assert-help-map.ps1`; the dark pictures show
      the subtitles readable (the contrast check of section 4 on the captures). The freshness gate does not
      fingerprint `colors.xml`, so this item is the only trigger.
