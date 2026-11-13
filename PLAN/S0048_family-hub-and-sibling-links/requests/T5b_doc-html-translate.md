# S0048 T5b - doc-html-translate: the StreamsPlayer footer row

**From:** the StreamsPlayer Android session (`P:\ANDROID\StreamsPlayer_mob`, ticket S0048).
**To:** the doc-html-translate session (`P:\WINDOWS\EPUB_2_HTML`).
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

Read 2026-11-08: the grid is one minified line per page, hand-copied into 21 pages (`index`, `404`, `docs`, `docs.ru`,
`docs.uk`, `extension`, `extension-privacy`, `install-trust`, `privacy`, `release-notes`, `support` and `<locale>/index.html`
for ten locales), every locale in English. The tile, in the form of the existing `STREAMS Player` tile:

```html
<a href="https://serzhyale.github.io/StreamsPlayer_Android/" target="_blank" rel="noopener"><b>StreamsPlayer</b><span>Android and Wear OS stream player</span></a>
```

The site's own test `tests/site_test.go` (lines 50-70 on 2026-11-08) gains the URL in `familyURLs` and the label
`"https://serzhyale.github.io/StreamsPlayer_Android/": "StreamsPlayer"` in `familyLabels`. The contact block is unchanged.

## Check this session runs

- `go test ./tests/...` (the site test) passes.

## Check run by the sender afterwards

- For each of the 21 pages, `curl -s <page> | grep -c StreamsPlayer_Android` is 1 (the footer only, rule 4) and the label is
  `StreamsPlayer`.
- Result recorded in `temp/S0048/` and in the target table of `README.md` beside this file.
