# StreamsPlayer for Android

StreamsPlayer is a calm player for internet radio, live video (HLS/DASH) and RTSP streams: a channel
catalog with search and filters, your own channels, pins and collections, radio that plays straight from
the list, and a fullscreen player for video. It also broadcasts live: the phone its microphone and
camera, the watch its microphone, for another device to play. Three installable packages: the phone app,
the Wear OS app and a StreamsPlayer watch face.

It is the Android sibling of [StreamsPlayer for Windows](https://github.com/SerZhyAle/StreamsPlayer) and
an independent product: it shares no application code with the other products of the family, only the
published data contracts (the stream bank catalog, the user playlist format).

## Features

The phone has a searchable channel catalog with list and grid views, filters, pins and collections,
manual channels, and a persistent radio player with live track metadata. Channel pictures show a video channel's
preview or the station's logo in the list and the grid: the phone offers them after a catalog update, and
**Settings > General** removes them or fetches them again; the watch downloads them itself, and its **Channel pictures**
screen does the same. When a newer channel
catalog is published, the phone offers it in a bar above the list, a fresh install included; **Settings > General**
chooses whether it suggests, stays silent until you ask, or updates by itself on Wi-Fi.

Import a live broadcast by pasting its link or descriptor, opening its file, or scanning its QR code.
Playback tries the descriptor's supported endpoints in order, including the paths through the exchange server and the TLS tunnels,
and starts that order again when the network changes. Receiving a shared broadcast needs no account
on its exchange server. A foreign server's accepted certificate is kept for that broadcast;
a changed certificate is refused.

Sign in once: paste the connection string or scan the QR the host of the server gave you, type the
password - the login is already filled in when the string carries it - and the phone stays signed
in until the server says otherwise. The server's address shows as one small line under the login;
IP, port, firewall and router details stay out of sight. If the server refuses the phone later
(the account's password was changed, the device was removed), the entry, its kept channels and its
identity stay, and the screen says in words why it asks you to sign in again; the app never
retries a password by itself. Accounts are created in Fast Media Sorter & Organizer for Android or Fast Media Sorter & Sharing for Windows; this
program only signs in. On the account screen, **Change password** signs every other device of the account
out, and **Sign out on this device** keeps your account on the server and your kept channels. Once the
phone is signed in, every broadcast it starts is announced on that server from its start, with no
switch, and Broadcast Control names the server and the state of the broadcast there; without a server
it says the broadcast is reachable on this network only. A guest invitation signs the phone in as a
guest: a guest sees only what was shared with it and cannot publish broadcasts or announce them via
Google Drive.

**Settings**, in the catalog's menu, holds the theme and the language, both applied at once without a restart, the
broadcast settings, and an About page with the version, the license and privacy pages, help, support, the source
repository, the update page, the diagnostic report and the related products.

An exchange account also receives broadcasts sent from your own devices. Each offer asks before
playing. **Play broadcasts sent from my devices without asking** on the account screen enables
automatic acceptance while the app is open; it is off by default. Stopping the cast or ending the
broadcast stops its player.

On the exchange account screen, **Connect watch** enrolls a connected Wear OS watch using a one-time
pairing code. The watch keeps its own encrypted device token; no account password is sent to it.
With the watch connected, every watch broadcast is announced on the exchange server from its start, over the
watch's own Wi-Fi or cellular connection, and the watch's Broadcast screen says whether it is on the server.
The watch supports audio broadcasts with a total limit of four listeners across the local network, the relay through the server and the tunnel.
Outside a broadcast, exchange connections close when the broadcast/player screen is left or the display turns off.
Audio broadcast descriptors can also be sent from the phone to the watch.

Broadcasts can also be found via Google Drive, with no server and no typed address. On the Exchange
screen, **Sign in with Google** with your own account, and the broadcasts that your other devices announce
via Google Drive are listed there. **Announce via Google Drive** in the broadcast settings is off by
default and is remembered separately for audio and for the camera; while a broadcast runs, it writes a small
announcement file (the title, this phone's name and the addresses a listener can try) to the hidden app folder
of your Drive and removes it when the broadcast stops. The app asks Google for the app-folder permission only;
the sound and the picture never go through Drive. **Sign out of Google Drive** does not touch your exchange
account.

## Media exchange and broadcasting portal

The shared [media exchange portal](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/)
explains how the family connects devices, shares media and plays live broadcasts. See
[the portal page directory](docs/MEDIA_EXCHANGE_PORTAL.md) for every topic in English, Russian and Ukrainian.
In the phone app, open the toolbar menu and choose **Media exchange and broadcasts**, then a topic. The exchange server
screen, the broadcast settings and the watch's Broadcast screen also link the page that explains that screen, in the
app language (English when the portal has no page in it).

## Help

Press the **?** at the end of a screen's top bar, or **F1** with a hardware keyboard, to open the help page of that
screen: a picture with numbered markers, the usual order of use, every element explained, and links to the
neighbouring screens. A sheet or dialog opened from the catalog (channel actions, recently played, the sleep timer, the
two imports) has the **?** in its title row and opens the catalog page at the entry about it. The pages are bundled in the package, work without a network and come in English, Russian and
Ukrainian (the other interface languages show the English pages): the index is
[English](phone/src/main/assets/help/en/index.html), [Russian](phone/src/main/assets/help/ru/index.html) and
[Ukrainian](phone/src/main/assets/help/uk/index.html). They are generated from `docs/help/content/` by
`tools/help/generate-help-pages.ps1`; [tools/help/README.md](tools/help/README.md) says how the set is kept complete.

The watch has a **Help** row on its start screen. It opens the page about the watch app on the phone, in the phone's
language, and that page explains the watch's channels, players, broadcast, QR code and tile screens.

## Privacy

The developer receives none of your data: the app has no analytics, advertising or tracking, runs no server and
creates no account. What it stores, what it sends and where, every permission with its purpose, the exchange
server, Google Drive and encryption are in the [privacy policy](docs/PRIVACY_POLICY.md)
([RU](docs/PRIVACY_POLICY-ru.md), [UK](docs/PRIVACY_POLICY-uk.md)), which is also published as the pages under `site/`.
The permissions table in it is generated from the merged store release manifests (`tools/privacy/permissions.ps1 -Build`
checks it), and the Play Data safety answers are in [docs/play/PLAY_DATA_SAFETY.md](docs/play/PLAY_DATA_SAFETY.md).

## Building

JDK 17, 21 or 25 and the Android SDK. Put the SDK path in a `local.properties` of your own
(`sdk.dir=..`, not tracked), then:

```
./gradlew assembleDebug          # phone, Wear OS app and watch face, both editions of the first two
./gradlew testStoreDebugUnitTest testNoLegalDebugUnitTest
./gradlew -p build-logic :convention:test
```

Release packages are built unsigned unless a release key is configured (`keystore.properties`).

### GitHub builds

Use PowerShell 7 and an authenticated [GitHub CLI](https://cli.github.com/manual/gh_workflow_run):

```powershell
./a.ps1 r                         # signed store release APKs and AABs, all three modules
./a.ps1 d                         # store debug APKs, all three modules
./a.ps1 r -Flavor noLegal          # optional noLegal edition
./a.ps1 d -Module phone            # phone only
./a.ps1 r -NoWait                  # dispatch and print the run URL
./a.ps1 d -DryRun                  # preview without contacting GitHub
```

The launcher builds the selected remote ref (`main` by default), waits for that exact run and
downloads verified packages to `DOWNLOADS/<run-id>/`. Local edits reach the build only after they
are committed and pushed. `-Repo owner/repo` and `-Ref branch-or-tag` override the defaults.
The workflow can also be started in GitHub Actions under **Android packages**.

Before the first build, create/push the project to `SerZhyAle/StreamsPlayer_Android`, including
`.github/workflows/android-build.yml` on the default branch. Debug needs no signing secrets.
Release requires these repository Actions secrets, using the existing product key:

| Secret | Value |
| --- | --- |
| `STREAMSPLAYER_KEYSTORE_BASE64` | Base64 encoding of the existing release keystore |
| `STREAMSPLAYER_STORE_PASSWORD` | Keystore password |
| `STREAMSPLAYER_KEY_ALIAS` | Existing release key alias |
| `STREAMSPLAYER_KEY_PASSWORD` | Key password |

Upload secrets with `gh secret set --repo SerZhyAle/StreamsPlayer_Android`, supplying values via
standard input or its hidden interactive prompt. Never put passwords in command arguments or
tracked files. The runner decodes signing material outside the checkout and removes it afterwards.
Missing secrets or a certificate differing from the established product key fail the release build.

Each build runs applicable unit tests, verifies APK versions and signatures, and includes
checksums and a build verdict. Release also checks port drift against FastMediaSorter and includes
R8 mappings and signed AABs. All modules share one mechanically derived versionName; versionCode
uses each family's existing band. Release builds dispatched on a version tag pin that tag's encoded
instant instead of reading the clock. Debug APKs within one run share a key for phone/watch pairing;
fresh GitHub runners generate a different debug key in subsequent runs, so debug updates across
runs can require reinstalling. Release files are prepared for publication. Store uploads, GitHub
Releases, release tags and the device update pre-flight are separate owner-operated steps. Android lint
belongs to that publication pre-flight; package assembly does not certify an application as ready to ship.

## Related

- [StreamsPlayer for Windows](https://serzhyale.github.io/StreamsPlayer/) - the same stream bank and playlist formats on
  the desktop.
- [Fast Media Sorter & Organizer](https://serzhyale.github.io/FastMediaSorter_mob_v2/) and
  [Fast Media Sorter & Sharing for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/) - the exchange server
  and its accounts, which this app signs in to.
- [Media exchange between your devices](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/) -
  the shared portal of the family.
- [SZA](https://sza.od.ua) - every product of the family.

## Contact

Email [sza@ukr.net](mailto:sza@ukr.net), GitHub [SerZhyAle](https://github.com/SerZhyAle). Questions and bug reports are
welcome as [GitHub issues](https://github.com/SerZhyAle/StreamsPlayer_Android/issues).

This project is developed with heavy AI assistance; the author reviews every change.

## License

[MIT](LICENSE).
