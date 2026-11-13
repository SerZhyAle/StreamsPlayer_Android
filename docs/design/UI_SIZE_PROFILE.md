# UI Size and Input Profile - StreamsPlayer Android

```contract
id: UI-SIZING
version: 1.1
status: active
scope: StreamsPlayer Android (Phone, Wear OS, Watch Face)
author: S0036 (owner decisions 2026-10-26)
```

## 1. Surface Input Profile

Under **UI-SIZING** 1.1 section 2 rule 1, each product selects a stable input profile.
Android is inherently a touch platform:

- **Phone application (`:phone`)**: Fixed profile `TOUCH`. No pointer/mouse profile switching is performed.
- **Wear OS application (`:wear`)**: Fixed profile `TOUCH`.
- **Watch Face (`:watchface`)**: Ambient / interactive watch face (no settings rail).

No geometry or hit target dynamically resizes or reflows on pointer or touch movements.

## 2. Compact Mode Decision and Target Floor

- **Compact Mode**: Explicitly **NOT exposed** on StreamsPlayer Android (owner decision 2, 2026-10-26).
- **Smallest Supported Touch Target**: **48 dp** (`@dimen/ui_touch_target`).
  In accordance with UI-SIZING section 2 rule 4 and Android Accessibility guidelines, no interactive hit target is ever reduced below 48 x 48 dp.
- **Ordinary Touch Baseline**: 48 dp target height/width, with 16 dp horizontal padding for buttons.
  Visible control faces may measure 40 dp (as with standard Material 3 buttons) while maintaining a minimum 48 dp touch target via touch bounds/insets (UI-SIZING section 5 amendment 1.1).

## 3. Scale Composition and Glyph Bounds

Under **UI-SIZING** section 2 rule 3:
1. **Interface Scaling**: Resolved by Android display density metrics (`dp`). Platform layout handles display density without manual double scaling.
2. **Text-Only Enlargement**: Governed by Android user font scale (`sp`).
3. **Companion Glyphs**: Scaled according to `UiSizeTokens.glyphScale(fontScale)`:
   - Growth ratio: `0.5x` of text enlargement delta (`GLYPH_FOLLOW_RATIO = 0.5f`).
   - Minimum scale bound: `1.0f` (glyphs never shrink below their specified tier even if system font scale < 1.0).
   - Maximum scale bound: `1.5f` (at maximum system text enlargement 2.0x, glyphs reach 1.5x of base tier).
   - Base tiers (touch profile):
     - Button glyph: 20 dp (`@dimen/ui_button_glyph`)
     - Boolean option glyph: 20 dp (`@dimen/ui_option_glyph`)
     - Settings rail glyph: 20 dp (`@dimen/ui_rail_glyph`)
     - Group disclosure marker: 16 dp (`@dimen/ui_group_marker`)

## 4. Approved Permanent Variants

Under **UI-SIZING** section 3, **BUTTONS** section 6, and **BOOLEAN-OPTION** section 6:
- **Wear OS (`:wear`)**: Recorded permanent variant (owner decision 3, 2026-10-26).
  Wear OS follows Wear Compose conventions (`Chip`, `ToggleChip`, 48 dp targets).
  Contracts are applied semantically:
  - Boolean options use visible checkbox indicators and label activation.
  - Settings use single-column page lists rather than a two-pane rail (which does not fit round watch displays).

Under **BUTTONS** section 2 and **BOOLEAN-OPTION** section 1, the watch's own surfaces keep that variant. Its measured
checks are the rows marked Wear in `docs/design/UI_CONTRACT_CHECKS.md`.

### Per-surface applicability (S0049)

| Surface | UI-SIZING | BUTTONS | BOOLEAN-OPTION | COLLAPSIBLE-GROUP | SETTINGS-NAVIGATION |
| --- | --- | --- | --- | --- | --- |
| `:phone` every screen | binds | binds | binds | binds where a screen has several topics | binds the settings screen |
| `:wear` every screen | permanent variant | permanent variant | permanent variant | no group is used; a watch screen is one topic | permanent variant: page lists, no rail |
| `:watchface` | not applicable | not applicable | not applicable | not applicable | not applicable |

The watch face has no configuration surface: it draws the time, and it has no settings window, command panel, boolean
option or group of its own, so none of the five contracts has anything to bind on it.

## 5. Settings Screen Record (SETTINGS-NAVIGATION 2.0 section 6)

- **Inventory.** One screen, `SettingsActivity`, reached from the catalog's menu and from **Broadcast settings** on
  Broadcast Control. Destinations in fixed order: General (`general`), Broadcast (`broadcast`), About (`about`).
  - General: theme, language, the live broadcasting switch (`enableBroadcasting`, its one editor).
  - Broadcast: the broadcast settings of `fragment_settings_broadcast`, their one editor. Publish and Drive are one row
    per family (audio, camera), so no row depends on a mode chosen elsewhere.
  - About: the set of section 3.3, below.
- **Icon policy.** Monochrome for every destination (section 2.3): the rail tints selected and normal glyphs from theme
  roles. Glyphs: `app.settings`, `feature.live-broadcast`, `app.info`.
- **Parent/child map.** No child level: three top-level destinations.
- **Responsive rail decision (section 2.6).** The breakpoint is a screen width of 600 dp. At 600 dp and more the rail
  stays beside the page. Below it the rail is a dedicated navigation view: the page list fills the window, a destination
  opens its page in the same window, and Up or Back returns to the list with the selection kept. A screen opened at a
  named destination (Broadcast Control's button) returns to its caller on Back.
- **Commit model.** Immediate: a dropdown or a checkbox commits on selection, and a text row commits when it is left or
  when the screen pauses. Selecting a page starts no operation.
- **Remembered state (section 4.3).** The last page, and per page the top row of the viewport by its view id plus the
  offset into it, in the private preference file `settings_screen_state`; restored after layout, and an unknown page or
  row falls back to the first page and the page's top. A later opening lands on the last page at any width; only the very
  first opening below the breakpoint starts at the list, and Up from the page returns to it.
- **Presentation (section 5).** A page is built once, on its first visit, from values read before it is inflated, and
  then kept for the screen's life; a newer activation cancels page work in progress; the window holds its first frame
  until the first page is ready, for at most 400 ms. Each visit opens the trace section
  `settings.page.<id>.first|warm` from activation to the frame that shows the page.

### About resource inventory (section 3.3)

One inventory, the companion of `SettingsAboutDestinationView` and the edition's `Distribution.updatesUrl`. Opening About
contacts nothing; every link is user-initiated.

| Resource | Where it leads |
| --- | --- |
| Description, running version | `about_description`; `BuildConfig.VERSION_NAME` and `VERSION_CODE` |
| License | the repository's `LICENSE` |
| Privacy | the hosted privacy page in the app language (EN, RU, UK; English otherwise) |
| Help | the bundled help index |
| Official site | the media exchange portal start page |
| Support | the repository's issue tracker |
| Diagnostic export | `DiagnosticReportExporter`, through the system share sheet |
| Download and updates | the Play listing (store) or the releases page (noLegal) |
| Repository | the repository page |
| Index of necessary resources | `about_resources_summary`; the full list is `THIRD-PARTY-NOTICES.txt` in the package |
| Related products | FastMediaSorter and StreamsPlayer for Windows, at their `SITE-FAMILY-MAP` addresses |

### Settings with no editor

`enableWearCompanion`, `dimClockOverlayEnabled` and `hideSystemUiInFullscreen` stay internal values with no editor (owner,
2026-11-08). This is a gap against section 3.2, not conformance: the dim clock overlay cannot be switched on by a user
today, and an editor or retiring the fields is a separate ticket the owner may order.

## 6. Group Decisions (COLLAPSIBLE-GROUP 1.1)

| Screen | Decision | Why |
| --- | --- | --- |
| Exchange server | Three independent groups: Via Google Drive, Exchange server, Live broadcasts. First use: all expanded. State kept per group (`exchange__drive`, `exchange__account`, `exchange__broadcasts`) | Three distinct topics, and the person arrives for one of them. Expanded first, so nothing is hidden until the person chooses to fold it |
| Broadcast Control | No group | One coherent set per state once the settings left it (section 2.2) |
| Settings pages | No group | A rail page is already a topic boundary (section 2.2) |
| Filter & Sort, other sheets | No group | One topic each |

**Packing (section 4.3): not enabled.** Three headers on a phone-width screen stack in one column and stay readable at the
largest text; on a wide screen each header still spans the content width, with its body directly below. Delivering a
connection string expands the account group, because the form it fills is inside it (section 4.2).

## 7. Command Decisions (BUTTONS 2.0)

Text-only is the policy for plain answers and short actions (OK, Close, Apply, Clear, Keep); a substantive command with a
recognizable glyph carries it. Web links carry the external marker and link semantics; child navigation carries the
trailing chevron.

| Layout | Decision |
| --- | --- |
| `activity_exchange`, `activity_main`, `activity_qr_scanner`, `dialog_import_broadcast`, `item_exchange_broadcast`, `sheet_stream_filter`, `view_settings_about`, `activity_broadcast_control` (both orientations), `dialog_exchange_diagnosis`, `sheet_sleep_timer` | Command rows flow through `CommandFlowLayout` at content width. On the exchange screen the destructive command is last and apart |
| `custom_stream_player_controls` | Icon-only transport and top-bar controls: familiar repeated commands in a constrained area (section 2.4), each with a localized name |
| `item_stream_channel`, `item_recently_played_channel` | Icon-only list-row controls, repeated per row (section 2.4) |
| `item_stream_live`, `sheet_recently_played`, the banner of `activity_main`, `sheet_channel_actions` | One command in its row: no panel to flow. Their corner help is icon-only |
| `view_collapsible_section_header`, `view_dialog_title_help`, `view_settings_toggle_row`, `view_settings_dropdown_row`, `view_settings_input_row` | Icon-only option or corner help inside a shared component |

## 8. Boolean Options (BOOLEAN-OPTION 2.0)

Every product-owned boolean option of the phone uses `SettingsToggleRow`: the broadcast settings, the General page, the
three options of the exchange screen and **Pinned only** in Filter & Sort. No `CheckBox` or `MaterialCheckBox` is used in a
layout outside that component.
