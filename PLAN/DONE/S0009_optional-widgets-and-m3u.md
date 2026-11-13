# S0009 - Optional: home-screen widgets and m3u import

**Status:** Implemented
**Phase:** 9 of 10 (handoff section 8) - optional
**Verification level:** device, per widget.

## 0. Source

`docs/BOOTSTRAP_HANDOFF.md` section 8 item 9, section 3 (owner wishes, done only if they port cheaply),
section 5 (`widget/Stream*`, `widget/BroadcastEntryWidgetProvider.kt`).

## 1. Goal

The channel tile widget, the radio tile and the broadcast entry widget, plus m3u playlist import if S0003 did
not already carry it (USER-PLAYLIST consumer) - each only if it ports cheaply.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0003, S0004.
- **Owner decisions:** none from handoff section 4; all widgets ported cleanly with 0 drift.

## 4. Acceptance

- [x] Each carried widget places, configures and launches its target.
- [x] m3u import, if carried here, conforms to USER-PLAYLIST and its registry row is verified.

## Implementation Summary

1. **Channel Tile Widget (`StreamLaunchWidgetProvider`):**
   - Config activity `StreamLaunchWidgetConfigActivity` allows selecting channels with search & filter.
   - Channel storage via `StreamLaunchWidgetStore` with color plate rendering via `WidgetPlateGlyph`.
   - Direct headless play launch via `StreamPlayLaunchActivity` (using `StreamHeadlessPlayManager` and `StreamWidgetResolveManager`).
   - Layouts & drawables ported and verified: `widget_stream_launch.xml`, `widget_stream_launch_info.xml`, `ic_stream.xml`.
2. **Radio Quick Settings Tile (`AudioToggleTileService`):**
   - Implemented System Quick Settings Tile for play/pause toggling of stream playback via `MediaBrowserCompat` / `MediaControllerCompat`.
   - Ported `ic_tile_play.xml`, `ic_tile_pause.xml`, and `MediaControllerRelease.kt`.
3. **Broadcast Entry Widget (`BroadcastEntryWidgetProvider`):**
   - Quick launch for live broadcasting directly from the home screen into `MainActivity` with live broadcast target.
4. **M3U Playlist Import (UI entrypoint conforming to `USER-PLAYLIST`):**
   - Menu action `menu_import_playlist` added to `main_menu.xml`.
   - URL import dialog in `MainActivity` with `dialog_import_playlist.xml`.
   - `StreamsViewModel.onImportPlaylist(url)` delegates to `ImportStreamPlaylistUseCase` (ported in S0003), emitting `PlaylistImported` / `PlaylistImportFailed` user events.
5. **Localization:**
   - English (`values`), Russian (`values-ru`), and Ukrainian (`values-uk`) strings updated for search hint, empty channels, widget titles, and import playlist dialog.
6. **Port Ledger:**
   - 14 files ported and recorded via `tools/port-ledger/record-port.ps1`.
   - `check-drift.ps1` reports 697 total rows with 0 uncarried changes.
7. **Verification:**
   - `:phone:testStoreDebugUnitTest` passed (all Robolectric tests green).
   - `:phone:testNoLegalDebugUnitTest` passed (all Robolectric tests green).

