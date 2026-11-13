# S0048 T5e - Universal Agent Kit: the StreamsPlayer footer row

**From:** the StreamsPlayer Android session (`P:\ANDROID\StreamsPlayer_mob`, ticket S0048).
**To:** the Universal Agent Kit session (`P:\WEB\universal-agent-kit`).
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

Read 2026-11-08: the grid of `privacy.html`, `404.html` and the 52 portal pages comes from the `$tools` list of the `Footer`
function in `tools/portal/render.ps1` (lines 212-222); `index.html` (about line 1405) is hand-maintained; the tiles show the
name only, in every language of the in-page switch. The entries, in the form of the existing ones:

```powershell
        @('StreamsPlayer', 'https://serzhyale.github.io/StreamsPlayer_Android/'),
```

```html
      <a href="https://serzhyale.github.io/StreamsPlayer_Android/" target="_blank" rel="noopener">StreamsPlayer</a>
```

A name-only grid shows `STREAMS Player` and `StreamsPlayer` side by side with nothing telling them apart; the map's `Type`
column is what does that elsewhere. Whether this grid takes the type line (`Windows stream player`, `Android and Wear OS stream
player`) is the session's choice. The contact block is unchanged.

## Check this session runs

- `tools/portal/render.ps1` regenerates the 54 pages; the site's own contract-sync check passes.

## Check run by the sender afterwards

- For `index.html`, `privacy.html` and one portal page, `curl -s <page> | grep -c StreamsPlayer_Android` is 1 (the footer only,
  rule 4) and the label is `StreamsPlayer`.
- Result recorded in `temp/S0048/` and in the target table of `README.md` beside this file.
