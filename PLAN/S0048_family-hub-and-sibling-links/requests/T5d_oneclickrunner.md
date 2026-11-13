# S0048 T5d - OneClickRunner: the StreamsPlayer footer row

**From:** the StreamsPlayer Android session (`P:\ANDROID\StreamsPlayer_mob`, ticket S0048).
**To:** the OneClickRunner session (`P:\WINDOWS\OneClickRunner`).
**Drafted:** 2026-11-08. **Sent:** - (at G1).
**Gate:** G1 - the landing answers 200 and the `SITE-FAMILY-MAP` 1.11 row is in the catalog (`SITE-FAMILY-MAP` rule 5). State
when drafted (2026-11-08): the landing answers 404.

## Evidence attached at sending

```
curl -s -o /dev/null -w "%{http_code} %{url_effective}\n" -L https://serzhyale.github.io/StreamsPlayer_Android/
```

## The change - the footer row only

The map row (`SITE-FAMILY-MAP` 1.11): Tool `StreamsPlayer`, Type `Android and Wear OS stream player`, URL
`https://serzhyale.github.io/StreamsPlayer_Android/`. **No body-copy link**: neither product covers what the other lacks
(rule 4).

Read 2026-11-08: the grid is hand-copied into 3 pages (`docs/index.html`, `docs/guide.html`, `docs/privacy.html`), in English.
The tile, in the form of the existing tiles:

```html
<a href="https://serzhyale.github.io/StreamsPlayer_Android/"><b>StreamsPlayer</b><span>Android and Wear OS stream player</span></a>
```

The contact block is unchanged.

## Observed on the same read, not asked here

The grid has 7 tiles and no `STREAMS Player` tile (`https://serzhyale.github.io/StreamsPlayer/`), and `guide.html` labels two
tiles `FastMediaSorter v2` and `FastMediaSorter LITE` where the map reads `Fast Media Sorter & Organizer` and `Fast Media Sorter &
Sharing for Windows`. These are this site's own map conformance (`SITE-FAMILY-MAP` section 5); the session decides whether to
take them in the same edit.

## Check this session runs

- The site's own footer check, if one exists; otherwise the map's URL check over the grid.

## Check run by the sender afterwards

- For each of the 3 pages, `curl -s <page> | grep -c StreamsPlayer_Android` is 1 (the footer only, rule 4) and the label is
  `StreamsPlayer`.
- Result recorded in `temp/S0048/` and in the target table of `README.md` beside this file.
