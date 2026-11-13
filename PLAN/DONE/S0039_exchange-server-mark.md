# S0039 - Exchange server mark: show the shared mark (`EXCHANGE-MARK` 0.1) in the product, its UI and its documentation

**Status:** Implemented
**Status note:** Implemented 2026-11-08 (queue pass pq1, static): exchange mark vendored (Find-ExchangeMark PASS) and placed on phone (exchange screen, server errors, certificate dialogs, publish line, portal chooser, Own status) and watch (Own status, diagnosis alert); pointer, check-sync binding, registry patch for the canon session; not built, not tested until the release - build, light/dark/RTL walk and broadcast help recapture are RELEASE items
**Complexity:** Complex - about 20 existing files and 5 new ones across phone, watch, documents, the contract check and a registry patch, plus a state flag in the exchange form model; confirmed at the 2026-11-08 plan pass (section 7), which found the 2026-10-29 reading still right.
**Phase:** the exchange server's identity across the phone, the watch and the documents - independent of the build
phases of handoff section 8; the slices that touch the exchange screen follow S0027's layout, the help rows follow
S0035, the watch rows S0022, the Own-streams row S0038 (section 3.2)
**Verification level:** `Find-ExchangeMark.ps1` (rung 2) `exchange-mark: PASS` with exit 0 for the files each slice
vendors; a capture of every marked surface in the light and the dark theme, plus one right-to-left capture, with its
verdict row in `PLAN/S0039_exchange-server-mark/walk.tsv` (rung 3); every page of rules 10 and 11 opened and read (rung 4); the product's own registry row; the
ticket store validator.

## 0. Source

- Owner, 2026-10-19, through the canon session, for every product that names the exchange server (paraphrased): the
  exchange server («сервер обмена») needs a new sign and a new icon, used in every application and handed to all of
  them without exception, and recognisable on its portal and in its documentation. The base is a five-pointed star -
  not the Soviet one: slightly tilted, rays of different colours - and behind it, in the large versions, the FMS icon.
  The catalog designed it and generated the artwork.
- Contract `EXCHANGE-MARK` 0.1 (catalog domain `exchange-mark/`, status draft, owner shared; this product is named in
  its consumers: "StreamsPlayer_mob (phone and watch)"). Read through the pointer path in `CLAUDE.md`; cited here by id
  and section, never linked. Sections 2 and 3 were read in full for this ticket: the mark and its forms (section 2),
  one picture (rules 1-3), two forms and sizes (rules 4-7), where it stands (rules 8-13), the words and the source
  (rules 14-16), conformance rungs 1-4 (section 5), out of scope (section 6).
- The artwork is in the catalog's `exchange-mark/artwork/` (SVG masters, PNG at every size, ICO, two Android vector
  drawables, `MANIFEST.json` with a SHA-256 per file); `exchange-mark/gallery.html` shows every file on light, dark and
  blue. This product **copies files byte for byte, draws nothing, re-exports nothing** (rules 1, 5, 6).
- Two forms and one glyph: the **mark** (star alone, any size from 16 px, flat drawing up to 32 px) for a marker in a
  row, card, button, status or beside a heading; the **full form** (star in front of the FMS arrows on a plate, from
  128 px up) for the identity of the thing - a program icon of the server, a listing tile, the head of a page; the
  **banner** (1200 x 630, no words) for a portal or document head; the **one-tone glyph** where a platform or theme
  allows one colour only (notification small icon, tinted toolbar, a row in a one-tone list of source kinds).
- Words: `DEVICE-EXCHANGE` item CW owns the term - "Exchange server", «Сервер обмена», «Сервер обміну». The strings
  `exchange_server_title`, `menu_exchange_server` and `media_exchange_server` already hold it in `values`,
  `values-ru` and `values-uk`.
- Canon: the `feature-to-site` skill (the ship-together surfaces manifest: every surface and every authored locale in
  one edit); the `contract-sync` skill (pointer file, registry row); `ICON-RENDER` rules 2, 5, 7, 8, 9 as
  `EXCHANGE-MARK` cites them.
- Facts in this tree, read 2026-10-19: section 2.2. Strings searched: "exchange server", «сервер обмена»,
  «сервер обміну», `FMSEXCH1`, `DEVICE-EXCHANGE`, `exchange-server`, `ExchangeServer`; drawables searched for
  server, exchange, dns, storage, cloud. P1 re-derives all of it.

## 1. Goal

Wherever StreamsPlayer for Android names or shows the exchange server - the screen where the phone signs in to it,
the account it keeps, a failure or status about the server, the documentation page that links to its portal topic -
the person sees the one shared mark beside the name, unaltered, in the light and the dark theme and in every
authored locale. Surfaces that cannot draw a picture keep the name alone. The watch gets the mark on the day it gets
an exchange surface. Every file of the mark in the tree matches the catalog's manifest by hash, and the product's
own registry row says which surfaces were walked.

## 2. Scope

### 2.1 Steps

- **P0 Align.** Run the `contract-sync` skill for this one contract: the pointer `docs/contracts/EXCHANGE-MARK.md`
  (id, version 0.1, role: consumer, no payload), its row in `docs/contracts/README.md`, and the product's own row in
  the catalog registry (`Implements` `partial 0.1`, `Verified` pending until a surface is walked). Teach
  `tools/contracts/check-sync.ps1` the new domain in the same edit: it throws "Unexpected or duplicate own row" on a
  registry row it has no binding for, so a binding (`exchange-mark` -> `EXCHANGE-MARK`) and the id in its required
  list are part of this step, not a later chore.
- **P1 Inventory and decisions.** Re-derive section 2.2 from the source of truth - the manifest, `res/layout*`,
  `res/menu`, every `AlertDialog` and `MaterialAlertDialogBuilder` call site, the `wear` routes, the documents - never
  from the counts here. For each row fix the placement in the layout, the size (24 dp is the product's smallest, see
  section 5 item 1) and whether the mark is decorative or image-only. Decide the documents' asset home (section
  2.4) and record it in `docs/README.md`. Re-read `activity_exchange.xml` against S0027's current state first.
- **P2 Vendor.** Copy the files of section 2.4 from the catalog under their catalog names; run
  `Find-ExchangeMark.ps1` for them.
- **P3 Place.** One slice per group of rows, in the order of section 3.3 item 5, each landing in every surface and
  every authored locale in one edit; each row captured in both themes.
- **P4 Documents and listings.** Run `feature-to-site` for the rows of group (g): the portal directory page, the
  changelog entry, and, as they come into being, help pages (S0035), the site page (S0010 decision 4.7) and the
  store listing (S0014).
- **P5 Verify.** The acceptance of section 4, row by row.
- **P6 Close.** The registry row verified and dated (`partial 0.1` while a dependent row is open, `0.1` after); the
  product's own record updated; findings about the contract go to the catalog as a proposal through the canon
  session (section 5), never as an edit.

### 2.2 Inventory of the surfaces that name or show the exchange server (read 2026-10-19)

Platform facts that decide the forms: the phone is the View system (XML layouts, ViewBinding, Material
`MaterialToolbar`, platform `android.app.AlertDialog` in `ExchangeReceiverRuntime` and AppCompat `AlertDialog` in
`ExchangeActivity`); the watch is Compose for Wear OS (`androidx.wear.compose.material`, `SwipeDismissableNavHost`,
vector drawables through `painterResource`, chip icons at 20 dp), minimum SDK 26 on the phone and 28 on the watch;
`supportsRtl="true"` in both manifests and `ar` and `ur` are declared interface languages; only EN, RU and UK have
string resources, the other ten declared tags fall back to English. The product holds one exchange account per phone
(string `exchange_change_server_confirm`: "This phone publishes through one exchange server").

Form codes: **mark** = `android/ic_exchange_mark.xml` (coloured, faceted vector) or the PNG named; **glyph** =
`android/ic_exchange_mono.xml` (one tone); **full** = full form; **none** = no picture, reason given. Sizes are the
displayed size.

| # | Surface (rule) | File or screen in this repository | Form of the mark | Size | Locale files touched |
| --- | --- | --- | --- | --- | --- |
| R01 | (a) Exchange screen, sign-in state: where an account is added or signed in to | `phone/src/main/res/layout/activity_exchange.xml` (`layoutSignIn`, heading `tvSignInTitle` "Sign in to exchange"); `ui/exchange/ExchangeActivity.kt` | mark, before the heading | 24 dp | none new; the heading names the server, so the mark is decorative |
| R02 | (a)+(b) Exchange screen, account state: the one account entry | `activity_exchange.xml` (`cardAccount`: `tvAccountEndpoint`, `tvAccountLogin`, `tvAccountStatus`) | mark, before the endpoint line; never inside the status pill | 24 dp | none new; decorative |
| R03 | (b) A list of account entries | not present: one account per phone; no list of accounts exists. Entries of an account list arrive only if S0038 or S0028 build one | none today; each entry of such a list carries the mark when built | - | - |
| R04 | (a) Exchange screen toolbar | `activity_exchange.xml` `MaterialToolbar` (`exchange_server_title`, back arrow) | none: the body marker of R01 or R02 is the one mark of the state | - | - |
| R05 | (a) Change-password dialog (account management) | `layout/dialog_exchange_password.xml`, `ui/exchange/ExchangePasswordDialog.kt` | none: title and fields do not name the server | - | - |
| R06 | (a) Confirmations: change server, sign out, replace PIN | `ExchangeActivity.kt` `AlertDialog.Builder` (`exchange_change_server_confirm`, `exchange_remove_confirm`, `exchange_action_replace_pin`) | none: the server is named in running text only (rule 8) | - | - |
| R07 | (a) Import entries: file, link, QR, edit-channel refusal | `ui/exchange/ExchangeImportActivity.kt` (no layout, `Toast`), `layout/activity_qr_scanner.xml`, `layout/dialog_import_broadcast.xml`, `ui/streams/StreamEditDialog.kt` (`EditText` error on an `FMSEXCH` string) | none: a toast and an error line are running text; the scanner and the import dialog do not name the server | - | - |
| R08 | (c) Error banner of the exchange screen: refusals, unreachable server, certificate | `activity_exchange.xml` (`cardError`, `tvError`); reasons from `data/exchange/session/DeviceExchangeReasons.kt` (`exchange_refused_*`), `cert_changed_error` | mark, before the banner text, only for an error about the server; field-validation errors (`exchange_error_missing_*`, port range) carry none | 24 dp | none new; decorative |
| R09 | (c) Certificate dialogs of the exchange screen | `ExchangeActivity.kt` `showUntrustedCertDialog`, `showCertChangedDialog` (`exchange_untrusted_cert_dialog_title`, `exchange_replace_pin_dialog_title`) | mark, in the dialog title row; no theme tint | 24 dp | none new; decorative |
| R10 | (c) Certificate and playback-failure dialogs of the receiver | `ui/exchange/ExchangeReceiverRuntime.kt` `confirmCertificate`, `showPlaybackFailure` (`cert_pin_title`, `cert_changed_title`); `ui/player/FullscreenPlayerActivity.kt` (the same two titles, about lines 264 and 281). The cast-offer dialog (`cast_offer_title`) is about a device, not the server: none | mark, in the dialog title row | 24 dp | none new; decorative |
| R11 | (c) Publish state line of the broadcast screen | `layout/activity_broadcast_control.xml` and `layout-land/activity_broadcast_control.xml` (`tvPublishState`); `ui/broadcast/helpers/BroadcastControlManager.kt` (about line 740, `broadcast_publish_state_*`) | mark, before the line in the "publishing" and "published" states; none in "not published" | 24 dp | none new; both layout files in one edit |
| R12 | (c) Status pills, switches and directory rows of the exchange screen | `tvAccountStatus` and `tvDevicePresence` (`drawable/bg_status_badge.xml`, saturated ground), `switchPublish`, `switchPlayCastImmediately`, `layout/item_exchange_group.xml`, `layout/item_exchange_broadcast.xml` | none: a saturated pill is not a ground for the mark (rule 7); directory rows are devices and broadcasts, not the server | - | - |
| R13 | A settings row and a notice that name the server (not a listed place) | `layout/fragment_settings_broadcast.xml` and `layout-land/` (`rowPublishToExchange`, `guestPublishNotice`); `ui/broadcast/helpers/BroadcastSettingsPanelManager.kt` (about line 124, notice dialog) | none by rule 8; owner input 3.3 item 1 asks whether the design wants one | - | - |
| R14 | (d) A picker of source kinds or channels | the catalog's chip row, `layout/activity_main.xml` (`mediaChipsGroup`: All, Radio, Video, Mine, Recent; text-only chips), has no exchange entry; the filter sheet (`layout/sheet_stream_filter.xml`) and add-channel (`layout/dialog_edit_stream.xml`) list no source kind. S0038 (Draft) puts the group "Live from my devices" inside Mine, with a state and a setup action that name the exchange server | none today; S0038's rows that name the server (its no-entry state, the setup action) take the mark - glyph if the neighbours are one-tone `source.*` rows (as `source.recent`), mark if they are coloured; mapped in `docs/PHONE_SHELL_ICON_MAP.md` | 24 dp | S0038's strings |
| R15 | (d) Overflow entries "Exchange server" and "Media exchange and broadcasts" | `res/menu/main_menu.xml` (`menu_exchange_server`, `menu_media_exchange_portal`); `MainActivity.kt` (about lines 241-249) | none: the overflow of a Material toolbar draws no item icons and it is not a rule 8 place; owner input 3.3 item 1 | - | - |
| R16 | (d)/(g) Portal topic chooser: the topic "Exchange server" is a link to a page about the server | `ui/common/support/MediaExchangePortalDialog.kt` (`setItems`, text only, 7 topics, topic `media_exchange_server` -> `exchange-server[-ru\|-uk].html`) | mark, before that one topic (rule 10: a link to such a page may carry it); needs a `setAdapter` list because `setItems` draws no icon | 24 dp | none new (`media_exchange_server` exists in all three) |
| R17 | (e) About or credits on the phone | not present: no About, Help or credits entry in `main_menu.xml`; the only bundled document is `phone/src/main/assets/THIRD-PARTY-NOTICES.txt` (licences, names no server) | none; an About built later that lists what the product connects to takes the mark (24 dp) | - | - |
| R18 | (e) About on the watch | not present in code: ported `about_*` strings sit in `wear/src/main/res/values*/strings.xml`, but no composable shows them (only `ui/testing/WearTestTags.kt` names them); routes are home, streams, audio and video player, broadcast, broadcast QR, tile picker | none today; same rule when an About exists | - | - |
| R19 | (f) The server program itself: executable, installer, uninstall entry, service display, shortcuts, Share Manager entry | not this product: it is a receiver and broadcaster client and declares no server or administration role (`docs/contracts/DEVICE-EXCHANGE.md`); FMS_Win ships the server | not applicable | - | - |
| R20 | One-tone: notification small icons | `phone/src/main/java/com/sza/streamsplayer/core/notification/NotificationIcons.kt` and `wear/src/main/java/com/sza/streamsplayer/wear/core/notification/NotificationIcons.kt` (`STATUS_BAR` = `ic_notification_app_logo`, one value for every notification); posters `BroadcastNotificationFactory`, `OpenOnPhoneNotifier`, `CameraSessionConsentNotifier`, `WearPlaybackService`, `VoiceRecordingService`, `WearOpenOnWatchNotifier` | none: no notification is about the exchange server today; read section 5 item 2 before one is added | - | - |
| R21 | Watch: any surface naming the server | none today: `wear/src/main/res` and `wear/src/main/java/com/sza/streamsplayer/wear/ui` hold no exchange string or screen. S0022 adds the pairing hand-over, the watch's publish status and its failures | capability: the watch draws pictures (Compose `Image` and `painterResource`), so the mark fits at 24 dp (`ICON-RENDER` rule 9: 24 to 32 dp on the watch); only one of the 5 watch backgrounds (`WearBackgroundMode.NONE`) is plain black - the others are branded, an image or the phone's wallpaper - so the mark stands on a neutral plate (rule 7); use `Image`, not `Icon` (it tints) | 24 dp | `wear` `values`, `values-ru`, `values-uk` with S0022's strings |
| R22 | Watch tiles, complications, watch face | `wear/src/main/java/com/sza/streamsplayer/wear/tile/`, `watchface` module (resource-only Watch Face Format) | none: none of them names the server; a watch face is not a surface of this contract | - | - |
| R23 | (g) README | `README.md` (English only; two paragraphs on the exchange account and a portal section) | none: the page's subject is the product, it merely mentions the server (rule 10) | - | no translations exist |
| R24 | (g) Portal directory page | `docs/MEDIA_EXCHANGE_PORTAL.md`, the "Exchange server" row (EN, RU, UK links) | mark, before the row's name, an HTML `img` with `alt` "Exchange server"; the page head carries no banner (owner input 3.3 item 2) | 24 px | English only |
| R25 | (g) Other documents that mention the server | `docs/PRIVACY_POLICY.md` (running text, lines 10, 19, 30-33), `docs/contracts/*.md`, `docs/README.md`, `CHANGELOG.md` | none: they merely mention it; the changelog gets a text entry (section 2.4) | - | - |
| R26 | (g) The shared portal's own pages, index and exchange-server page | published from FastMediaSorter Android, not in this repository; this product writes its own sections there (S0030 item 5) | the portal's head, index entry and exchange-server page wear the mark under the same contract, in FastMediaSorter Android's repository; here only the words of this product's sections | - | - |
| R27 | (g) Help pages (`CONTEXT-HELP`) | none exist; `phone/src/main/assets/` holds only `THIRD-PARTY-NOTICES.txt`. S0035 (Draft) builds a page for the exchange screen and its dialogs | when built: the exchange screen's page opens with the full form, once, with the CW term as `alt` (rule 10); each help page of a surface that shows the mark has the mark inline beside its title and its screenshot is taken with the mark in place (rule 11) | full 128 px or more; title marker 24-32 | S0035's locale set (EN, RU, UK authored) |
| R28 | (g) User guide chapter | not present: the repository has no user guide | none | - | - |
| R29 | (g) Product site | not present: `.sza-canon.json` `site.kind` is `none`; the hub page of S0010 decision 4.7 is not built | when it exists and features the exchange server, the mark in that section, never as the product's icon (rule 12) | - | - |
| R30 | (g) Store listing | not present in this repository: channels are `github` only; Play and the listing copy await S0010 and S0014 | same rule 12; store graphic sizes are out of scope | - | - |

### 2.3 The existing generic picture of the exchange server

None. The product draws no picture of the exchange server today: no server-bays glyph (Material `dns` or similar) and
no private drawable among the phone's `res/drawable` files or the three of the watch (a search of drawable resources
for server, exchange, dns, storage and cloud finds nothing), and `docs/PHONE_SHELL_ICON_MAP.md` has no exchange row.
S0030's exchange glyphs (`ICON-SET`, FastMediaSorter Android S4221) are not folded in yet. There is nothing to replace:
the mark is the first picture. When S0030 maps the `source.exchange-server` row, it takes the contract's one-tone
glyph and not the two server bays (`EXCHANGE-MARK` section 6, the owner's order of 2026-10-19), and the mapping goes
into `docs/PHONE_SHELL_ICON_MAP.md`.

### 2.4 The work, one line per inventory row

Vendored files, copied byte for byte under their catalog names (a Gradle resource name is the catalog name, so the
check finds them by hash), each slice vendoring only what its rows use:

- `phone/src/main/res/drawable/ic_exchange_mark.xml` from `android/ic_exchange_mark.xml` (R01, R02, R08-R11, R16);
  `phone/src/main/res/drawable/ic_exchange_mono.xml` from `android/ic_exchange_mono.xml` only when a one-tone row is
  built (R14). No `drawable-night` variant: the mark does not change by theme.
- `wear/src/main/res/drawable/` the same two, a separate byte-identical copy, only when S0022's rows exist (R21).
- The documents' asset home: the repository has no convention for pictures under `docs/`; P1 proposes
  `docs/assets/exchange-mark/` and records it in `docs/README.md`. `png/mark-24.png` shown at 24 px (R24).
- Help pages (R27), under S0035's asset home: `svg/exchange-mark-full.svg` shown at 128 px or more, or
  `png/full-256.png` shown at 256 px; `png/mark-24.png` or `png/mark-32.png` beside titles. A PNG is never scaled
  (rule 5); a size the set lacks comes from the SVG.

The work per row (every placement lands in every authored locale and every surface of its group in one edit; the
product's launcher icons - phone, watch, watch face - are not touched and not badged):

- **R01** place the vector before the heading `tvSignInTitle`, 24 dp, clear space of one eighth of its width (3 dp),
  decorative (`importantForAccessibility="no"`: the name stands beside it).
- **R02** place it before `tvAccountEndpoint`, outside the status pill, same size and clear space, decorative.
- **R03** no work now; the row is a note for the day an account list exists.
- **R04** no work; recorded so the toolbar is not given a second mark.
- **R05** no work; the dialog does not name the server.
- **R06** no work; running text carries no picture.
- **R07** no work; toast and error line carry no picture.
- **R08** place the vector at the start of `cardError` for server errors only; P1 fixes which `ExchangeUiState.Error`
  and refusal reasons count as "about the server" and records the list; validation errors stay bare.
- **R09** the two dialogs get the vector in the title row (`setIcon` or a title view), untinted.
- **R10** the same for `confirmCertificate`, `showPlaybackFailure` and the two dialogs in `FullscreenPlayerActivity`;
  the cast-offer dialog stays bare.
- **R11** put the vector before `tvPublishState` in both layout files; show it only in the two states that talk to the
  server; the word "server" in the line is S0030's to settle (the line says "your server", item CW says "exchange
  server").
- **R12** no work; pills and directory rows stay as they are.
- **R13** none unless owner input 3.3 item 1 says yes; if it does, `SettingsToggleRow` is a ported file with a
  port-ledger row, so the change is recorded in `docs/port-ledger.jsonl` (`CLAUDE.md` section 2).
- **R14** no work now; S0038's definition of done carries the row (glyph or mark by its neighbours) and the
  icon-map line.
- **R15** no work unless owner input 3.3 item 1 says yes.
- **R16** replace `setItems` with a `setAdapter` list; the topic "Exchange server" gets the vector at 24 dp, the other
  six stay as they are; the name beside it is `media_exchange_server`.
- **R17** no work; recorded.
- **R18** no work; recorded.
- **R19** no work; recorded as not applicable.
- **R20** no work; read section 5 item 2 before the first exchange notification.
- **R21** no work now. S0022's definition of done gains: every watch row that names the server or reports a failure
  about it carries the vector at 24 dp on a neutral plate, drawn with `Image`.
- **R22** no work; recorded.
- **R23** no work; recorded.
- **R24** vendor `png/mark-24.png`; put an HTML `img` (`width="24" height="24" alt="Exchange server"`, the picture
  at its own pixel size) before the name in the "Exchange server" row; the page stays English.
- **R25** no work for the pictures; add the `CHANGELOG.md` entry under `[Unreleased]` / `Added` (the product's
  ledger): the exchange server now carries one shared mark on the exchange screen, its errors and dialogs and the
  broadcast screen; and add the pointer file, its README row and the check-sync binding of P0.
- **R26** no work here; P1 sends FastMediaSorter Android's session the list of portal pages that are pages about the
  exchange server, through the canon session.
- **R27** no work now. S0035's exchange slice gains: the page head carries the full form once, with `alt`, in each
  authored locale; title marker 24-32 px; screenshots taken with the mark in place; assets vendored byte for byte.
- **R28** no work; recorded.
- **R29** no work now; the site ticket (S0010 decision 4.7 and S0033's hosted page) gains the rule 12 line.
- **R30** no work now; the listing ticket (S0014) gains the rule 12 line.

## 3. Constraints and dependencies

### 3.1 Constraints

- The mark is never altered: no recolour, tint (no `android:tint`, `app:tint` or `ImageViewCompat` tint on it; a
  Compose `Icon` is not used for it), outline, shadow, glow, crop, stretch, further rotation, mirror or animation, and
  no shape of its own (rule 2). In a right-to-left layout it moves to the start side of its row and is **not**
  mirrored (`ar` and `ur` are declared; no `autoMirrored`). It carries no words.
- Clear space is one eighth of the mark's width on every side (3 dp at 24 dp). The mark stands on a neutral light or
  dark surface; on a saturated or photographic one - the status pills, the watch backgrounds - it stands on its own
  neutral plate or not at all (rule 7).
- Running text carries no picture; a toast, a log, a notification line and an error line show the name alone (rule 8).
- A mark with its name beside it is decorative; an image-only mark takes the CW term in the interface language as its
  accessible name (`contentDescription`, `alt`, `aria-label`; `ICON-RENDER` rule 8), and `exchange_server_title`
  already holds it in all three authored locales. The ten fall-back tags show the English term, as for every string.
- A raster is copied, never resized or re-exported; a vector is the only thing drawn at a size the set lacks. A web
  page shows a PNG at its own pixel size only.
- No drive-letter path and no link to the catalog in any file of this repository outside `CLAUDE.md`; the check
  commands are written with `<catalog>` standing for the locator in `CLAUDE.md`.
- Out of scope: the product's launcher icons (never replaced, never badged), the words (item CW), store graphic sizes,
  motion, and the two things the exchange server is not: Drive (`via Google Drive`) and direct access (`the sharing
  service`) never wear the mark - S0029's Drive screen gets none.

### 3.2 Related tickets

- **S0027** (exchange sign-in-only account, In Progress): it is reshaping `activity_exchange.xml` right now; the slice
  of R01, R02, R04, R08, R09 starts from its landed layout, and S0027's strings stay its own.
- **S0035** (in-app help, Draft): R27 and rule 11; its exchange slice consumes this ticket's artwork; the exchange
  screen's help screenshots are taken after R01, R02, R08 and R09 land. S0035 section 3.3 item 3 (the page viewer)
  decides how a bundled page shows an SVG.
- **S0036** (unified UI contracts, Draft): migrates the same layouts; the final help captures and this ticket's
  captures wait for its layouts, and the mark follows `UI-SIZING` and `ICON-RENDER` as S0036 applies them.
- **S0030** (exchange terms, names, icons, portal links, Draft): the CW terms in the three locales, the
  `source.exchange-server` glyph row and `docs/PHONE_SHELL_ICON_MAP.md`; the portal's name (section 5 item 5).
- **S0038** (Own streams, Draft) and **S0028** (account broadcast list): R03 and R14; the first list and the
  rows that name the server are born with the mark.
- **S0022** (the watch as an account device, Draft): R21; **S0031** (connection diagnosis) adds failure surfaces that
  take R08's rule; **S0029** (Drive announcements) never takes the mark.
- **S0014** (free promotion, Draft) and **S0010** decision 4.7 with **S0033** (site and hosted privacy page): R29 and
  R30.
- **S0013**, **S0026**, **S0037** (contract-sync runs): none takes `EXCHANGE-MARK`; P0 here is its own run with its own
  registry row, in the shape S0026 normalises.
- **S0004** (phone shell, icon map): the map gains the exchange rows; **S0025** (deep dynamic audit): its emulator rig
  is a possible driver for the captures, no dependency.
- Sibling repositories, cited with their names: StreamsPlayer (Windows), FastMediaSorter Android (publishes the
  portal), FMS_Win (the server), FMS_Web, CyrFlip - each carries its own ticket of the same work; nothing in any of
  them is edited from here.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0004, S0010, S0014, S0022, S0027, S0028, S0029, S0030, S0031, S0033, S0035, S0036, S0038.
- **1. The settings row and the overflow entries (R13, R15):** ANSWERED 2026-11-08, option (a): all three stay
  text-only - resolved by research, not asked; see "Quiz decisions (2026-11-08)" below. Original question: rule 8 lists no settings row and no menu, and
  allows the mark "at no other unless its design asks". Options: (a) leave all three text-only; (b) mark the settings
  row (a change to a ported widget); (c) mark the overflow entries (needs optional menu icons). Recommendation: (a).
- **2. The portal directory page (R24):** ANSWERED 2026-11-08, option (a): a 24 px marker on the "Exchange server"
  row only, no banner - resolved by research, not asked; see "Quiz decisions (2026-11-08)" below. Original options: (a) a 24 px marker on the "Exchange server" row only; (b) also
  the banner at the head, treating `docs/MEDIA_EXCHANGE_PORTAL.md` as a portal page. Recommendation: (a): the portal's
  head belongs to FastMediaSorter Android (R26) and this page's subject is how this product relates to it.
- **3. The topic chooser (R16):** ANSWERED 2026-11-08, option (a): an adapter list with the mark on the one topic.
  Original options: (a) an adapter list with the mark on the one topic; (b) leave the
  dialog text-only. Recommendation: (a), as the portal's own index lists the server with the mark and rule 10 allows
  the marker on a link.
- **4. Which errors are "about the server" (R08):** the project's call, made in P1 and recorded; the owner reviews the
  list once. Not asked now.
- **5. Order of the slices:** ANSWERED 2026-11-08: everything here, help separate (see "Quiz decisions
  (2026-11-08)" below; it replaces the proposal that follows where they differ). Original proposal: (1) P0 and the vendored vector, then the exchange screen and its dialogs
  (R01, R02, R08, R09, R10) after S0027 lands, then the broadcast screen (R11) and the topic chooser (R16); (2) the
  documents (R24, R25) at any time; (3) help rows with S0035's exchange slice; (4) watch rows with S0022; (5) the Own
  list with S0038; (6) site and listing rows when those exist. Recommendation: as proposed.
- **6. Queue placement and release:** the owner orders the queue (`CLAUDE.md` section 4). The harness added this
  ticket to the end of package 1 in `PLAN/RELEASE_QUEUE.md` when the record was inserted - the store's own projection
  of a new ticket into the current package, not a scheduling decision; the owner moves it, or leaves it, and says
  which release first ships the mark. ANSWERED 2026-11-08: the ticket stays in package 1 and release 1 is the release
  that first ships the mark (see "Quiz decisions (2026-11-08)" below).

### Quiz decisions (2026-11-08)

- Settings row and overflow entries (item 1; R13, R15) -> all three stay text-only, as option (a). Resolved without
  asking, three facts settle it: the `rowPublishToExchange` row (both orientations) already carries `ic_server_relay`,
  mapped in `docs/PHONE_SHELL_ICON_MAP.md` to `feature.server-relay`, which `EXCHANGE-MARK` section 6 keeps with the
  server bays, so the mark neither replaces nor doubles it; `guestPublishNotice` is a notice line (rule 8: running text
  carries no picture); the two overflow entries sit in a `showAsAction="never"` overflow list that draws no item
  pictures by design (rule 8, 0.4). Rule 8 allows the mark elsewhere only "unless its design asks", and no design asks.
  R13 therefore needs no change to the ported `SettingsToggleRow` and no port-ledger row.
- Portal directory page (item 2; R24) -> option (a): a 24 px `img` before "Exchange server" in the row of
  `docs/MEDIA_EXCHANGE_PORTAL.md`, no banner at the page head. Resolved without asking: the page's subject is the
  shared portal, not the server, so it "merely mentions" the server and rule 10 gives it no head image, while rule 10
  lets a link to the server's page carry the mark at 16 to 24 px; the portal's own head is FastMediaSorter Android's
  (R26).
- Portal topic chooser (item 3; R16) -> "Знак у одной темы" (option (a)): replace `setItems` in
  `MediaExchangePortalDialog.kt` with a `setAdapter` list; the topic `media_exchange_server` carries the 24 dp vector,
  the other six topics are aligned with an empty indent of the same width (reason: the owner's original order that the
  server is recognisable on its portal, and rule 10, under which the portal's index lists the server with the mark;
  text-only stays permitted by rule 8, 0.4, but is not chosen). The new row follows `UNIFIED-UI` sizing (S0036 is
  Verified); the mark is captured in the light and the dark theme and once right-to-left.
- Order of the slices (item 5) -> "Всё здесь, справка отдельно": S0039 carries every phone, document, watch and Own
  streams row itself, in this order - P0 and P2, then the phone slice (R01, R02, R08-R11, R16), then the documents
  (R24, R25), then the watch rows (R21), then the Own streams rows (R14). Reason: S0027, S0022, S0030 and S0038 are
  `BlockNeedUserTest` (built, waiting for the owner's device runs) and S0036 is `Verified`, so the earlier plan of
  adding a line to their definitions of done would reopen tickets that wait for the owner; P1 re-derives section 2.2
  against the tree as it is now (R14, R17, R21 and the S0027 layout were written when those tickets were Draft or In
  Progress) before any slice starts. The help rows (R27) stay with S0035's exchange slice, the site and listing rows
  (R29, R30) with S0010 and S0014; each of those tickets gains the rule line as section 2.4 already says. The registry
  row therefore reads `partial` at this ticket's close until R27 lands.
- Queue placement and first release (item 6) -> stays in package 1; release 1 is the release that first ships the
  mark (reason: the first public build already carries it, so no visible change reaches installed users later). The
  rung 3 walk of every phone row (light, dark, one right-to-left capture) precedes that release, as section 4 says.
  The queue position inside package 1 stays as the harness projected it; the owner moves it if wanted.
- Item 4 (which errors are "about the server", R08) -> not asked, as the ticket says: the project's call in P1, which
  the owner reviews once.

## 4. Definition of done

Per slice (each proof is the repository's check or a recorded command output):

- Every row of its group is placed or recorded as none with the reason of section 2.2; no surface outside the
  inventory shows the mark; no look-alike drawable exists in the tree.
- `pwsh -File <catalog>\exchange-mark\reference\Find-ExchangeMark.ps1 -RepoRoot <this repository> -Require <the
  manifest paths this slice vendors>` ends `exchange-mark: PASS` with exit 0. The first slice requires
  `android/ic_exchange_mark.xml`; the documents slice adds `png/mark-24.png`; the one-tone, watch and help slices add
  `android/ic_exchange_mono.xml`, `svg/exchange-mark-full.svg` or `png/full-256.png`, and `png/mark-32.png` as they
  vendor them. The script does not skip `build/` (section 5 item 7): run it after a clean, and read any extra copy
  it reports.
- A capture of each surface of the slice in the light and the dark theme, plus one in a right-to-left layout, one row
  per surface and theme in `PLAN/S0039_exchange-server-mark/walk.tsv` (surface, theme, direction, verdict), with the
  summary in the closing note; the mark is untinted and clear of
  saturated grounds in every capture; TalkBack reads each image-only mark as the CW term and skips each decorative one.
- Every page of rules 10 and 11 that exists opens with the image and has the `alt` (R24 now; R27 with S0035); the
  changelog entry is in `CHANGELOG.md`.
- `docs/contracts/EXCHANGE-MARK.md` and its row in `docs/contracts/README.md`; the product's own registry row for
  `EXCHANGE-MARK` (`Implements` `partial 0.1` while R14, R21 or R27 is open, `0.1` after; `Verified` a date; a note
  naming the walked surfaces and the rungs reached); `pwsh -File tools/contracts/check-sync.ps1 -CatalogRoot
  <catalog>` ends exit 0 with the new binding.
- `pwsh -NoProfile -File tools/harness.ps1 spec_catalog/validate` reports no new FAIL.

Before the release that first ships the mark: the walk of rung 3 complete for every phone row; the closing note
recorded in this ticket (P6).

## 5. Conflicts found while reading

The contract is a draft; each item is a finding for the catalog, sent through the canon session as a proposal, and none
is a reason to skip a row.

1. **No flat drawing as an Android vector (R01, R02, R08-R11, R16).** Rule 5 wants the flat drawing up to and including
   32 px; the set holds the flat drawing only as PNG (`mark-16/24/32`) and SVG, and the Android vectors are the
   faceted drawing. At 24 dp the physical size is 24 px on mdpi and 32 px on tvdpi, so rule 5 asks for the flat form
   there. On phones of minimum SDK 26 the density is hdpi or more (36 px and up), so nothing visible breaks; the
   product uses 24 dp as its smallest size and records the gap. Ask: a flat Android vector in the set, or a line that a
   vector at the system's size satisfies rule 5.
2. **The one-tone glyph file against a notification small icon (R20).** `android/ic_exchange_mono.xml` carries
   `android:tint="?attr/colorControlNormal"`. This product's `ic_notification_app_logo.xml` is a fork of its logo
   precisely because a theme-attribute tint cannot resolve outside the app theme and `startForeground` then throws (the
   file's own comment cites S0405 and S0416), and `NotificationIcons.kt` fixes one branded icon for every notification.
   Rules 1 and 6 forbid an altered copy, so the vendored file cannot be a notification small icon as it is. Nothing
   breaks today because no notification is about the exchange server. Ask: a notification-safe variant in the set
   (no `?attr` tint), or a line in rule 3 that allows a tint-free copy checked by its path data.
3. **Surfaces that cannot draw a picture.** Toasts (`ExchangeImportActivity`, the exchange screen), the `EditText` error
   of `StreamEditDialog`, the cast-result strings and every notification line keep the name alone, as rule 8 says;
   `setItems` dialogs and `SettingsToggleRow` have no icon slot (R16 fixes the first, R13 leaves the second). No
   conflict, recorded.
4. **A coloured or photographic ground behind the watch's screens (R21).** Only `WearBackgroundMode.NONE` is plain
   black; the other four values are branded, an image or the phone's wallpaper, so a watch row carries a neutral plate
   under the mark; rule 7 allows it, and the plate is a layout surface of the product, not a change to the mark. No
   conflict, recorded.
5. **The portal's name.** The contract names it "Media exchange between your devices"; this product's README heading,
   `docs/MEDIA_EXCHANGE_PORTAL.md` title and menu string say "Media exchange and broadcasting portal" and "Media
   exchange and broadcasts". Words are S0030's and FastMediaSorter Android's; ask the catalog which is the published
   name before R24 and R26 are worded.
6. **Locale gap under R11 and R13.** Twelve S0021 strings - `broadcast_publish_*`, `broadcast_cast_result_*`,
   `settings_broadcast_publish_*` - exist in `values` only; `values-ru` and `values-uk` lack them, so the publish
   state line and the publish settings row read in English under RU and UK. Adding the mark needs no string, so this
   ticket does not close the gap; it belongs to S0030's sweep. The "every authored locale in one edit" rule shows it.
7. **`Find-ExchangeMark.ps1` walks `build/`.** Its skip list holds `.git`, `node_modules`, `.gradle`, `.scratchpad`,
   `.cxx` and `temp`; a Gradle build leaves byte-identical copies of a vendored vector under `phone/build/` and
   `wear/build/`, which it reports as extra copies, and a stale one after a version rise would read as a near copy.
   Ask: add `build` to the skip list.
8. **This repository's own check refuses an unknown own row.** `tools/contracts/check-sync.ps1` throws on a registry
   row it has no binding for; P0 teaches it the domain in the same edit as the registry row. A repository finding, not
   the catalog's.

Seen on the way and not in this ticket: hard-coded English in the exchange screen (`ExchangeActivity.kt` toasts "No
camera available", "Could not open file", "PIN replaced", "Cannot play this broadcast format", the "PIN:" label,
`ExchangeDirectoryAdapter.kt` "Mode: ") - words for S0030 and the localization sweep.

## 6. Not decided here (the project decides, and records why)

- The exact placement of each marker in its layout (a compound drawable, a leading `ImageView`, a title view), within
  the sizes and clear space above.
- The list of errors that count as "about the server" (R08).
- The documents' asset folder (section 2.4).
- Anything outside `EXCHANGE-MARK` section 3.3: the words, the launcher icon, store graphics, motion, and any surface
  the contract does not list unless owner input 1 asks.

**Addendum 2026-10-19, later the same day (the portfolio owner, through the canon session).** This ticket was written against `EXCHANGE-MARK` 0.1; the contract is now 0.3, and its log rows 0.2 and 0.3 answer several of the conflicts recorded above - read both rows before the work and let the answer stand over the conflict note. The PNG set now holds the 100 to 200 per cent steps of 16, 24 and 32 (rule 5). Android has a flat 24 dp vector and an untinted one-tone drawable (section 2). A high-contrast theme shows the one-tone glyph (rule 3). Vendored SVG and XML are marked binary in `.gitattributes`, and the glyph may be carried inline (rule 6). A surface with no room or no picture capability keeps the name alone, with its reason listed (rule 8). A chapter of a longer page takes the inline mark, and a page that has a head image does not also take rule 11's (rules 10 and 11). `Find-ExchangeMark.ps1` skips build outputs and judges a near copy by `exchange`-named files only. **Names (rule 14):** the owner's answer is that the full name is "Media Exchange Server", a proper name kept in English in every language. It stands beside the mark where the thing is named as itself - the server program's own surfaces, a page title and the head image's `alt`, a listing, an About credit, the family map; item CW's term stays what a sentence and a marker call it, and the `alt` of a head image is the full name. **The hub** now shows the mark in its family map (its own ticket). The registry row `Implements` is written against 0.3.

**Spec pass 2026-10-29.** `EXCHANGE-MARK` is now 0.4; section 3.3 rule 8 keeps an entry text-only in a list whose entries have no pictures by design. The current portal chooser uses a text-only `setItems` list, so that form is permitted; the owner's item 3 remains open because it asks whether to redesign that list. Rule 10 also changes head-image alternative text when the full name is already visible beside the image, and rule 12 distinguishes the server page from the portal index. Version 0.4 changes no artwork; its manifest remains 0.3. The implementation plan and inventory must be reconciled against 0.4 and the current phone, watch, help and site before implementation. Owner inputs 1, 2, 3, 5 and 6 remain unanswered; the queue runner's selection authorizes this pass but does not decide the release in item 6. The documentation registry is not configured; S0051 carries it. This spec pass changes the ticket only; product documentation and UI remain unchanged.

## 7. Plan (2026-11-08, queue pass pq1)

Read before planning: `EXCHANGE-MARK` 0.4 in full (sections 2-6 and log rows 0.2-0.4), `artwork/MANIFEST.json` (0.3,
45 files), `Find-ExchangeMark.ps1`, the material 1.14.0 dialog layouts and styles, and FastMediaSorter Android's own
placements (read only): it draws `ic_exchange_mark_small` at 24 dp in rows and failure panels and puts it into a
dialog with `setIcon`. Shared-contract boundary: this ticket consumes a picture and writes no format, wire shape or
algorithm; the boundary is the product's own registry row and pointer, both in phase A.

### 7.1 Inventory re-derived against the tree of 2026-11-08 (P1)

Rows of section 2.2 that still read as written: R01, R02, R04-R07, R08, R09, R12, R13, R15, R16, R19, R20, R22, R23,
R25, R26, R28, R30. Rows that changed since 2026-10-19:

- **Artwork (0.2 answer to section 5 item 1).** The set now holds a flat Android vector for 24 dp,
  `android/ic_exchange_mark_small.xml`. Every marker here is 24 dp and every dialog icon slot is 24 to 32 dp, so that
  file is the one vendored; the faceted `ic_exchange_mark.xml` (48 dp) and the one-tone `ic_exchange_mono.xml` have
  no row that uses them and are not vendored (rule 6: no dead copy). No `drawable-night` copy.
- **R03** still none: one account per phone. **R10**: the cast-offer dialog shares `ask()` with the certificate
  prompt; only the certificate prompt takes the mark.
- **R11**: the RU and UK strings now exist (section 5 item 6 is closed). The line is centred in a full-width view, so
  a start drawable would stand at the far edge; the view becomes `wrap_content` and centred in both layout files.
- **R14 (phone)**: S0038 built the Own status banner (`activity_main.xml` `tvOwnStatus`, `MainActivity.renderOwnStatus`).
  Its states "no exchange server" (`own_status_no_server`), "sign in again" (`own_status_sign_in`) and the failures
  (`own_status_refused`, `own_status_unreachable`) are connection statuses about the server: rule 8 (c), coloured
  mark, because the banner's neighbours are text buttons and not one-tone `source.*` rows. Loading, updated and stale
  lines carry none. The answers to Refresh (`own_refresh_*`) are messages: running text, none.
- **R14 (watch)**: S0038 built the watch's Own section (`WearOwnStreamsItems.OwnStatusLine`): the same three server
  states (`wear_own_status_no_server`, `wear_own_status_sign_in`, `wear_own_status_unreachable`) take the mark above
  the caption, as the loading state shows its progress ring there. Notices (`wear_own_notice_*`) are messages: none.
- **R17**: an About now exists (`view_settings_about.xml`, S0036). It lists no service the program connects to
  (description, version, licence, links, related products, libraries), so rule 8 (e) does not apply: none.
- **R21**: S0022 built the watch's exchange surfaces. The relay switch and the relay status line
  (`exchange_watch_*`) are `feature.server-relay`, which keeps the server bays and never wears the mark (section 6).
  The pairing hint (`exchange_watch_pair_through_phone`) is an instruction in running text: none. The connection
  diagnosis alert of the broadcast screen states a failure about the server (rule 8 (c)): the mark in the alert's
  icon slot, on a neutral plate (rule 7), drawn with `Image`.
- **R27**: the exchange screen's help page now exists (`help-map.json` row `ExchangeActivity` -> `exchange`, no
  picture set yet). Per the owner's answer to item 5 the help rows stay with S0035; S0035 gains the line (step E4).
- **Rule 3, high contrast.** Android views have no forced-colours mode that recolours app content (the system's
  high-contrast text setting changes text only), so the coloured mark stays; recorded, nothing to build.

**Item 4 - which errors of the exchange screen's banner are "about the server" (R08), the project's call.** About the
server, with the mark: a sign-in refused by the server (`DeviceExchangeEnrollResult.Refused`, every
`exchange_refused_*` reason), the server unreachable at sign-in (`Unreachable`), a sign-in reason the server left on
the retained entry (`showSignIn` with a reason: signed out, device revoked), the account directory refused by the
server and the directory disconnected (`exchange_directory_disconnected`). Not about the server, bare: a refused
connection string (`UnsupportedMajor`, `NotExchange`, `Refused` of the string itself), a file that cannot be opened
or read, and the field checks (host, port, login, secret). `ExchangeUiState.Error` is never constructed; it stays
bare. The owner reviews this list once (item 4).

**Asset home of the documents:** `docs/assets/exchange-mark/`, recorded in `docs/README.md`.

### 7.2 Phases and steps

Coverage: goal and DoD lines map as follows - pointer, README row, check binding, registry row (A); vendoring and
`Find-ExchangeMark` (B); phone rows R01, R02, R08-R11, R14, R16 (C); watch rows R14, R21 (D); documents R24, R25 and
the hand-over lines R27, R29, R30 (E); captures, TalkBack, build and unit suites (RELEASE, section 7.3). Topology:
A and B produce files only; C and D consume the drawable of B; E consumes nothing of C or D except the list of
placed rows. No step consumes what a later step produces.

**Phase A - align (P0).**
- A1. New `docs/contracts/EXCHANGE-MARK.md`: `EXCHANGE-MARK` 0.4 read (artwork 0.3), role consumer on phone and
  watch, no payload. Check: `check-sync.ps1` pointer block PASS with one more pointer version.
- A2. `docs/contracts/README.md` gains its row. Check: grep `EXCHANGE-MARK.md` in the README.
- A3. `tools/contracts/check-sync.ps1` gains the binding `exchange-mark` -> `EXCHANGE-MARK` and the id in its
  required list. Check: the script parses; its run names `Missing own row: EXCHANGE-MARK` and nothing else new
  (the row arrives with A4).
- A4. `PLAN/S0039_exchange-server-mark/registry-sync.patch` and `registry-sync-reason.txt` for the canon session: one
  new own row `EXCHANGE-MARK | StreamsPlayer Android | C | partial 0.4 | - | pending | ..`. Check: the patch applies
  to a scratch copy of the live registry, and `check-sync.ps1` run against a scratch catalog holding the patched
  registry reports no `EXCHANGE-MARK` finding.

**Phase B - vendor (P2).**
- B1. `phone/src/main/res/drawable/ic_exchange_mark_small.xml` and `wear/src/main/res/drawable/ic_exchange_mark_small.xml`,
  byte copies. B2. `docs/assets/exchange-mark/mark-24.png`, byte copy. B3. `.gitattributes` marks the vendored XML
  `binary` (rule 6). Check: `Find-ExchangeMark.ps1 -Require android/ic_exchange_mark_small.xml,png/mark-24` ends
  `exchange-mark: PASS`, exit 0; `git check-attr binary` reports `set` for both XML copies.

**Phase C - phone (P3).**
- C1. R01 and R02: `activity_exchange.xml` `tvSignInTitle` and `tvAccountEndpoint` take the mark as their start
  drawable, `drawablePadding` 8 dp (clear space 3 dp is met). Compound drawables are not read by TalkBack: decorative.
- C2. R08: `ExchangeFormState.errorAboutServer`; `ExchangeViewModel` sets it by the list of item 4;
  `ExchangeActivity.showError` takes `aboutServer` and sets or clears the start drawable of `tvError`
  (`drawablePadding` in the layout). Unit tests gain the flag's assertions (run at the release).
- C3. R09: the two certificate dialogs of `ExchangeActivity` take `setIcon`. R10: `ExchangeReceiverRuntime.ask` takes
  an icon, the certificate prompt and the certificate-changed failure pass the mark; `FullscreenPlayerActivity`'s two
  certificate dialogs take `setIcon`. The slots draw the icon untinted (AppCompat and platform: no tint; material 1.14
  M3: a `colorControlNormal` overlay, which the mark's fixed colours do not read).
- C4. R11: both `activity_broadcast_control.xml` files: `tvPublishState` `wrap_content`, centred, `drawablePadding`;
  `BroadcastControlManager` sets the mark for Publishing and Published and clears it otherwise.
- C5. R16: `MediaExchangePortalDialog` uses `setAdapter` with a new row layout `item_portal_topic.xml` (the material
  list row's text style and paddings, 48 dp minimum height, start and end paddings for right-to-left); the
  `media_exchange_server` topic takes the mark, the other six an empty start drawable of the same size.
- C6. R14 (phone): `MainActivity.renderOwnStatus` sets the mark on `tvOwnStatus` for the three server states and
  clears it otherwise; the padding is set with the drawable, so `activity_main.xml` does not change.
- Checks: grep of every placement; no `tint` on any mark (`grep -n "ic_exchange_mark_small"` then read each site);
  `Find-ExchangeMark` PASS.

**Phase D - watch (R14, R21).**
- D1. New `wear/.../ui/common/ExchangeMark.kt`: `ExchangeMarkPlate`, the 24 dp mark drawn with `Image` on a neutral
  rounded plate of the theme's surface colour, 3 dp clear space, decorative (`contentDescription = null`).
- D2. `WearOwnStreamsItems.OwnStatusLine` shows the plate above the caption in NO_SERVER, SIGN_IN and FAILED.
- D3. `StandardWearAlertDialog` gains an optional `icon` slot passed to both `Alert` overloads (default null, no
  other caller changes); the diagnosis alert of `WearBroadcastScreen` passes the plate.
- Checks: grep; no `Icon(` draws the mark.

**Phase E - documents (P4).**
- E1. `docs/MEDIA_EXCHANGE_PORTAL.md`: the "Exchange server" row begins with
  `<img src="assets/exchange-mark/mark-24.png" width="24" height="24" alt="">`. The `alt` is empty because the name
  stands beside the picture (constraint 4 of section 3.1, `ICON-RENDER` rule 8); this replaces the `alt` of R24.
- E2. `docs/README.md` names the asset home. E3. `CHANGELOG.md` `[Unreleased]` / `Added` entry.
  `docs/PHONE_SHELL_ICON_MAP.md` and `docs/contracts/ICONOGRAPHY.md` say where the coloured mark is placed.
- E4. The hand-over lines: S0035 (R27, rule 11 and the exchange page's head) and S0014 (R29, R30, rule 12).
- Checks: grep; `assert-help-map.ps1` (diagnostic run, section 7.3).

### 7.3 What is left to the release (TESTING_AND_QA section 2)

Nothing here is built or run on a device between releases. RELEASE items: the build and the unit suites
(`pwsh -NoProfile -File tools/release/pre-release-sweep.ps1`); the rung 3 walk of every placed row in the light and
the dark theme and one right-to-left capture, a verdict row each in `PLAN/S0039_exchange-server-mark/walk.tsv`, with
TalkBack skipping each decorative mark; the
recapture of the `broadcast` and `broadcast-settings` help pictures, whose input `layout/activity_broadcast_control.xml`
changes in C4 (`capture-help-screenshots.ps1`; until then the help gate reports them `STALE-SHOT`, and the baseline
is not raised - it only falls).

### 7.4 Done in this pass (2026-11-08)

Phases A to E as planned. One deviation from section 2.4: R24's picture takes an empty `alt`, because the name stands
beside it (constraint 4 of section 3.1). One defect found at the phase C boundary and fixed before phase D: the
portal chooser's rows were inflated in the activity's theme, where `materialAlertDialogBodyTextStyle` is not set; they
now use the dialog builder's themed context, as `setItems` does. The documentation registry is not configured (S0051);
the records touched are the pointer and its README row, `docs/README.md`, `docs/MEDIA_EXCHANGE_PORTAL.md`,
`docs/PHONE_SHELL_ICON_MAP.md`, `docs/contracts/ICONOGRAPHY.md` and `CHANGELOG.md`; README.md and the help sources are
unchanged (R23; R27 is S0035's).

## Last Audit

**Date:** 2026-11-08 06:21 | **Mode:** static, queue pass pq1 (between releases: static checks, gates of 5 s or less,
direct calls) | **Outcome:** Implemented - not built, not tested until the release
**Counts:** PASS 20 | WARN 0 | FAIL 0 | MANUAL 2 | UNCHECKABLE 0 | EXEMPT 3 | RELEASE 4

Evidence, each run in this pass:

1. PASS - pointer: `check-sync.ps1 -CatalogRoot <catalog>` prints `PASS: 32 pointer versions match domain headers`
   (31 before). Exit 1 overall from known reds (below). expected: 32 | actual: 32.
2. PASS - README row: `docs/contracts/README.md` names `EXCHANGE-MARK.md`.
3. PASS - binding and required id: the same run adds exactly one finding, `Missing own row: EXCHANGE-MARK` (12 own-row
   findings, 11 before). expected: 1 new | actual: 1 new.
4. PASS - registry patch: `patch -p1 --binary` on a scratch copy of the live registry exit 0, `cmp` with the proposed
   file exit 0; applied after S0057's patch exit 0 (offset 7 lines); `check-sync.ps1` against a scratch catalog with
   the patched registry reads 19 own rows and reports no `EXCHANGE-MARK` finding.
5. PASS - rung 2: `Find-ExchangeMark.ps1 -Require android/ic_exchange_mark_small.xml,png/mark-24.png` ends
   `exchange-mark: PASS (2 of 45 manifest files vendored, 0 inline)`, exit 0, 4.33 s.
6. PASS - `git check-attr binary` reports `set` for both vendored XML files and the PNG.
7. PASS - R01, R02: `activity_exchange.xml:237` (`tvSignInTitle`), `:476` (`tvAccountEndpoint`), start drawable, 8 dp.
8. PASS - R08: `ExchangeViewModel.kt:92, 381, 397` set the flag (sign-in reason, refusal, unreachable), `:216, 422`
   clear it (connection string, field checks); `ExchangeActivity.kt:502, 560, 562` pass it, `:600` draws the mark.
9. PASS - R09: `ExchangeActivity.kt:611, 626` `setIcon`.
10. PASS - R10: `ExchangeReceiverRuntime.kt:82` (certificate prompt), `:103` (certificate changed); the cast offer
    keeps `icon = 0`; `FullscreenPlayerActivity.kt:319, 337`.
11. PASS - R11: both `activity_broadcast_control.xml` files `wrap_content` with `drawablePadding`;
    `BroadcastControlManager.kt:769` for Publishing and Published only.
12. PASS - R16: `MediaExchangePortalDialog.kt` `setAdapter` with `item_portal_topic.xml`; mark on the
    `EXCHANGE_SERVER` topic, transparent indent of the same size on the other six.
13. PASS - R14 (phone): `MainActivity.kt:345`, the three server states only.
14. PASS - R14 (watch), R21: `ExchangeMarkPlate` (`Image`, plate `WearAppColors.exchangeMarkPlate`) at
    `WearOwnStreamsItems.kt:129` (NO_SERVER, SIGN_IN, FAILED) and `WearBroadcastScreen.kt:201` (diagnosis alert,
    through the new `icon` slot of `StandardWearAlertDialog`); no `Icon(` draws the mark.
15. PASS - no tint: none of the six marked views carries a tint attribute; no tint call in the touched code.
16. PASS - XML: the four edited layouts and both vendored vectors parse.
17. PASS - documents: the portal row's `img` (24 x 24, empty `alt`), the asset home in `docs/README.md`, the
    `CHANGELOG.md` entry, `docs/PHONE_SHELL_ICON_MAP.md` and `docs/contracts/ICONOGRAPHY.md`.
18. PASS - hand-over lines in S0035 ("Remaining development" item 3) and S0014 (section 4).
19. PASS - `spec_catalog/validate`: 9 OK, 1 FAIL - the known `S0031` journal path; no new FAIL. 1.72 s.
20. PASS - hygiene: no long dash, no `...` in the new prose, no ticket-id probe, `release-syntax: PASS`.

EXEMPT: the rows recorded as none with their reasons (R03-R07, R12, R13, R15, R17-R20, R22, R23, R26, R28); help rows
(R27, rules 10 and 11) by the owner's answer to item 5, handed to S0035; site and listing (R29, R30, rule 12) handed
to S0014.

Known reds that are not this ticket's: `check-sync.ps1` 13 findings (S0057's pending cells, S0053 item CZ);
`assert-help-map.ps1` exit 2 `COULD NOT VERIFY` (no committed baseline at HEAD); `spec_catalog/validate` S0031.

### Action items
1. None in the code. The help gate run with the working baseline as the committed copy reports
   `FAIL STALE-SHOT broadcast` and `broadcast-settings` - introduced by C4 on purpose and cleared by RELEASE item 3.

### Manual / on-target
- [ ] RELEASE: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` - the first compile of the phone and watch
  changes and the unit suites, with the new `errorAboutServer` assertions of `ExchangeViewModelTest` and
  `ExchangeSignInOnlyTest`.
- [ ] RELEASE: rung 3 on the phone - every placed row (sign-in heading, account card, a server error and a field error,
  both certificate dialogs of each of the three places, the publish line publishing / published / not published, the
  portal chooser, the Own status in no-server, sign-in and failure states) in the light and the dark theme plus one
  right-to-left capture, a verdict row each in `PLAN/S0039_exchange-server-mark/walk.tsv`; the mark untinted, not
  mirrored, at the start side; TalkBack skips it.
- [ ] RELEASE: `capture-help-screenshots.ps1 -Help broadcast` and `-Help broadcast-settings`, then
  `generate-help-pages.ps1`; `assert-help-map.ps1` then reports no `STALE-SHOT`.
- [ ] RELEASE: rung 3 on the watch - the Own status in the three server states on a plain and on an image background,
  and the diagnosis alert of the broadcast screen.
- [ ] MANUAL (canon session): apply `PLAN/S0039_exchange-server-mark/registry-sync.patch`; `check-sync.ps1` then shows no
  `EXCHANGE-MARK` finding.
- [ ] MANUAL (owner): review the item 4 list of section 7.1 once.
