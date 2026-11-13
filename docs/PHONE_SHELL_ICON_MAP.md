# Phone shell icon mapping

S0004, reviewed 2026-10-10 against ICON-SET vocabulary declarations, swept on device the same day
(Pixel_6 emulator, API 33, store-release build). This maps the catalog and radio controls.

| Drawable / surface | Vocabulary id |
| --- | --- |
| `ic_search` / search input | `action.search` |
| `ic_filter` / Filter & Sort | `action.filter` |
| `ic_grid`, `ic_list` / layout toggle | `view.grid`, `view.list` |
| `ic_pin_outline`, `ic_pin` / pin toggle | `action.pin` (outline and filled states) |
| `ic_more_vert` / channel actions and toolbar overflow | `nav.more` |
| `ic_play`, `ic_pause` / radio transport | `media.play`, `media.pause` |
| `ic_play` / playing row marker | `media.play` (qualified label: Playing) |
| `ic_close` / close radio | `nav.close` |
| `ic_share`, `ic_copy`, `ic_edit`, `ic_delete` / channel actions | `action.share`, `action.copy`, `action.edit`, `action.delete` |
| `ic_radio` / missing radio favicon and radio bar | `content.audio` (donor musical note, local filename retained) |
| `ic_video` / missing video favicon | `content.video` |
| `ic_live_broadcast` / broadcast entry | `feature.live-broadcast` |
| `ic_sleep_timer` / radio-bar sleep timer | `media.sleep-timer` |
| `ic_history` / Recently played entry | `source.recent` (clock with arrow back; shares the drawing with `content.history` per the record) |
| `ic_help_outline` / the help action of every top bar and of the player and scanner screens | `app.help` (the S0035 help actions; the name is the vocabulary name of the meaning in each UI language: Help, Справка, Довідка) |
| launcher foreground | product identity artwork, outside the vocabulary |

Downloaded favicons are external pictures under ICON-EXTERNAL. A missing tile uses the media-kind glyph;
the picture does not replace the channel title or the transport's accessible name. Source hashes for the
ported Android vectors are in the port ledger.

The initial device sweep measured glyph contrast in both themes (4.8-16.4:1), but did not prove
the pin's state drawing or the search text contrast. The implementation recheck found that the
local pin stand-in was filled in both states, and the search field combined a white platform
background with light text in dark mode. Those earlier observations do not prove these properties.

The final visual recheck of 2026-10-10 uses the donor's actual `ic_pin_outline` and `ic_pin`, with
theme tint, in the channel row and the actions sheet. The unused `ic_pin_filled` stand-in is removed.
Pixel checks of the search field, toolbar layout/filter controls, row pin/more controls and sheet pin
in both themes pass (8.03-10.91:1; search field 8.88:1 light / 10.91:1 dark). The pin shapes differ
after removing colour (884 light / 859 dark mask pixels), so state does not depend on colour alone.
Evidence: `temp/S0004/finish-visual-verdict.txt` and `finish-{light,dark}-{search,actions,pinned}.png`.

The other catalog glyphs retain their initial mapping and theme rendering. Missing favicons use
`content.audio` / `content.video`; pictures do not act as transport controls. Icon-only accessible
names use EN/RU/UK vocabulary wording (`nav.more`: "More actions" / "Ещё" / "Ще"). The toolbar's own
overflow button is platform chrome. The catalog's own registry already records first-screen rung 2
for all three icon contracts; this recheck corrects the implementation defects without claiming a
Wear OS sweep, a site/manual audit, or full product-wide rungs 3-5.

## Exchange glyphs (S0030)

`ICON-SET` 0.32 section 17 (FastMediaSorter Android S4221, current text 0.36) adds the exchange glyphs; all are
`proposed`. The drawables are the records' own drawings (24 dp, untinted, evenodd), made from the vocabulary's
`glyph.svg` by a script and not redrawn; each layout tints them with the text colour of its theme.

| Drawable / surface | Vocabulary id |
| --- | --- |
| `ic_connect_device` / **Connect watch** on the Exchange server screen | `action.connect-device` |
| `ic_server_relay` / the line of the Exchange server screen's account card that says every broadcast is announced on the server from its start (S0021), the relay status line of the watch's Broadcast screen (`wear/.../drawable/ic_server_relay.xml`) | `feature.server-relay` |
| `ic_guest` / the guest notice of the Exchange server screen and of the broadcast settings | `content.guest` |
| `source.exchange-server` | mapped to the one-tone glyph of `EXCHANGE-MARK` (`ic_exchange_mono`), by the owner's order of 2026-10-19; no control of this product draws a one-tone server, so the glyph is not vendored. Where the server is named, S0039 places the coloured mark instead (`ic_exchange_mark_small`, vendored byte for byte, untinted, not an `ICON-SET` glyph): the exchange screen's sign-in heading, account card and server errors, the certificate dialogs, the publish line of Broadcast Control, the Exchange server topic of the portal chooser and the Own status line of phone and watch (S0039 section 7.1) |
| `brand.google-drive` | not drawn: the Drive route is named by the words "via Google Drive" (`ICON-SET` section 17 item C) |
| `action.send-clipboard`, `action.receive-clipboard`, `content.invitation` | no control of this product: it keeps no clipboard on the server and creates no invitation (accounts are signed in only) |

The overflow menu of the catalog draws no item icons, so its **Exchange server** and **Media exchange and
broadcasts** entries carry none. The status pills stay text on a saturated ground, with no glyph.

Device sweep (S0030, 2026-10-25, emulator API 33, store debug build, EN/RU/UK, light and dark): the relay glyph of the
broadcast settings row is drawn in all six combinations and measures 8.88:1 on the light and 10.91:1 on the dark
background (`temp/S0030/glyph-contrast.json`, screenshots beside it); S0021 removed that row on 2026-11-10. Not swept
on a device: the glyphs of the account state of the Exchange server screen (**Connect watch**, the line about
broadcasts on the server, the guest notice), which needs a
signed-in exchange account, and every watch glyph, which needs a Wear OS device; both are drawn by layout and
composable code that compile and are held by the static wiring test (`ExchangePortalLinksTest`) only.

Contrast and state: every glyph is a single-colour shape tinted `colorOnSurface` or `colorOnSurfaceVariant`, so it
follows the theme in light and dark; none carries state by colour (the switch and the status text do).

## Settings screen (S0049)

The settings rail draws every glyph monochrome from the theme (`SETTINGS-NAVIGATION` 2.0 section 2.3).

| Drawable / surface | Vocabulary id |
| --- | --- |
| `ic_settings` / the General destination and the catalog's Settings menu entry | `app.settings` |
| `ic_live_broadcast` / the Broadcast destination | `feature.live-broadcast` |
| `ic_info` / the About destination | `app.info` |
| `ic_chevron_right` / **Broadcast settings** on Broadcast Control, **Open General** on the Broadcast page (child navigation) | `nav.go-to` |
| `ic_open_in_browse` / the web links of About and of the exchange screen | `nav.open-external` |
