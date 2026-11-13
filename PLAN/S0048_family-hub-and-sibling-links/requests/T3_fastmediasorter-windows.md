# S0048 T3 - Fast Media Sorter & Sharing for Windows: footer row and body copy

**From:** the StreamsPlayer Android session (`P:\ANDROID\StreamsPlayer_mob`, ticket S0048).
**To:** the FastMediaSorter Windows session (`P:\WINDOWS\FastMediaSorter_Lite`).
**Drafted:** 2026-11-08. **Sent:** part A - (at G1); part B - (at G2).
**Gates:** part A at G1 (the landing answers 200 and the `SITE-FAMILY-MAP` 1.11 row is in the catalog); part B at G2 (the Play
listing answers 200; owner decision D5 (a) of 2026-11-08). State when drafted (2026-11-08): both answer 404.

## Evidence attached at sending

```
curl -s -o /dev/null -w "%{http_code} %{url_effective}\n" -L https://serzhyale.github.io/StreamsPlayer_Android/
curl -s -o /dev/null -w "%{http_code} %{url_effective}\n" -L "https://play.google.com/store/apps/details?id=com.sza.streamsplayer"   # part B only
```

Part B also carries the sender's record of one run: a live broadcast started in Share Manager on this PC and played in
StreamsPlayer on an Android phone. Without that record at sending, part B is withheld and only part A goes.

## Part A (G1) - the footer row

The map row (`SITE-FAMILY-MAP` 1.11): Tool `StreamsPlayer`, Type `Android and Wear OS stream player`, URL
`https://serzhyale.github.io/StreamsPlayer_Android/`, placed after `STREAMS Player`.

Footer source read 2026-11-08: `tools/site-copy.json`, `_family.members` and `_familyTranslations`, rendered by
`RenderFamilyGridForLocale` in `tools/Build-SitePages.ps1` into the `<!-- gen:family -->` region of 26 pages (`index.html`,
`trust.html`, and `lang/<locale>/index.html` and `trust.html` for twelve locales).

### A1. A finding first - the translation keys of the Windows row

`_familyTranslations` keys the Windows row as `"StreamsPlayer"` (en, ru, uk), while the member's name, which the lookup uses, is
`"STREAMS Player"`; the ru and uk pages therefore show the English `Windows stream player` for that tile today. Adding a member
named `StreamsPlayer` as it stands would give the Android tile the Windows translation. So, in one edit:

```diff
-        "StreamsPlayer": "Windows stream player",
+        "STREAMS Player": "Windows stream player",
+        "StreamsPlayer": "Android and Wear OS stream player",
```

```diff
-        "StreamsPlayer": "Плеер стримов для Windows",
+        "STREAMS Player": "Плеер стримов для Windows",
+        "StreamsPlayer": "Плеер стримов для Android и Wear OS",
```

```diff
-        "StreamsPlayer": "Плеєр стрімів для Windows",
+        "STREAMS Player": "Плеєр стрімів для Windows",
+        "StreamsPlayer": "Плеєр стрімів для Android і Wear OS",
```

### A2. The member

```json
      {
        "name": "StreamsPlayer",
        "type": "Android and Wear OS stream player",
        "url": "https://serzhyale.github.io/StreamsPlayer_Android/"
      },
```

The other locales render the English type, as they do for every tile today. The contact block is unchanged.

## Part B (G2) - body copy where a broadcast from this PC is played

Rule 4 basis: this product broadcasts from a Windows PC; StreamsPlayer plays live broadcasts on an Android phone and a Wear OS
watch (`SITE-FAMILY-MAP` 1.11 rule 4, request T0). The places read 2026-11-08 that name the Windows player of a broadcast:

- `README.md` line 124, the "Live broadcasts on Windows" bullet, after its last sentence ("[STREAMS Player](..) is a separate player that
  can also receive live broadcasts."):
  - EN: ` On an Android phone and a Wear OS watch, [StreamsPlayer](https://serzhyale.github.io/StreamsPlayer_Android/) plays them too.`
  - `README_RU.md`, the same bullet: ` На телефоне Android и часах Wear OS их также воспроизводит [StreamsPlayer](https://serzhyale.github.io/StreamsPlayer_Android/).`
  - `README_UK.md`, the same bullet: ` На телефоні Android і годиннику Wear OS їх також відтворює [StreamsPlayer](https://serzhyale.github.io/StreamsPlayer_Android/).`
- Any site page that names STREAMS Player as a player of this PC's live broadcasts: the same sentence in that page's locales.

Not asked: the "Broadcasts for StreamsPlayer" feature (`.spres` resources) - StreamsPlayer for Android does not read `.spres`, so
no sentence there names it.

## Checks this session runs

- Part A: `tools/Build-SitePages.ps1` regenerates the 26 pages; the site's own family-grid check passes; the ru and uk pages
  show the localized types of both StreamsPlayer tiles.
- Part B: the README translations carry the sentence in the same edit.

## Check run by the sender afterwards

- Part A: for each of the 26 pages, `curl -s <page> | grep -c StreamsPlayer_Android` is at least 1; on `lang/ru/index.html` the
  tile reads `Плеер стримов для Android и Wear OS` and the Windows tile `Плеер стримов для Windows`.
- Part B: the README sentence is present in EN, RU and UK.
- Results recorded in `temp/S0048/` and in the target table of `README.md` beside this file.
