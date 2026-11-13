# S0048 T4 - STREAMS Player for Windows: footer row and the Android sibling

**From:** the StreamsPlayer Android session (`P:\ANDROID\StreamsPlayer_mob`, ticket S0048).
**To:** the StreamsPlayer Windows session (`P:\WINDOWS\Streams_Player`).
**Drafted:** 2026-11-08. **Sent:** part A - (at G1); part B - (at G2).
**Gates:** part A at G1 (the landing answers 200 and the `SITE-FAMILY-MAP` 1.11 row is in the catalog); part B at G2 (the Play
listing answers 200; owner decision D5 (a) of 2026-11-08). State when drafted (2026-11-08): both answer 404.

## Evidence attached at sending

```
curl -s -o /dev/null -w "%{http_code} %{url_effective}\n" -L https://serzhyale.github.io/StreamsPlayer_Android/
curl -s -o /dev/null -w "%{http_code} %{url_effective}\n" -L "https://play.google.com/store/apps/details?id=com.sza.streamsplayer"   # part B only
```

## Part A (G1) - the footer row

The map row (`SITE-FAMILY-MAP` 1.11): Tool `StreamsPlayer`, Type `Android and Wear OS stream player`, URL
`https://serzhyale.github.io/StreamsPlayer_Android/`. This site omits its own row; the Android row is a sibling and is listed.

Footer source read 2026-11-08: `tools/site/templates/_footer.html`, rendered by `tools/site/build-site.ps1` with `{{t.type-*}}`
keys from `tools/site/copy/<locale>.txt`, into 66 pages.

### A1. The tile in `_footer.html`, after the `FileDO` tile (map order)

```html
          <a href="https://serzhyale.github.io/StreamsPlayer_Android/"><b>StreamsPlayer</b><span>{{t.type-android-stream-player}}</span></a>
```

### A2. The key in each `copy/<locale>.txt`, beside `@@type-android-sorter`

| File | `@@type-android-stream-player` |
| --- | --- |
| en.txt | Android and Wear OS stream player |
| ru.txt | Плеер потоков для Android и Wear OS |
| uk.txt | Плеєр потоків для Android і Wear OS |
| de.txt | Stream-Player für Android und Wear OS |
| es.txt | Reproductor de transmisiones para Android y Wear OS |
| fr.txt | Lecteur de flux pour Android et Wear OS |
| it.txt | Lettore di stream per Android e Wear OS |
| pt.txt | Reprodutor de streams para Android e Wear OS |
| hi.txt | Android और Wear OS स्ट्रीम प्लेयर |
| bn.txt | Android ও Wear OS স্ট্রিম প্লেয়ার |
| ar.txt | مشغل بث لنظامي Android وWear OS |
| ur.txt | Android اور Wear OS اسٹریم پلیئر |
| zh.txt | Android 和 Wear OS 流媒体播放器 |

The forms follow this site's own type labels (`Сортировщик медиа для Android`, `Trieur de médias pour Android`,
`فارز وسائط لنظام Android`); the session adjusts a word to its dictionary where its translators chose differently. The
contact block is unchanged.

## Part B (G2) - the Android sibling on the landing and in the README

Rule 4 basis: the two products are one stream catalog and one playlist format on two platforms - both read the `STREAM-BANK`
catalog and the `USER-PLAYLIST` format of the contracts catalog - and each covers a platform the other does not
(`SITE-FAMILY-MAP` 1.11 rule 4, request T0).

### B1. README (`README.md`, `README.ru.md`, `README.uk.md`)

Read 2026-11-08: no "Android sibling" line and no "Related" section exist. One sentence at the end of "What it does":

- EN: `On an Android phone and a Wear OS watch, the same catalog and the same playlist files play in [StreamsPlayer for Android](https://serzhyale.github.io/StreamsPlayer_Android/).`
- RU: `На телефоне Android и часах Wear OS тот же каталог и те же файлы плейлистов воспроизводит [StreamsPlayer для Android](https://serzhyale.github.io/StreamsPlayer_Android/).`
- UK: `На телефоні Android і годиннику Wear OS той самий каталог і ті самі файли плейлистів відтворює [StreamsPlayer для Android](https://serzhyale.github.io/StreamsPlayer_Android/).`

### B2. Landing

The same sentence in every locale the landing publishes, where the landing describes the catalog or the playlist import; the
session translates it into its other ten locales in the same edit, as it does for its own copy.

Optional, only after this session has played a live broadcast from StreamsPlayer for Android in this program: where the README
says it plays a live audio broadcast from a phone or watch running Fast Media Sorter & Organizer for Android (line 221 on
2026-11-08), the sentence may name StreamsPlayer for Android as a second source. Without that run it is not written.

## Checks this session runs

- `tools/site/build-site.ps1` regenerates the 66 pages; the site's own checks pass; every locale renders its
  `type-android-stream-player` label (no `{{t.` left in the output).

## Check run by the sender afterwards

- Part A: for each of the 66 pages, `curl -s <page> | grep -c StreamsPlayer_Android` is at least 1 and the tile label is
  `StreamsPlayer`.
- Part B: the README sentence is present in EN, RU and UK; the landing carries it in each locale.
- Results recorded in `temp/S0048/` and in the target table of `README.md` beside this file.
