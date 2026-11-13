# S0048 T0 - contracts catalog: the StreamsPlayer row of `SITE-FAMILY-MAP`

**From:** the StreamsPlayer Android session (`P:\ANDROID\StreamsPlayer_mob`, ticket S0048).
**To:** the canon session `sza-unified-rules-dc`, which routes the map change to the hub as owner of `SITE-FAMILY-MAP`
(`CLAUDE.md` section 6 of the sender).
**Drafted:** 2026-11-08. **Sent:** - (filled at sending).
**Gate:** G1 - `SITE-FAMILY-MAP` rule 5, a URL enters the table only once it answers. Not sent before both URLs below answer
200. State when drafted (2026-11-08): both answer 404.

## Evidence attached at sending

Run on the day of sending; the output is pasted here:

```
for u in https://serzhyale.github.io/StreamsPlayer_Android/ https://serzhyale.github.io/StreamsPlayer_Android/index-ru.html https://serzhyale.github.io/StreamsPlayer_Android/index-uk.html https://serzhyale.github.io/StreamsPlayer_Android/help/en/; do curl -s -o /dev/null -w "%{http_code} $u\n" -L "$u"; done
```

## Reason

StreamsPlayer for Android (phone app, Wear OS app, watch face) is a product of the family with its own landing. The owner
decided on 2026-11-08 (StreamsPlayer_mob S0048 D1 and D2, S0045 D1 and D2): the `Tool` label is `StreamsPlayer`, the
application's own display name (`app_name`, the launcher label, the Play title); the Windows row keeps `STREAMS Player`; the
`Type` column tells the two apart; the help index is published with the first release at `help/en/`. Adding a row is
additive (section 4): a page that has not caught up shows a grid one entry short and misstates nothing.

## Exact diff - `product-web-pages/SITE-FAMILY-MAP.md`

Header block:

```diff
-version: 1.10
+version: 1.11
-consumers: sza.od.ua hub, FastMediaSorter_mob_v2, FastMediaSorter_Lite, CyrFlip, doc-html-translate, FileDO, universal-agent-kit, OneClickRunner, StreamsPlayer
+consumers: sza.od.ua hub, FastMediaSorter_mob_v2, FastMediaSorter_Lite, CyrFlip, doc-html-translate, FileDO, universal-agent-kit, OneClickRunner, StreamsPlayer, StreamsPlayer_mob
```

Section 2, the row after `STREAMS Player`:

```diff
 | STREAMS Player | Windows stream player | https://serzhyale.github.io/StreamsPlayer/ | - |
+| StreamsPlayer | Android and Wear OS stream player | https://serzhyale.github.io/StreamsPlayer_Android/ | https://serzhyale.github.io/StreamsPlayer_Android/help/en/ |
 | OneClickRunner | Windows tray launcher | https://serzhyale.github.io/OneClickRunner/ | - |
```

Rule 4, the contextual pairs the sibling body copy of this change relies on (additive; the catalog session decides whether it
belongs in the same version):

```diff
-   original. A README "Related project" section that promotes a sibling which does not cover what this
+   original. *(1.11)* STREAMS Player and StreamsPlayer, the Windows and the Android players of one stream catalog and one
+   playlist format; and StreamsPlayer with FMS-mob and FMS-Lite, as a player of the live broadcasts they send.
+   A README "Related project" section that promotes a sibling which does not cover what this
```

Section 7, the document-log row (date = the day of sending):

```diff
+| <date of sending> | 1.11 | additive | Section 2 gains the row **StreamsPlayer** - Android and Wear OS stream player - with its landing and its help index `help/en/` (the `ru` and `uk` routes are linked from the index). The `Tool` cell is the application's own display name (`app_name`, launcher label, Play title), the same reasoning as 1.3 and 1.4; the Windows row keeps **STREAMS Player** and the `Type` column tells the two apart. Owner decisions of 2026-11-08 (StreamsPlayer_mob S0048 D1, D2; S0045 D1, D2). Rule 4 names the two pairs this product genuinely completes. Evidence, <date of sending>: `curl -L -o /dev/null -w "%{http_code}"` returned 200 for the landing, `index-ru.html`, `index-uk.html` and `help/en/`. Additive: a page that has not caught up shows a grid one entry short and misstates nothing. |
```

## Exact diff - consumer lines of `PAGE-CONTENT` and `PAGE-STYLE`

```diff
-consumers: sza.od.ua hub, FastMediaSorter_mob_v2, FastMediaSorter_Lite, CyrFlip, doc-html-translate, FileDO, universal-agent-kit, OneClickRunner, StreamsPlayer
+consumers: sza.od.ua hub, FastMediaSorter_mob_v2, FastMediaSorter_Lite, CyrFlip, doc-html-translate, FileDO, universal-agent-kit, OneClickRunner, StreamsPlayer, StreamsPlayer_mob
```

Whether a consumer-line change takes a version on those two documents is the catalog's call under `_meta/VERSIONING.md`.

## Exact diff - `_meta/REGISTRY.md`

Section 1, the `SITE-FAMILY-MAP` row: `1.10` -> `1.11`.

Section 2, three adoption rows for the product `StreamsPlayer Android`, beside its other rows. `Implements` and `Reads` are the
versions read off the published landing on the day of sending (StreamsPlayer_mob S0045 publishes it); the notes cell is written
by the sender from that reading, in the form of the other rows:

```
| `SITE-FAMILY-MAP` | StreamsPlayer Android | C | <version read> | 1.11 | <date> | <landing footer grid and contact read off the rendered pages> |
| `PAGE-CONTENT` | StreamsPlayer Android | C | <version read> | 1.5 | <date> | <landing read against "The landing always carries"> |
| `PAGE-STYLE` | StreamsPlayer Android | C | <version read> | 3.5 | <date> | <vendored kit hash, landing and privacy pages> |
```

## Check the catalog session runs

- The map's own conformance command (section 5) over every section 2 URL and every non-empty help-index cell: 200 for each.
- `pwsh -NoProfile -File <canon>/tools/check-contracts.ps1` (or the catalog's current registry gate): exit 0.

## Check run by the sender afterwards

- The catalog's `SITE-FAMILY-MAP.md` holds the row line above verbatim and `version: 1.11`; the document log holds the 1.11 row.
- The registry's section 1 row reads 1.11 and section 2 holds the three `StreamsPlayer Android` rows.
- Result recorded in `temp/S0048/` and in the target table of `README.md` beside this file.
