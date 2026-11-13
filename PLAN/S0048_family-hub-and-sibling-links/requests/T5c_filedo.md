# S0048 T5c - FileDO: the StreamsPlayer footer row

**From:** the StreamsPlayer Android session (`P:\ANDROID\StreamsPlayer_mob`, ticket S0048).
**To:** the FileDO session (`P:\WINDOWS\FileDo`).
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

Read 2026-11-08: the grid is hand-copied into 6 static pages (`docs/index.html`, `docs/privacy.html`, and `index.html` and
`privacy.html` under `docs/de/` and `docs/fr/`) and injected by `docs/guides/guide.js` into the guide pages; every locale in
English. The tile, in the form of the existing `STREAMS Player` tile:

```html
<a href="https://serzhyale.github.io/StreamsPlayer_Android/"><b>StreamsPlayer</b><span>Android and Wear OS stream player</span></a>
```

The `de` and `fr` pages link the same URL: the landing publishes `en`, `ru` and `uk` only, and chooses its language itself.

### A finding first - the test guard against `<b>StreamsPlayer</b>`

`cmd/filedo/fdsec_surfaces_test.go` (line 468 on 2026-11-08) fails a page that contains `<b>StreamsPlayer</b>`, a guard from
the 1.4 rename of the Windows row. With this row that string is correct on the Android tile, so the guard becomes: the tile
whose `href` is `https://serzhyale.github.io/StreamsPlayer/` is labelled `STREAMS Player`, and `<b>StreamsPlayer</b>` appears only
on the tile whose `href` is `https://serzhyale.github.io/StreamsPlayer_Android/`. The test's URL list and card list gain the new
row (`{"StreamsPlayer", "Android and Wear OS stream player"}`). The contact block is unchanged.

## Check this session runs

- `go test ./cmd/filedo/ -run Surfaces` (or the package's surface test) passes.

## Check run by the sender afterwards

- For each of the 6 static pages and one guide page, `curl -s <page> | grep -c StreamsPlayer_Android` is at least 1, the footer
  only (rule 4), and the label is `StreamsPlayer`.
- Result recorded in `temp/S0048/` and in the target table of `README.md` beside this file.
