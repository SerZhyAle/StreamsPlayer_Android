# Product positioning - the source

Every external surface of StreamsPlayer for Android is written from this file: the README, the Play listing in English,
Russian and Ukrainian ([docs/play/PLAY_LISTING.md](docs/play/PLAY_LISTING.md)), the product landing and the repository
metadata. No surface is written from another surface; where two disagree, this file decides which one is wrong. The rule is
`SITE-REPRESENTATION` rules 1 and 2, and the surfaces it binds are in [DOCS_SURFACES.md](docs/DOCS_SURFACES.md).

This file names no other product: the alternatives below are generic, and the names behind them stay in the internal
research notes (`PLAN/S0014_free-promotion-campaign/research/`).

## 1. What the product is

One sentence, the atom every surface renders. One name for the thing everywhere: **StreamsPlayer**.

| en | ru | uk |
| --- | --- | --- |
| StreamsPlayer plays internet radio, live video and camera streams on your phone and watch, and broadcasts your microphone or camera live. | StreamsPlayer воспроизводит интернет-радио, live-видео и потоки с камер на телефоне и часах и ведёт прямую трансляцию с вашего микрофона или камеры. | StreamsPlayer відтворює інтернет-радіо, live-відео та потоки з камер на телефоні й годиннику та веде пряму трансляцію з вашого мікрофона чи камери. |

## 2. The pillars

What the product is for, in the order every surface lists it. A surface too short for all four names the first ones. The
terms are the words a surface uses in each language, and the same as the Windows product's, so a person who knows one
product reads the other.

| # | Pillar | What it covers | en | ru | uk |
| --- | --- | --- | --- | --- | --- |
| 1 | Internet radio | Radio stations, played straight from the list and kept playing in the background | Internet radio | Интернет-радио | Інтернет-радіо |
| 2 | Live video | Live video streams, opened in the fullscreen player | Live video | Live-видео | Live-відео |
| 3 | RTSP | Network camera and RTSP stream addresses, opened in the same player | RTSP | RTSP | RTSP |
| 4 | Live broadcast | The phone's microphone or camera and the watch's microphone, played on another device | Live broadcast | Прямая трансляция | Пряма трансляція |

A term that is the same in all three languages (RTSP) is not translated. The listing and any landing speak to the person
first: radio and live video lead, RTSP and broadcast follow (the compass conflict of S0014 section 2.3).

## 3. The worksheet

Canon `PROMOTION.md` section 2, in Dunford's order.

### 3.1 Competitive alternatives

What the person does today without this product (research of 2026-11-13, internal notes):

1. A radio app tied to one directory, with its own ads and a paid tier; the station wanted is missing or buried.
2. A general video player where the stream address is pasted again every time.
3. A camera maker's own app, or a dedicated viewer, for a network camera.
4. A watch that has no radio of its own, or a radio app whose watch build is a separate install.
5. Nothing: a browser tab.

### 3.2 Unique attributes

Each has a pointer to code. An attribute without one at listing time is cut.

| # | Attribute | Pointer |
| --- | --- | --- |
| A1 | Radio plays straight from the list and video opens in a fullscreen player; the media type decides which | `phone/src/main/java/com/sza/streamsplayer/ui/player/AudioPlaybackService.kt`, `phone/src/main/java/com/sza/streamsplayer/ui/player/FullscreenPlayerActivity.kt`, `phone/src/main/java/com/sza/streamsplayer/domain/usecase/streams/StreamMediaKindClassifier.kt` (S0003, S0005) |
| A2 | One catalog beside your own channels, with pins and collections; a newer catalog is offered when one is published; a playlist file imports | `phone/src/main/java/com/sza/streamsplayer/ui/streams/StreamsViewModel.kt`, `phone/src/main/java/com/sza/streamsplayer/domain/usecase/streams/ImportStreamPlaylistUseCase.kt`, `phone/src/main/java/com/sza/streamsplayer/data/repository/M3uPlaylistParser.kt` (S0004, S0009) |
| A3 | RTSP camera streams open in the same player | `phone/src/main/java/com/sza/streamsplayer/ui/player/FullscreenPlayerActivity.kt` (S0003, S0005) |
| A4 | Live broadcast of the phone's microphone and camera and of the watch's microphone, played by another device from a link, a QR code or a file | `phone/src/main/java/com/sza/streamsplayer/broadcast/BroadcastCaptureService.kt`, `phone/src/main/java/com/sza/streamsplayer/broadcast/VideoBroadcastService.kt`, `wear/src/main/java/com/sza/streamsplayer/wear/ui/broadcast/WearBroadcastViewModel.kt` (S0006, S0007) |
| A5 | A Wear OS app and a watch face | `wear/src/main/java/com/sza/streamsplayer/wear/MainActivity.kt`, `watchface/src/main/res/raw/watchface.xml` (S0007, S0008) |
| A6 | A channel widget and a quick settings tile for radio | `phone/src/main/java/com/sza/streamsplayer/widget/StreamLaunchWidgetProvider.kt`, `phone/src/main/java/com/sza/streamsplayer/ui/player/AudioToggleTileService.kt` (S0009) |
| A7 | English, Russian and Ukrainian from the first release | `phone/src/main/res/values/strings.xml`, `values-ru`, `values-uk` (handoff section 3) |

Not claimed on any surface, for want of a pointer: touch, gamepad and TV-remote parity and TalkBack coverage (the gaps of
S0052 are open), and "no background surprises" (no measured pointer).

### 3.3 Value

In the person's words, jobs-to-be-done form:

- When I want something playing in the background, I want to pick a station from one list and hear it, so I can listen
  without hunting through apps. (A1, A2)
- When I only have my watch with me, I want to start a channel from the wrist, so I can listen without taking out the phone.
  (A5)
- When someone sends me a link to a live video or I have a camera address, I want to open it directly, so I do not need
  another viewer. (A1, A3)
- When I want another device to hear or see what my phone hears or sees, I want to start a broadcast and hand over a link
  or a QR code, so I can share live without an account on someone else's service. (A4)

### 3.4 Target persona

Primary: ordinary non-technical people who listen to internet radio or watch a live stream on a phone or a watch (the
product compass default). Secondary, never mixed into one post (S0014 decision 4): the person with a network camera, and
the person who broadcasts the phone's microphone or camera or the watch's microphone.

### 3.5 Market category

**Internet radio player**, with "live stream player" as the second phrase. The category is one people already search for;
radio is how the primary persona reaches the product. The category fixes the category term of the Play title and the first
search phrase of every locale, and nothing else.

## 4. Components and install intent

Components, installer profiles and distribution channels are separate facts. Three packages do not make three products.

| Component | Purpose | Relation to the others | Channel today |
| --- | --- | --- | --- |
| Phone app | the whole product: catalog, players, broadcast, exchange | stands alone; needs no watch and no server | Google Play, `store` edition |
| Wear OS app | the same channels, players and a broadcast from the wrist | optional companion of the phone app, same application id | Google Play, `store` edition |
| Watch face | a StreamsPlayer face for Wear OS | optional; a separate package | Google Play, `store` edition |

The exchange server is optional: receiving a broadcast needs no account, and the phone works without any server. The
`noLegal` edition has no channel and is absent from every published surface until one opens. Default family order on
every surface: phone app, Wear OS app, watch face.

## 5. Discoverability fields

Derived from sections 1 to 3; the gate `tools/promotion/assert-promotion.ps1` reads these lines and the listing source.

Topics: android, wear-os, kotlin, internet-radio, radio-player, live-streaming, stream-player, hls, dash, rtsp, watch-face, live-broadcast, media3

Search terms en: internet radio, online radio, radio player, live stream player, live video player, radio for watch, wear os radio

Search terms ru: интернет-радио, радио онлайн, радио плеер, плеер прямых трансляций, live-видео плеер, радио для часов, wear os радио

Search terms uk: інтернет-радіо, радіо онлайн, радіо плеєр, програвач прямих трансляцій, live-відео плеєр, радіо для годинника, wear os радіо

The Play title is the product name, a colon and the category term; the short and full descriptions are in
[docs/play/PLAY_LISTING.md](docs/play/PLAY_LISTING.md), [Russian](docs/play/PLAY_LISTING-ru.md) and
[Ukrainian](docs/play/PLAY_LISTING-uk.md). The GitHub description is the sentence of section 1 in English; the topics are
the line above, at most twenty. The contact on every surface is `sza@ukr.net`.

## 6. Where it is checked

`pwsh -NoProfile -File tools/promotion/assert-promotion.ps1` ([tools/promotion/README.md](tools/promotion/README.md)):

- no term of the forbidden-terms list (the piracy-signal class and the other products' names) in any published source, and
  none of the field-only terms in a topic, a search term or a listing field;
- the Play title, short description and full description inside their limits, in all three languages;
- at most twenty topics, and every contact stated is `sza@ukr.net`;
- the listing says the pillar terms of section 2 in their order.

## 7. Changing it

Change this file first. A surface whose wording turns out to be wrong is fixed here before it is fixed there, or the drift
starts again. A new pillar is a new row, and every surface above is then rewritten to list it in its place.
