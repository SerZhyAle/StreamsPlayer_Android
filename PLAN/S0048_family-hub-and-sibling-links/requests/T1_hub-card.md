# S0048 T1 - sza.od.ua hub: the StreamsPlayer card for Android and Wear OS

**From:** the StreamsPlayer Android session (`P:\ANDROID\StreamsPlayer_mob`, ticket S0048).
**To:** the hub session (`P:\WEB\sites.google.comsiteszaodua`).
**Drafted:** 2026-11-08. **Sent:** - (filled at sending).
**Gate:** G1 - the landing answers 200 and the `SITE-FAMILY-MAP` row of request T0 is in the catalog. State when drafted
(2026-11-08): the landing answers 404.
**Owner decision:** D1 (a), 2026-11-08 - a new card "StreamsPlayer" for Android and Wear OS next to the Windows card; the
hub's cards are one link each (`SITE-FAMILY-MAP` rule 2). The card order is the hub's.

## Evidence attached at sending

```
curl -s -o /dev/null -w "%{http_code} %{url_effective}\n" -L https://serzhyale.github.io/StreamsPlayer_Android/
```

The icon file and its SHA-256 are attached by the sender: the application icon PNG the landing shows in its identity block
(StreamsPlayer_mob S0045), copied as `assets/streamsplayer_android.png`.

## The change - four edits per page, in `index.html` and `embed.html` alike

The hub's own rule: `setLanguage()` overwrites `textContent` from the `i18n` dictionaries, so every card edit is the inline
fallback plus the `en`, `ru` and `uk` dictionaries; `embed.html` stays byte-identical to `index.html`. The keys below continue
the hub's numbering (`gh-card-9-*`); rename them freely if the hub numbers by position.

### 1. Inline card (suggested place: after the `STREAMS Player` card, `<!-- 8 -->`)

```html
        <!-- 9 -->
        <article class="card project-card">
          <div class="proj-head">
            <img src="assets/streamsplayer_android.png" alt="StreamsPlayer icon">
            <div><div class="proj-title">StreamsPlayer</div><span class="pill" data-i18n="gh-card-9-type">Android &amp; Wear OS App</span></div>
          </div>
          <p class="proj-desc" data-i18n="gh-card-9-desc">Listen to internet radio and watch live video and RTSP streams on an Android phone and a Wear OS watch: search the channel catalog, keep your own channels and collections, and broadcast live from the phone's microphone or camera or from the watch's microphone.</p>
          <div class="proj-foot btn-row">
            <a href="https://serzhyale.github.io/StreamsPlayer_Android/" class="btn" target="_blank" rel="noopener noreferrer"><span data-i18n="open-project">Open project</span><span class="gi gi-external" aria-hidden="true"></span><span class="sr">, <span data-i18n="ext-window">Open in new window</span></span></a>
          </div>
        </article>
```

### 2. `en` dictionary

```
        "gh-card-9-type":"Android & Wear OS App",
        "gh-card-9-desc":"Listen to internet radio and watch live video and RTSP streams on an Android phone and a Wear OS watch: search the channel catalog, keep your own channels and collections, and broadcast live from the phone's microphone or camera or from the watch's microphone.",
```

### 3. `ru` dictionary

```
        "gh-card-9-type":"Приложение для Android и Wear OS",
        "gh-card-9-desc":"Слушайте интернет-радио и смотрите прямые видеотрансляции и RTSP-потоки на телефоне Android и часах Wear OS: ищите в каталоге каналов, храните свои каналы и коллекции и ведите прямое вещание с микрофона или камеры телефона или с микрофона часов.",
```

### 4. `uk` dictionary

```
        "gh-card-9-type":"Застосунок для Android і Wear OS",
        "gh-card-9-desc":"Слухайте інтернет-радіо й дивіться прямі відеотрансляції та RTSP-потоки на телефоні Android і годиннику Wear OS: шукайте в каталозі каналів, зберігайте власні канали й колекції та ведіть пряме мовлення з мікрофона чи камери телефона або з мікрофона годинника.",
```

## The hub README product list

Under `### Android Apps`, after the Fast Media Sorter & Organizer line:

```
*   **[StreamsPlayer](https://serzhyale.github.io/StreamsPlayer_Android/)** - Android and Wear OS player for internet radio, live video, and RTSP streams, with live broadcasting from the phone and the watch.
```

The `STREAMS Player` line under Windows stays as it is.

## Checks the hub runs

- `diff -q index.html embed.html` exits 0; state both SHA-256 hashes in the reply.
- `pwsh -File tools/check.ps1` (the compliance gate) exits 0; the hub's UI check passes.
- The deploy is `deploy.bat`, only on the owner's confirmation (a site update is not a release).

## Check run by the sender afterwards

- `curl -s https://sza.od.ua | grep -c StreamsPlayer_Android` returns 1 (the card's one link) and the dictionaries hold the three
  `gh-card-9-*` pairs (`curl -s https://sza.od.ua | grep -c gh-card-9-desc` returns 4: the inline card and three dictionaries).
- `curl -s https://sza.od.ua/embed.html` returns the same bytes as `https://sza.od.ua/` (SHA-256 compared).
- Result recorded in `temp/S0048/` and in the target table of `README.md` beside this file.
