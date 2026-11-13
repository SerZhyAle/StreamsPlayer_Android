# Play listing - English

The text of the three store fields for the product's own page on Google Play, written from [POSITIONING.md](../../POSITIONING.md)
(the sentence of section 1, the pillars of section 2). The Russian text is [PLAY_LISTING-ru.md](PLAY_LISTING-ru.md) and the
Ukrainian [PLAY_LISTING-uk.md](PLAY_LISTING-uk.md); the headings are the console's field names in all three. Limits: title 30
characters, short description 80, full description 4000; the gate `tools/promotion/assert-promotion.ps1` holds them.

## Title

StreamsPlayer: Internet Radio

## Short description

Internet radio, live video and camera streams on your phone and watch

## Full description

StreamsPlayer plays internet radio, live video and camera streams on your phone and watch, and broadcasts your microphone or camera live.

Internet radio
Pick a station in the catalog and hear it straight from the list. Radio keeps playing in the background, shows the live track title and stops by itself with the sleep timer.

Live video
Video channels open in a fullscreen player. HLS and DASH streams are supported, and the catalog tells radio from video on its own.

RTSP
Open a network camera by its RTSP address in the same player.

Your own channels
Add a stream by its address or import a playlist file, pin your favourites and group them into collections. Search and filters find a channel in a long list. When a newer channel catalog is published, the app offers it.

On your watch
The Wear OS app brings your channels, its own players and a broadcast screen to your wrist. The StreamsPlayer watch face is a separate package for the watch.

Live broadcast
Broadcast the phone's microphone or camera, or the watch's microphone, and play it on another device from a link, a QR code or a file. On your own network no account is needed to listen. Sign in once to an exchange server of your own to announce broadcasts and play the ones sent from your other devices.

Home screen and quick settings
A channel widget starts a stream from the home screen, and a quick settings tile switches the radio on and off.

Privacy
The developer receives none of your data: no analytics, no advertising, no tracking. The microphone and the camera are used only for a broadcast you start.

Interface languages: English, Russian and Ukrainian.

## Console fields (2026-11-13)

Entered in the Play Console for `com.sza.streamsplayer` from this file and its two translations: App name, short and
full description in en-US (default), ru-RU and uk. Store settings: category Music & Audio, contact email `sza@ukr.net`,
website empty until the landing `https://serzhyale.github.io/StreamsPlayer_Android/` answers (S0045), then the landing,
never the repository. Graphics: `icon-512.png` and `feature-graphic-1024x500.png` beside this file, render targets of
`tools/store/render-store-assets.ps1`; the other languages fall back to them. Screenshots are not uploaded (S0072).
The watch face app `com.sza.streamsplayer.watchface` has category Personalization and the same contact; its listing has
no source yet (S0072 step 4).
