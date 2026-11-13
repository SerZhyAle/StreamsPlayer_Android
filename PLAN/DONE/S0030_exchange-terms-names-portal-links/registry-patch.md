# S0030 - registry patch for the canon session

Catalog changes are the canon session's (`CLAUDE.md` section 6); this is the exact text, with the reason, for
`<catalog>/_meta/REGISTRY.md`. Nothing here was applied from this repository.

## 1. Row `ICON-SET` / StreamsPlayer Android (section 2) - append to the notes cell

> **S0030, 2026-10-25:** the exchange glyphs of section 17 (0.32) are mapped in `docs/PHONE_SHELL_ICON_MAP.md`.
> `action.connect-device` (Connect watch), `feature.server-relay` (the publish row of the broadcast settings, the
> publish switch of the Exchange server screen and the relay status line of the watch's Broadcast screen) and
> `content.guest` (the guest notices) are drawn from the records' own drawings and tinted by the layout;
> `source.exchange-server` is mapped to the one-tone glyph of `EXCHANGE-MARK` and drawn by no control yet (S0039
> places the mark); the Drive route stays words (section 17 item C); `action.send-clipboard`,
> `action.receive-clipboard` and `content.invitation` have no control in this product. Implementation evidence is the
> JVM tests and the layouts; the emulator sweep is recorded in `temp/S0030/` and in the map.

Reason: the row said "exchange concepts" were not drawn by this surface; they now are, in part.

## 2. Row `DEVICE-EXCHANGE` / StreamsPlayer Android - append to the notes cell

> **S0030, 2026-10-25:** items CW (terms), CZ (messages) and CY (ladder) are vendored as text and compared with the
> catalog by `check-sync.ps1`; `ExchangeTermsDictionaryTest` holds the exchange string set of the phone and the
> watch (EN/RU/UK) to them: the terms, the retired words, every refusal and cast failure word for word, the names of
> item CX. A finding for the contract owner: the owner's questionnaire of 2026-10-14 says "through Google Drive",
> item CW says "via Google Drive"; this product follows item CW.

## 3. New rows (section 2), StreamsPlayer Android

```
| `DOC-EXTERNAL-QUALITY` | StreamsPlayer Android | C | partial 0.12 | - | pending | S0030: contributor to the shared media exchange portal. Section 9 items F/G: the product's own sections (receiving, broadcasting, the watch) are written in EN, RU and UK at once in `docs/PORTAL_SECTIONS_STREAMSPLAYER.md` and applied in the portal source by FastMediaSorter Android, never edited from here. No site or help page of its own. Pointer: `docs/contracts/PORTAL-DOCS.md`. |
| `SITE-STRUCTURE` | StreamsPlayer Android | C | partial 0.3 | - | pending | S0030: the linking side only. Items C and E: every screen that has a portal page (Exchange server screen sign-in/account/Drive/broadcast list, the publish notice, the watch via "Help on the phone") opens it through one resolver (`ExchangePortalLinks`) that picks the EN/RU/UK page by the app language and falls back to English; the readable list of held addresses is `docs/MEDIA_EXCHANGE_PORTAL.md`, held to the resolver by `ExchangePortalLinksTest`. Item F: no "not available yet" mark. The publisher-side items (A, B, D) bind FastMediaSorter Android. Pointer: `docs/contracts/PORTAL-DOCS.md`. |
```

Reason: the product links the portal from its screens and contributes to it, so blanket non-adoption is no longer
honest; `Verified` is `pending` until the emulator walk of the deep links is recorded for the build that ships.
