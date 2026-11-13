# S0048 T5a - CyrFlip: the StreamsPlayer footer row

**From:** the StreamsPlayer Android session (`P:\ANDROID\StreamsPlayer_mob`, ticket S0048).
**To:** the CyrFlip session (`P:\WINDOWS\CyrFlip`).
**Drafted:** 2026-11-08. **Sent:** - (at G1).
**Gate:** G1 - the landing answers 200 and the `SITE-FAMILY-MAP` 1.11 row is in the catalog (`SITE-FAMILY-MAP` rule 5). State
when drafted (2026-11-08): the landing answers 404.

## Evidence attached at sending

```
curl -s -o /dev/null -w "%{http_code} %{url_effective}\n" -L https://serzhyale.github.io/StreamsPlayer_Android/
```

## The change - the footer row only

The map row (`SITE-FAMILY-MAP` 1.11): Tool `StreamsPlayer`, Type `Android and Wear OS stream player`, URL
`https://serzhyale.github.io/StreamsPlayer_Android/`, after `STREAMS Player`. **No body-copy link**: neither product covers
what the other lacks (rule 4).

Read 2026-11-08: the grid is hand-copied into 13 pages (`docs/index.html` and `docs/<locale>/index.html` for twelve locales),
every locale in English. The tile, in the form of the existing `STREAMS Player` tile:

```html
<a href="https://serzhyale.github.io/StreamsPlayer_Android/"><b>StreamsPlayer</b><span>Android and Wear OS stream player</span></a>
```

The site's own check `tools/site/Test-SiteConformance.ps1` (lines 104-118 on 2026-11-08) lists the expected sibling URLs and
asserts 8 family links per landing; it gains the new URL and asserts 9. The contact block is unchanged.

## Check this session runs

- `tools/site/Test-SiteConformance.ps1` passes with 9 family links on every landing.

## Check run by the sender afterwards

- For each of the 13 pages, `curl -s <page> | grep -c StreamsPlayer_Android` is 1 (the footer only, rule 4) and the label is
  `StreamsPlayer`.
- Result recorded in `temp/S0048/` and in the target table of `README.md` beside this file.
