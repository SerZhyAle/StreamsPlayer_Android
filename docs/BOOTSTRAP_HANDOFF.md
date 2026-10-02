# Bootstrap handoff - StreamsPlayer for Android

The starting brief for this repository, written on 2026-10-02 by the session that decided the repository
boundary (FastMediaSorter ticket S4053, `P:\ANDROID\FastMediaSorter_mob_v2\PLAN\S4053_streamsplayer-standalone-product.md`).
It is an input, not a living document: once this repository has its own ticket store, the open items
below become tickets here, and this file is kept as the record of where the product came from.

## 1. Decision

- StreamsPlayer for Android lives in this separate repository (ADR-1 of S4053, accepted by the owner on
  2026-10-02).
- Three installable packages: phone app, Wear OS app, watch face.
- The only coupling to FastMediaSorter is the shared contracts catalog. No shared code, no shared
  Gradle module, no shared release - the same philosophy as StreamsPlayer for Windows.
- Why not a module inside FastMediaSorter: its release orchestrator stamps one version pair for the whole
  repository and its release-scope gates measure the whole tree, so a second product inside would double
  every release run and tie two release calendars together for good. The seams (shared player, shared
  database, shared DI graph) have to be cut in either variant. Full reasoning:
  `P:\ANDROID\FastMediaSorter_mob_v2\PLAN\S4053_streamsplayer-standalone-product\research\01__repo-boundary.md`.

## 2. Product

Goals:

1. A standalone phone app: channel catalog, radio playing straight from the list, HLS/DASH/RTSP video in a
   fullscreen player - without FastMediaSorter installed.
2. A standalone Wear OS app: channel list, pinned channels, playback on the watch, and handing a channel
   to the StreamsPlayer phone companion.
3. A StreamsPlayer-branded watch face.
4. Independent release: own application ids, own signing key, own release cycle.
5. Look and behaviour continue StreamsPlayer for Windows - the "calm player": catalog, own channels,
   correct media type, no background surprises.
6. **Live broadcast, despite the product name** (owner, 2026-10-02): the ready FastMediaSorter broadcast
   feature ships here too - the phone broadcasts its microphone and camera, the watch its microphone, and
   another device (StreamsPlayer for Windows, FastMediaSorter, this app) plays it. This product is a
   LIVE-BROADCAST **producer** from its first release, not only a listener.
7. **No reinvented wheel, no drift.** Playback and broadcast behaviour are ported from FastMediaSorter in
   full, not rewritten, and stay in step with it afterwards (section 6a).

Non-goals:

- Sorting, browsing, cloud and SMB/SFTP resources of FastMediaSorter - StreamsPlayer is not a sorter.
- Stream features that exist in neither FastMediaSorter nor the Windows version - this is an extraction,
  not new development.
- Changing STREAM-BANK / USER-PLAYLIST / LIVE-BROADCAST from this repository - the product conforms to
  them; a needed change is a catalog amendment first.
- Rewriting a stream or broadcast behaviour that FastMediaSorter already ships.
- Automatic migration of FastMediaSorter user data.

## 3. Owner decisions already taken

- Variants: two - a store variant and a noLegal sideload variant, after the FastMediaSorter watch-module
  precedent. The 8-flavor FastMediaSorter matrix is not inherited. Flavor isolation stays: the interface
  in `src/main`, the implementation in the variant source set, one DI module per variant.
- API level: minSdk is a tactical decision with 26 (Android 8+) as the reference; targetSdk is whatever
  Play requires at the first release.
- Wear: own watch app with its own Data Layer pairing to the StreamsPlayer phone app. The phone and watch
  apps share one application id (Data Layer requires the same id and signing key on both sides).
- Storage: own Room schema built from the stream tables, starting at version 1 with no history. No
  automatic migration; an optional export/import from FastMediaSorter is a later wish, not a requirement.
- Localization: EN/RU/UK from the first release; other locales optional. Every user-visible string obeys a
  communication policy (FastMediaSorter's `docs/COMMUNICATION_POLICY.md` is the reference to adapt).
- Accessibility: TalkBack labels, touch targets, state not shown by colour alone; input parity for touch,
  gamepad and TV remote (INPUT-PARITY adopter).
- Performance: the current stream player's budget carries over (adaptive buffer, live edge, resilience
  policies) - no regression against the behaviour being ported.
- Live broadcast from the phone (microphone, camera) and from the watch (microphone) is in the first
  release scope, ported from FastMediaSorter with its consent and permission flows.
- Second wave, not the first iteration: receiving live broadcasts from other devices (LIVE-BROADCAST
  listener, as the Windows version does), Cast of live streams.
- Owner wishes, done only if they port cheaply: home-screen "channel tile" widget and the radio tile;
  m3u playlist import (the code exists and moves with the core).

## 4. Open owner decisions

Ask each one once, in question-with-options form, before the phase that needs it - never before.

1. **Frozen anchors.** Proposed: `com.sza.streamsplayer` for phone + watch (shared id),
   `com.sza.streamsplayer.watchface` for the watch face. Frozen forever after the first shipped package;
   confirm before the first build that leaves this machine.
2. **Signing.** A new key, kept outside the repository. Precedent: `P:\ANDROID\FastMediaSorter_credentials`;
   proposed `P:\ANDROID\StreamsPlayer_credentials`. Play App Signing or self-managed - owner's call.
3. **Distribution of the first release.** Play (phone + wear) plus a sideload variant at once, or
   GitHub/sideload first as the Windows version started. Decides the signing flow, the version family
   registration and whether an INSTALL-TRUST page is needed.
4. **Watch face composition.** A rebranded clone of the FastMediaSorter WFF watch face, a clone plus one
   stream complication (for example "now playing channel"), or a new design.
5. **GitHub remote.** Repository name and visibility (`SerZhyAle/StreamsPlayer` is taken by the Windows
   product). Nothing is pushed until the owner names it.
6. **License.** The Windows product is MIT.
7. **Site.** Whether the product gets a page and joins the family map (SITE-FAMILY-MAP) - an owner
   decision, not a consequence of adopting the canon.

The fate of the streams screen inside FastMediaSorter (keep both, or a staged hand-over) is decided in
the FastMediaSorter repository, not here. Nothing in this product depends on it.

## 5. Extraction source map

Source repository: `P:\ANDROID\FastMediaSorter_mob_v2` - **read-only from this repository's sessions**.
Copy, then clean: port a layer, rename the package `com.sza.fastmediasorter` -> `com.sza.streamsplayer`
(after anchor 1 is confirmed), cut every reference to sorter surfaces, port its unit tests with it.

Where to read first in the source:

- `docs/ARCHITECTURE.md`, section "Internet Streams Subsystem" - the boundaries, the data flow, the
  play-outcome side channel, the flavor scope.
- `docs/ALL_FEATURES.jsonl` - the shipped capability inventory. Records whose area is `Streams` (about 100)
  and the stream-related part of `Wear OS` (about 40) are the feature list to carry over. Filter with
  `status == "active"` and the `flavors` field.
- `docs/content/recipes/streams-*.md` - the user-facing description of catalog, playback, widget and
  watch behaviour.
- `docs/FLAVOR_MATRIX.md`, row `SUPPORT_STREAMS` - where the subsystem is gated today.
- `docs/CROSS_PROJECT_CONTRACTS.md` - the source repo's contract roles.
- Class catalog: `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*Stream*"` from the
  source repo root (regenerate first with `scripts/catalog_sync.ps1 -Module app_v2` / `-Module wear` if
  the index is missing; it is a gitignored local file).

Phone module, by package (file counts are by name only, a lower bound - about 170 files / 39k LOC):

- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/` - import, merge, classification,
  resilience use cases.
- `.../ui/streams/` and `.../ui/streams/helpers/` - the streams screen, `StreamsViewModel`,
  `StreamInlineAudioManager` (inline radio, ICY metadata).
- `.../data/local/db/` - stream entities and DAOs inside the app-wide `AppDatabase`: `StreamSourceEntity`,
  `StreamQualityMemoryEntity`, `StreamUserStateEntity`, `StreamCollectionEntity`,
  `StreamCollectionMemberEntity`, `StreamingCacheEntry`.
- `.../data/repository/` and `.../data/repository/streams/` - `StreamSourceRepository` and friends.
- `.../domain/model/` - stream models, media kinds.
- `.../core/playback/resilience/` - live edge, retries, quality probing.
- `.../ui/player/helpers/` - the stream path of the shared fullscreen player: `StreamPlaybackHelper`,
  `StreamDataSourceFactoryProvider`, `BandwidthAdaptiveLoadControl`, routing in `VideoPlayerManager`.
- `.../widget/Stream*` - the channel tile widget (`StreamLaunchWidgetProvider`, its config activity and
  store, `StreamPlayLaunchActivity`); the rest of that folder is sorter widgets and stays behind.
- `.../domain/radio/`, `.../broadcast/RtspEndpointAddress.kt` - check each for stream relevance.
- `app_v2/src/main/res/layout*/` - stream layouts, including their `layout-land` counterparts.

Watch module (about 40 files / 9.7k LOC):

- `wear/src/main/java/com/sza/fastmediasorter/wear/` - `domain/usecase`, `domain/model`,
  `data/repository` (incl. `WearFaviconAtlasStore`), `ui/streams`, `ui/player/helpers`, `tile/`,
  `domain/playback/` (background playback, stall, display hold policies), Data Layer paths.

Live broadcast (path match `*broadcast*`, a lower bound - about 45 files / 3.9k LOC phone, 10 files /
0.9k LOC watch):

- Phone: `app_v2/src/main/java/com/sza/fastmediasorter/broadcast/` (endpoint, RTSP address, camera
  session consent), `data/broadcast/`, `domain/usecase/broadcast/`, `ui/broadcast/`,
  `widget/BroadcastEntryWidgetProvider.kt`.
- Watch: `wear/src/main/java/com/sza/fastmediasorter/wear/data/broadcast/`, `domain/broadcast/`,
  `ui/broadcast/`.
- Gating in the source: `SUPPORT_BROADCAST_SOURCE` and `SUPPORT_MIC_RECORDING` rows of
  `docs/FLAVOR_MATRIX.md`; features: `docs/ALL_FEATURES.jsonl` records mentioning broadcast (areas
  `Broadcast`, `Streams`, `Wear OS` - about 30).
- Seams: the camera and microphone capture stacks the broadcast uses live outside these packages
  (camera, audio recording); trace the imports, port only what broadcast needs.

Watch face: `watchface/` in the source repo - a resource-only Watch Face Format module
(`com.sza.fastmediasorter.watchface`), EN/RU/UK strings.

The seams a name search cannot see - plan each as its own step:

- The fullscreen video/RTSP path is the shared player of the whole source app.
- The stream tables live inside one app-wide Room database.
- DI is one application graph; stream bindings sit in shared Hilt modules.
- Flavor gating (`SUPPORT_STREAMS`) and the device-profile gate decide the entry point today.

Tech stack reference: the source repo's `gradle/libs.versions.toml`, `docs/TECH_STACK.md` and its pins -
compileSdk 37, targetSdk 36, minSdk 26, Kotlin 2.2.10, Room 2.7.0, Media3 1.11.0, Glide 5.0.9, Gradle
9.4.1, Hilt 2.59, KSP 2.3.8. Re-read the file for the current values instead of copying this line.
Android SDK on this machine: `C:\Users\serzh\AppData\Local\Android\Sdk`.

## 6. The Windows sibling

`P:\WINDOWS\Streams_Player` (GitHub `SerZhyAle/StreamsPlayer`) - **read-only reference** for structure,
look and behaviour: main window catalog (filters, search, own channels, pins, collections, radio/video
split), its `docs/PLAYBACK_RESILIENCE.md`, `docs/stream-playback-recommendations.md`, and its owned
contracts `APP-BEHAVIOUR` / `APP-STYLE` (desktop UX - read for intent; they are declared for desktop
products, not binding on Android unless the owner says so). Its `PLAN/` and `memory/` show the same agent
workflow in a second repository.

## 6a. Staying in step with FastMediaSorter

The owner's requirement: this product gets the whole stream and broadcast code, and its playback never
quietly diverges from FastMediaSorter's. ADR-1 rules out shared code, so the guarantee comes from three
mechanisms, not from a shared module:

1. **Full port, never a rewrite.** Every stream, playback and broadcast class listed in section 5 is
   copied into this repository's source tree and cleaned, with its unit tests. Reimplementing a ported
   behaviour from scratch is refused in review.
2. **Port ledger and drift check.** Every ported file is recorded in a committed ledger: source path in
   FastMediaSorter, the source file's SHA-256 at port time, the destination path, the port date. A script
   in this repository re-hashes the source files and lists every one that changed in FastMediaSorter since
   its port - the to-do list for carrying a fix across. It runs at the start of every release of this
   product; a change left uncarried is a decision written next to its ledger row, never silence.
3. **Behaviour lives in the contracts.** Playback routing, buffering, live-edge recovery and the
   classification rules are written in the STREAM-BANK document set (`stream-catalog/05_ui_streams_screen.md`,
   `06_player_routing.md`, `07_entrypoints_and_gating.md`, `03_catalog_format.md`), broadcast in
   LIVE-BROADCAST. Both products conform to the same text. A behaviour either product wants to change is
   amended in the catalog first, then implemented in both - that is what keeps them from drifting.

Contracts are not copied into this repository: one pointer file per contract under `docs/contracts/`
(the `contract-sync` skill writes them). A local copy is exactly the drift the catalog exists to prevent.

## 7. Contracts

The catalog is `P:\Contracts`; name it in exactly one place in this repository's rules, cite contracts by
id everywhere else. Versions as read from `P:\Contracts\_meta\REGISTRY.md` on 2026-10-02 - re-read the
registry, do not trust these numbers later.

- `STREAM-BANK` 2.2 (`stream-catalog/`) - consumer. The published catalog ZIP is this product's channel
  source, and its documents 03, 05, 06 and 07 are the behaviour reference for the catalog screen and the
  player. Join `stream-catalog/CONSUMERS.md`. Owner: FastMediaSorter Android.
- `LIVE-BROADCAST` 0.13 (`live-broadcast/`) - **producer** from the first release (phone microphone and
  camera, watch microphone; the verification handshake of section 6 of the contract is the acceptance
  test), listener in the second wave. Owner: FastMediaSorter Android.
- `USER-PLAYLIST` 0.12 (`user-playlist/`) - consumer (m3u import). Owner: StreamsPlayer Windows.
- `MEDIA-CLASSIFICATION` 0.10 - consumer of the media category enum.
- `DIAGNOSTIC-REPORT` 0.12 - producer, if the product exports a diagnostic log bundle.
- `PACKAGE-VERSIONING` 0.2 (`package-versioning/`) - Android profile. Three new package families must be
  added to the band table **before the first package ships** (a MINOR amendment, written in the catalog
  first): phone and watch share one application id and therefore need disjoint bands, the watch face has
  its own id.
- `INPUT-PARITY` 0.3 - adopter.
- `INSTALL-TRUST` 1.1 - only if a sideload page ships.
- `REPO-STAMP` / `HARNESS-PROFILE` / `REPO-LAYOUT` / `RULE-DELIVERY` (`rule-adoption/`) - this repository
  is an adopter; the canon adoption run covers them.
- `PAGE-CONTENT` / `PAGE-STYLE` / `SITE-FAMILY-MAP` - only if the owner decides on a site (section 4.7).

## 8. Suggested phase order

Each phase is its own ticket in this repository's store, with its own verification level.

1. Canon adoption (`/sza:adopt-canon`, new-repo mode), contract pointers (`/sza:contract-sync`), ticket
   store and release queue.
2. Gradle skeleton: three modules (phone, wear, watch face), two variants, version stamping per
   PACKAGE-VERSIONING, empty apps that build and install. Frozen anchors confirmed first (section 4.1).
   The port ledger and its drift-check script (section 6a) exist before the first ported file.
3. Stream core: models, Room schema v1, catalog import/merge from STREAM-BANK, classification, user state,
   resilience - ported with their unit tests, no UI.
4. Phone shell: catalog screen (grid/list, search, filters, pins, collections), inline radio with ICY
   metadata and background service; UI placement settled before implementation.
5. Fullscreen player for video/HLS/DASH/RTSP - only the parts streams need.
6. Live broadcast from the phone: microphone and camera, consent and permission flows, the endpoint -
   proven by the LIVE-BROADCAST handshake against StreamsPlayer for Windows.
7. Wear app and the Data Layer pairing with the phone app, including watch microphone broadcast.
8. Watch face (after section 4.4 is decided).
9. Optional: widgets, m3u import if not already carried in phase 3.
10. Release pipeline and a first release dry-run (build, pre-release sweep, drift check, no upload).

## 9. Acceptance - the product is done when

1. The phone app is installed under its own application id: the catalog opens, a radio channel plays from
   the list, an HLS video channel opens in the fullscreen player - with FastMediaSorter not installed.
2. The watch app is installed: the channel list is visible, a channel pinned within the same test
   scenario starts playback on the watch, and with the StreamsPlayer phone app paired the channel opens
   on the phone.
3. The StreamsPlayer watch face installs and shows the time with the product branding.
4. All three packages carry versionName/versionCode from their registered PACKAGE-VERSIONING families,
   are signed with the product's own key, and install and update independently of FastMediaSorter.
5. Building and releasing StreamsPlayer needs no FastMediaSorter build, and the other way round.
6. A phone broadcast (microphone, then camera) and a watch microphone broadcast started in this product
   play on StreamsPlayer for Windows, and the LIVE-BROADCAST handshake passes for each stream kind.
7. The port ledger covers every ported file, and the drift check run at the release candidate reports
   zero uncarried FastMediaSorter changes without a written decision.

## 10. Do not

- Edit `P:\ANDROID\FastMediaSorter_mob_v2` or `P:\WINDOWS\Streams_Player` from this repository's sessions.
- Edit a contract anywhere but the catalog, and never silently - follow `P:\Contracts\_meta\RULES.md`.
- Copy a contract document into this repository - a pointer under `docs/contracts/` only.
- Reimplement a stream, playback or broadcast behaviour that can be ported.
- Hand-copy the FastMediaSorter harness scripts (`scripts/spec_catalog`, locks, leases, chat). The
  harness ships with the `sza` plugin and is adopted, not copied.
- Copy the FastMediaSorter flavor matrix, its launcher, sorter, cloud or network-resource code.
- Commit signing material or secrets.
- Tag, upload or publish anything unless the owner asked for that exact release.
