# S0048 T2 - Fast Media Sorter & Organizer for Android: footer row, portal sections, body copy

**From:** the StreamsPlayer Android session (`P:\ANDROID\StreamsPlayer_mob`, ticket S0048).
**To:** the FastMediaSorter Android session (`P:\ANDROID\FastMediaSorter_mob_v2`), publisher of the media exchange portal.
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
`https://serzhyale.github.io/StreamsPlayer_Android/`. Place it after the `STREAMS Player` tile; the map orders it there.

Footer sources read 2026-11-08: `scripts/site/family-footer.json`, rendered by `scripts/site/render-family-footer.ps1` into
`index.html`, `index-ru.html`, `index-uk.html`, `nolegal.html`, `nolegal-ru.html`, `nolegal-uk.html`; and the en-to-target pairs of
`_data/landing/<lang>.json` used by `scripts/site/generate-landing-pages.ps1` for the other ten locales. 16 pages carry the grid.

Tile, in the form of the existing `STREAMS Player` tile:

```html
<a href="https://serzhyale.github.io/StreamsPlayer_Android/"><b>StreamsPlayer</b><span>Android and Wear OS stream player</span></a>
```

The type label in each locale, modelled on this site's own label for `Windows stream player`:

| Locale | Existing `STREAMS Player` label | New `StreamsPlayer` label |
| --- | --- | --- |
| en | Windows stream player | Android and Wear OS stream player |
| ru | Плеер потоков для Windows | Плеер потоков для Android и Wear OS |
| uk | Плеєр потоків для Windows | Плеєр потоків для Android і Wear OS |
| de | Windows-Stream-Player | Stream-Player für Android und Wear OS |
| es | Reproductor de transmisiones para Windows | Reproductor de transmisiones para Android y Wear OS |
| fr | Lecteur de flux Windows | Lecteur de flux Android et Wear OS |
| it | Lettore di stream per Windows | Lettore di stream per Android e Wear OS |
| pt | reprodutor de streams para Windows | reprodutor de streams para Android e Wear OS |
| hi | Windows स्ट्रीम प्लेयर | Android और Wear OS स्ट्रीम प्लेयर |
| bn | Windows স্ট্রিম প্লেয়ার | Android ও Wear OS স্ট্রিম প্লেয়ার |
| ar | مشغل بث لـ Windows | مشغل بث لـ Android وWear OS |
| ur | Windows اسٹریم پلیئر | Android اور Wear OS اسٹریم پلیئر |
| zh-hans | Windows 流媒体播放器 | Android 和 Wear OS 流媒体播放器 |

The label `StreamsPlayer` is the same in every locale (a proper name). The contact block is unchanged.

## Part B (G2) - the portal and the body copy

### B1. Three portal sections (owner decision D3 (a), no new page)

The sections are in `P:\ANDROID\StreamsPlayer_mob\docs\PORTAL_SECTIONS_STREAMSPLAYER.md`, EN, RU and UK, one block per page:
`broadcast-receive`, `broadcast-start`, `watch-exchange` (and their `-ru` and `-uk` files). The sender attaches the file's SHA-256
at sending; its labels were checked against the shipped phone and watch strings on 2026-11-08 (51 labels, 0 misses). Each block
goes in as a section `## StreamsPlayer` on its page, in all three languages in one edit, placed by the portal (before
`## Step-by-step guides` is the natural place). The portal's picture and links stay the portal's.

### B2. The "Which devices play" list of `broadcast-receive`

The existing three rows stay as they are (they describe FastMediaSorter, FastMedia Wear and STREAMS Player). One row is added
after the `STREAMS Player` row:

- EN: `- **An Android phone and a Wear OS watch** also play broadcasts in [StreamsPlayer](https://serzhyale.github.io/StreamsPlayer_Android/).`
- RU: `- **Телефон Android и часы Wear OS** также воспроизводят трансляции в [StreamsPlayer](https://serzhyale.github.io/StreamsPlayer_Android/).`
- UK: `- **Телефон Android і годинник Wear OS** також відтворюють трансляції в [StreamsPlayer](https://serzhyale.github.io/StreamsPlayer_Android/).`

### B3. Body copy on the portal index (`index.md`, `index-ru.md`, `index-uk.md`)

The device list, after the `STREAMS Player` row (read 2026-11-08 at line 45 / 48 / 48):

- EN: `- **Android phone and Wear OS watch** - [StreamsPlayer](https://serzhyale.github.io/StreamsPlayer_Android/) plays live broadcasts and goes live with the phone's microphone or camera and the watch's microphone.`
- RU: `- **Телефон Android и часы Wear OS** - [StreamsPlayer](https://serzhyale.github.io/StreamsPlayer_Android/) воспроизводит живые трансляции и ведёт вещание с микрофона или камеры телефона и с микрофона часов.`
- UK: `- **Телефон Android і годинник Wear OS** - [StreamsPlayer](https://serzhyale.github.io/StreamsPlayer_Android/) відтворює прямі трансляції та веде мовлення з мікрофона чи камери телефона й з мікрофона годинника.`

"The family of programs", after the `STREAMS Player` line (read at line 75 / 81 / 81):

- EN: `- [StreamsPlayer](https://serzhyale.github.io/StreamsPlayer_Android/) - internet radio, live streams and broadcasts on an Android phone and a Wear OS watch.`
- RU: `- [StreamsPlayer](https://serzhyale.github.io/StreamsPlayer_Android/) - интернет-радио, живые потоки и трансляции на телефоне Android и часах Wear OS.`
- UK: `- [StreamsPlayer](https://serzhyale.github.io/StreamsPlayer_Android/) - інтернет-радіо, прямі потоки й трансляції на телефоні Android і годиннику Wear OS.`

Rule 4 basis: this app sends live broadcasts; StreamsPlayer plays them on a phone and a watch and broadcasts its own
(`SITE-FAMILY-MAP` 1.11 rule 4, request T0).

### B4. README

Read 2026-11-08: the README has no "Related" section and does not name STREAMS Player. Nothing is asked here; a Related
section created only for this product would be footer duplication (rule 4).

## Checks this session runs

- Part A: the site's own footer conformance check over the 16 pages, and the map's URL check over the new URL.
- Part B: the portal's own gates (three languages in one edit, links resolve); `docs/MEDIA_EXCHANGE_PORTAL.md` of the
  StreamsPlayer repository is not touched.

## Check run by the sender afterwards

- Part A: for each of the 16 pages, `curl -s <page> | grep -c StreamsPlayer_Android` is at least 1 and the label is
  `StreamsPlayer`.
- Part B: the three portal pages answer 200 in EN, RU and UK and each contains `## StreamsPlayer` rendered and the
  `StreamsPlayer_Android` link; the Windows row still reads `STREAMS Player`.
- Results recorded in `temp/S0048/` and in the target table of `README.md` beside this file.
