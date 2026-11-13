# S0035 - In-app help: F1 and a `?` action on every screen, and a help page or layer for each

**Status:** BlockNeedUserTest
**Status note:** Built 2026-11-12 (unattended pass): slice 5 closed (Connection check dialog row, ? and F1, baseline empty) and the exchange mark is in the help (exchange head, broadcast and catalog inline). No compile command is declared, so the Kotlin and layout are not compiled and not tested until the release. Waits for the owner: wording review, device walks (Connection check ? and F1, watch Help row, TalkBack, gamepad, TV remote). Release runs: recapture of five stale picture sets (help gate red on STALE-SHOT until then), pre-release sweep.
**Complexity:** hard - assessed at the spec pass 2026-11-12: the remainder still crosses a ported generator, a dialog surface, vendored contract artwork, three locales and device walks (TalkBack, gamepad, watch pair) that only the owner can run; the live help gate fails on five stale picture sets that need an emulator recapture.
**Depends on:** S0027, S0031
**Phase:** user-facing help across the phone and the watch - independent of the build phases of handoff section 8;
slices that touch the exchange screens follow the exchange block (section 3.2)
**Verification level:** static gate predicates per slice (surfaces against the help map, pages against the locale
set, links resolved, reasons present); a scripted offline check of the page set; the screenshot freshness check; the
emulator walk matrix (every surface x locale x theme, F1 with a hardware keyboard attached and the `?` action) before
the release that first ships the feature.

## Remaining development

*As of 2026-11-12. The earlier runs and their evidence are in sections 6 and 7.*

Development of the phone slices is finished: the exchange slice (slice 5) and the exchange server's mark in the help
(from S0039) were built by the second run, section 7. What is left is below and in the lists after it.

1. **Publication on the product's site** waits for the site page of decision 4.7 (S0033 hosts only the privacy pages).
2. **The screenshots are stale, which is a run, not development.** The gate reports `STALE-SHOT` for `broadcast`,
   `broadcast-settings`, `catalog`, `filter-and-sort` and `qr-scanner` (layouts changed by S0036, S0039 and others after
   the capture of 2026-10-29), and no picture set exists yet for the `exchange` page; the pictures of every page whose
   surface shows the exchange mark are to be taken with the mark in place (`EXCHANGE-MARK` rule 11).

The screenshot freshness gate and the inherited-surface decision are present in the tree: the gate checks the
fingerprints recorded in `img/capture.json`; item 10 records the decision and the five inherited surfaces have help
actions. Their latest full device walk remains a release-time check.

Slice 6 (the watch, owner answer 2b) is built as of 2026-10-27, see the addendum in section 6. What is left of it is not development: the `watch` page has no picture (nothing here captures the watch), and the Help row has not been walked on a watch and phone pair.

Not development (runs and reviews): rerun the capture and the control diff now that S0036 is closed, as the closing note asks; make a release build (R8 has not seen the new classes, and the viewer ran only in the debug build) and run the unit suites, which include the new exchange-dialog resolver test; the owner's review of the wording of the 11 pages and the index in 3 languages and of the map; the walk of the Connection check dialog's `?` and F1 on a device (the capture's `-F1Proof` cannot reach it: it opens only after a failed connection) and the look of the exchange mark in the viewer (API 27 and API 35, light, dark, a right-to-left locale); the walk of the watch's Help row on a watch and phone pair (including the phone not in front, notifications off and the phone out of reach); the TalkBack, gamepad and TV-remote sweeps on a device, with the S0004 sweeps.

## 0. Source

- Owner, 2026-10-16, through the canon session, for every product with a user interface (paraphrased): F1 means Help.
  Many of the product's own windows and dialogs get a `?` button in the top-right corner, added beside whatever is
  already there and never in place of it. Pressing it either explains every significant element of that window right
  away, or opens an HTML page with a screenshot and a detailed description in the user's language - what each part is
  for and in what order to use it - cross-linked to the help of the other windows, to the site's documentation and
  portals, and to sibling products where that is appropriate. The pages are simple but attractive. **Each project
  decides what and where is more appropriate.** On Android the owner's note is: F1 only with a hardware keyboard, the
  `?` is an action in the top app bar before the overflow, the pages are bundled assets.
- Contract `CONTEXT-HELP` 0.1 (catalog domain `in-app-help/`, status draft, owner shared; this product is named in
  its consumers): the key (rule 1), the button (rule 2), the same answer for both (3), which surfaces (4), the two
  forms layer / page (5-7), the page anatomy (8-11), offline and private (12), language (13), generated screenshots
  (14), permanent address (15), voice (16), one resolver and the help map (17), ship together (18), the gate (19),
  conformance rungs (section 4). Reference artifacts of the contract: the help page template, `help.css`, the example
  help map.
- Canon: `UI_UX.md` section 7; `DOCUMENTATION_CONCEPT.md` sections 4-5 (the help pages are a ship-together surface);
  the `feature-to-site` skill (window help step); the `contract-sync` skill (pointer file, registry row).
- Used by the contract: `ICON-SET` `app.help` (the glyph; a proposal widens its meaning and names), `ICON-RENDER`
  rule 5 (hit target), `APP-BEHAVIOUR` rules 4, 8, 9, 11, `PAGE-STYLE` and its `sza-kit.css` (page look),
  `DOC-INTERNAL-QUALITY` / `DOC-EXTERNAL-QUALITY` (link integrity), `CHECK-VERDICT` and `CHECK-BASELINE` (the gate),
  `LOCALIZATION` (locale parity), `SITE-STRUCTURE` (the address promise). `WINDOWS-UI` section 2 is Windows only.
- The same work elsewhere, cited by repository and id: StreamsPlayer (Windows) SP-0230. FastMediaSorter Android is a
  consumer of the same contract. Nothing in either repository is edited from here.
- Facts in this tree, read 2026-10-16: section 2.2.

## 1. Goal

A person who opens any screen of StreamsPlayer on the phone, and does not know what its controls are or in what order
to use them, presses `?` in the top app bar - or F1 on a hardware keyboard - and gets, without leaving the app and
without a network, either a numbered explanation on the live screen or a page with a real screenshot and a plain
description in the app's language, which links on to the neighbouring screens' help, to the documentation and to the
Windows sibling where the same function is shared. The watch gets the form its constraints allow (section 3.3, item 2).
The set is provably complete: a gate counts the screens against a help map and fails on a screen without a row.

## 2. Scope

Work in slices; a slice is done when its surfaces are in the help map and the gate counts them. Start with the screens
a user meets first (the catalog, the player, the broadcast screen), then the rest. Slice order is section 3.3, item 8.

### 2.1 Steps

- **P0 Align.** Read `CONTEXT-HELP`. Run the `contract-sync` skill for this one contract: the pointer
  `docs/contracts/CONTEXT-HELP.md`, its row in `docs/contracts/README.md`, and this product's adoption row in the
  registry (its own row, the date of the run, honest `partial` while it is partial). Record, in the same run, the two
  adoptions the gate needs and the pointer README lists as "candidate consumers" today: `CHECK-VERDICT` and
  `CHECK-BASELINE` (section 3.3, item 4).
- **P1 Inventory and decisions.** Enumerate every surface (activity with a screen, bottom sheet, dialog, settings
  panel, viewer, Wear route) from the source of truth in section 2.2 - never from memory, never from the counts there.
  For each choose `layer`, `page`, `both`, `inherit` (reason) or `none` (reason) by what is more appropriate for this
  product; trivial one-question confirmations and system dialogs are `none`, a dialog opened from a surface with help
  may `inherit`. Write the decisions into the help map (rule 17; shape in the contract's example map), a file in the
  repository named in `CLAUDE.md` and shipped with the package so the resolver and the gate read the same file
  (a location under the module's `assets/` is the likely home; decided here).
- **P2 The ask.** One resolver (surface id, optional element -> target); no screen carries help code of its own. F1 is
  handled at the activity or dialog level through the key-event path the app already has (bare F1 is unused today,
  section 2.2) and honoured when a hardware keyboard is attached; it is never registered system-wide. The `?` is an
  action of the top app bar with the `app.help` glyph, placed before the overflow, with the vocabulary name as
  accessible name and tooltip ("Help (F1)" where a keyboard exists), added beside what is there - the existing actions
  keep their place; prove it with a before / after control diff per surface. Screens that have no top app bar (the
  fullscreen player, the broadcast screen, the QR scanner, bottom sheets, dialogs) get a per-surface placement decision
  in P1 that still follows the corner rule of `CONTEXT-HELP` rule 2 and is reachable by touch, TalkBack, gamepad and
  TV remote (the `INPUT-PARITY` adoption). Hit target per `ICON-RENDER` rule 5; the glyph is mapped in
  `docs/PHONE_SHELL_ICON_MAP.md`.
- **P3 The answer.** For `layer` surfaces: the read-only numbered layer of rule 6 (Esc, the key again, the button
  again, a click outside, and Back all dismiss it; Tab steps through the markers; TalkBack reads each label; it works
  in landscape and in a right-to-left locale). For `page` surfaces: pages built from the reference template (anatomy
  of rule 8, entries of rule 9), one per surface, in every shipped locale (rule 13), bundled in the package and
  opening with no network (rule 12), shown with scripting off, with an "open on the site" link that leaves for the
  system browser and Back returning to the screen; published from the same files on the product's site when it has
  one. Screenshots generated from the build per locale and theme with demo content, marker bounds from the UI's own
  element tree (rule 14). Cross-links (rule 11): section 2.3.
- **P4 The gate.** Surfaces against the map, pages against the locale set, links resolved, reasons present, a baseline
  that only falls (rule 19), the scripted no-request check of rule 12; screenshot freshness (rung 2); the walk matrix,
  every surface x locale x theme, F1 and the `?` action (rung 3), before the release that first ships the feature. The
  verdict line follows `CHECK-VERDICT`. Where the gate runs is decided in P1 (section 3.1).
- **P5 Docs-sync.** Run `feature-to-site`: the how-to and the README link to the help index; the user-visible entry goes
  in the ledger; the help set is a row of the surfaces manifest.
- **P6 Close.** The registry row verified and dated; the product's own record updated; findings about the contract go
  to the catalog as a proposal, never as an edit.

### 2.2 Facts in this tree, 2026-10-16 (recon: counts by Glob and Grep, short reads; P1 re-derives all of it)

**Platform and toolkit.** Android, Kotlin, three modules (`settings.gradle.kts`). `phone` is the View system: XML
layouts, ViewBinding, Material 1.14 (`MaterialToolbar`, `BottomSheetDialogFragment`, `MaterialAlertDialogBuilder`),
AppCompat, fragments. `wear` is Compose for Wear OS 1.4.1 in one activity with a `SwipeDismissableNavHost`.
`watchface` is a resource-only Watch Face Format package: no activity, no code, no keyboard - not a surface
(`CONTEXT-HELP` section 1 leaves a watch face out). Variants `store` and `noLegal` share `src/main`, so bundled assets
are common to both. `usesCleartextTraffic="true"` is set in the phone manifest and no `WebView` is used anywhere in the
tree yet.

**Where the surfaces are enumerated (the gate's source of truth).**
- Phone: `phone/src/main/AndroidManifest.xml` (10 activities); `phone/src/main/res/layout*/` (28 layout files,
  `layout-land` variants of the broadcast screen and its settings panel); the 4 `BottomSheetDialogFragment` classes and
  `MediaExchangePortalDialog`; `MaterialAlertDialogBuilder` / `AlertDialog` call sites (15 source files).
- Wear: the `WearRoutes` destinations in `wear/src/main/java/com/sza/streamsplayer/wear/MainActivity.kt`.

**Rough counts by kind (before P1; not the inventory).**
- Phone, screens: 6 activities with a screen of their own - the catalog (`MainActivity`, whose `MaterialToolbar`
  carries the 10-item menu), the exchange account and server screen (`ExchangeActivity`, has a toolbar), the
  fullscreen player, the broadcast control screen, the QR scanner, the stream-launch widget configuration. 4 further
  activities appear to be hand-offs that call `finish()` at once (`ExchangeImportActivity`, translucent;
  `BroadcastEntryActivity`; `BroadcastShareActivity`; `StreamPlayLaunchActivity`) - P1 confirms and maps them `none`
  with a reason.
- Phone, other: 1 settings panel (broadcast settings, `fragment_settings_broadcast`, portrait and landscape; there is no
  general settings screen), 4 bottom sheets (filter and sort, sleep timer, recently played, channel actions), about
  5 non-trivial dialogs (edit channel, import playlist, import broadcast, exchange password, media exchange topic
  chooser), and confirmations and notices that are mostly `none` or `inherit`. Order of magnitude: about 20 candidate
  surfaces.
- Wear: 7 routes in one activity (home, streams, audio player, video player, broadcast, broadcast QR, tile picker);
  tiles and complications are system-hosted.
- Surfaces coming from open drafts: the sign-in-only account screen (S0027, In Progress), the account's broadcast
  list (S0028), the Drive screen (S0029), the "won't connect" diagnosis steps (S0031), watch pairing (S0022).

**Help that already exists - reuse it, do not duplicate it.**
- `TooltipDialog` and the help icon of the three settings-row widgets (`HelpableRow` / `HelpRowDelegate`;
  `SettingsToggleRow`, `SettingsDropdownRow`, `SettingsInputRow`, attributes `str_`, `sdr_`, `sir_` `helpTitle` and
  `helpMessage`). All of it is ported from FastMediaSorter and has port-ledger rows; no layout sets the attributes
  today, and the only help-like string is `broadcast_share_help`. Candidate for the element level of a layer or
  for retirement - P1 decides, and a change to a ported file is recorded in the ledger (`CLAUDE.md` section 2).
- `SupportDestination.HELP` -> `SupportIntentFactory`: opens the FastMediaSorter documentation site
  (`documentation/`, with RU and UK index variants) plus how-to pages for Wear install and watch broadcast; still
  FastMediaSorter-branded and inherited; its comment already anticipates "surface-specific help (e.g. F1 keyboard
  shortcut docs)". The only caller outside the support package is the broadcast viewer guide in
  `BroadcastControlManager`.
- The catalog menu entry "Media exchange and broadcasts" (`MediaExchangePortalDialog`, `docs/MEDIA_EXCHANGE_PORTAL.md`):
  7 portal topics x EN / RU / UK, opened in the system browser, topic level only.
- The watch's About opens a web portal on the phone ("Open on phone", `WearOpenUrlOnPhoneRepositoryImpl`); the phone
  has no About or Help item in `main_menu.xml`.
- No F1 handler. Existing chords in `StreamCatalogInputManager` and `MainActivity.dispatchKeyEvent`: Menu, gamepad Y and
  Shift+F10 for the context menu; gamepad Start and Ctrl+F for find; Esc / Enter in dialogs (`DialogKeyboardDelegate`).
  Bare F1 is free.
- Bundled documents today: `assets/THIRD-PARTY-NOTICES.txt` only. `docs/contracts/INPUT-PARITY.md` is adopted (S0004:
  manual TalkBack, gamepad and TV-remote sweeps still pending).

**Tooling.**
- Screenshots: no screenshot library in `gradle/libs.versions.toml`; `phone/src/androidTest` holds data tests only. The
  working practice is an emulator pass: `adb` screenshot plus a `uiautomator dump` XML tree per screen, kept in the
  git-ignored `temp/S0004/` beside a one-off `a11y_sweep.py` - the element tree is exactly the marker-bounds source
  that rule 14 asks for, but no generator is committed. A demo-content mode (no author's channels, accounts, server
  addresses or keys) does not exist and is part of P3.
- Strings: `values` 344, `values-ru` 332, `values-uk` 332 entries in the phone; `values`, `values-ru`, `values-uk` in
  Wear. Gates and scripts present: `tools/contracts/check-sync.ps1`, `tools/release/pre-release-sweep.ps1`,
  `tools/ci/`, `tools/port-ledger/`, `build-logic` unit tests; the catalog gate runs with
  `tools/harness.ps1 spec_catalog/validate`.

**Locales.** The interface languages are read from `res/xml/locales_config.xml` by `UiLanguageCatalog`: 13 tags (en,
zh-Hans, hi, es, fr, ar, bn, pt, ru, ur, uk, de, it). Only EN, RU and UK have string resources; the other ten fall back
to English. `supportsRtl="true"` in both manifests, and `ar` and `ur` are declared, so right-to-left is a real case for
the layer and the pages. The Windows sibling declares the same 13 (EN / RU / UK by hand, ten machine-translated).
Open: section 3.3, item 1.

**Site and portal.** The product has no site: `.sza-canon.json` `site.kind` is `none`; S0010 decision 4.7 (answered
2026-10-10) is to join `SITE-FAMILY-MAP` with a dedicated page, which is not built, and S0033 puts the privacy policy
on it. Until that page exists the pages ship in the package only; publishing "from the same files" waits for it. The
shared media exchange portal is published from FastMediaSorter Android and linked by every product of the family.

### 2.3 Cross-links to choose (rule 11; P3 picks the real ones)

- The help of neighbouring screens: the catalog -> channel actions, filter, edit channel, import playlist; the broadcast
  screen -> its settings panel, the QR view, the share panel; the exchange screen -> import broadcast and the password
  dialog; the player -> sleep timer and recently played.
- Deeper documentation: the shared media exchange portal pages for the exchange and broadcast screens (S0030 item 4
  deep links map the same screens to portal pages - they become Related links, not the `?` target, because the pages
  must open with no network); the how-to pages of the portal; the site page of decision 4.7 once it exists.
- Siblings: StreamsPlayer for Windows, where the stream bank, the user playlist and the live broadcast are shared (the
  catalog, add and import channel, and broadcast pages link to the matching Windows help; a Windows help page links
  back where SP-0230 allows); FastMediaSorter Android for the exchange; the hub's family map.

## 3. Constraints and dependencies

### 3.1 Constraints

- The pages make no request (rule 12). The tree allows cleartext traffic and plays arbitrary stream addresses, so the
  help view is locked to the bundled assets: scripting off, no network loads, external links handed to the system
  browser. Proven by the scripted check of rung 1 and by a page opened in airplane mode.
- One source of strings: names, labels and one-line summaries of the layer and the page come from the same string
  source as the UI, so a renamed button is renamed in its help by the same edit (rule 13); the termbase and the
  dictionary of S0030 apply to every title.
- F1 is handled only while one of the product's own surfaces has focus and only with a hardware keyboard; it is never
  a global hotkey, and it does not displace any chord listed in section 2.2.
- Screenshots are produced from the build by a script, per locale and theme, from demo content, with marker bounds
  from the element tree; none is hand-edited, none shows real data (rule 14).
- A help id never changes once a build has shipped with it (rule 15); an installed older version still reaches a page
  that explains its surface.
- The size of bundled pages and screenshots is measured in P1 against the package budgets of the `phone` and `wear`
  modules (WebP or equally small renders; locale x theme x surface count).
- The gate's place in the repository's normal checks, the generator's language, and the ledger handling of ported files
  that P2 touches are decided in P1, not here.
- The wording of every page is drafted in the product's voice and reviewed by the owner; no promotion and no "click
  here" (rule 16).

### 3.2 Related tickets

- **S0004** (phone shell, BlockNeedUserTest): the icon map (`app.help` joins it) and the pending TalkBack, gamepad and
  TV-remote sweeps the `?` action must pass.
- **S0030** (exchange words, product names, icons, portal links, Draft): the dictionary and names this ticket's titles
  use, the exchange glyphs, and the portal deep links that become Related links. Prefer S0030's terms folded first.
- **S0027** (In Progress), **S0028**, **S0029**, **S0031**, **S0022**: they reshape or add exchange screens; their
  slices of this ticket wait for them, and once the gate exists each of them adds its map row in its own definition of
  done (rule 18). Accepted debt before then lives in the baseline, which only falls.
- **S0033** (privacy policy, permissions list, Data safety): the no-request, no-telemetry stance is the same; the
  hosted page of 4.7 is the shared home for published help.
- **S0014** (free promotion, Draft): the store listing screenshots and the help screenshots can share one capture
  rig and one demo-content mode; listing copy and help copy stay separate sources.
- **S0013**, **S0026**, **S0034** (contract-sync runs): none of them takes `CONTEXT-HELP`; P0 here is its own run for
  this one contract, with its own registry row.
- **S0023** (QR scanner), **S0009** (widgets), **S0020** and **S0021** (receiver, broadcaster): own surfaces the
  inventory covers; no change to them is requested.
- **S0025** (deep dynamic audit, Draft): its emulator rig is a possible driver for the walk matrix; no dependency.
- **S0008** (watch face): out of scope by the contract.
- Sibling repositories: StreamsPlayer (Windows) SP-0230 (the same feature); the FastMediaSorter Android ticket of the
  same feature, once the canon session names its id.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0004, S0009, S0013, S0014, S0020, S0021, S0022, S0023, S0025, S0026, S0027, S0028, S0029,
  S0030, S0031, S0033, S0034.
- **1. Locale set of the help pages:** ANSWERED 2026-10-25: (a), as recommended. Options: (a) EN, RU and UK authored; the other ten declared tags fall back
  to English with the fallback recorded in the map (rule 13 allows it); (b) all 13, the ten machine-translated and
  marked as such, as the Windows sibling does; (c) trim `locales_config.xml` to the three authored locales first.
  Recommendation: (a), revisit (b) when the Windows pipeline's machine-translation step is shared.
- **2. The watch (Wear app):** ANSWERED 2026-10-25: (b), as recommended; the watch slice is not built yet. It has no keyboard and no top app bar, and the contract's rule 1 gives F1 nothing to
  do there. Options: (a) `none` for every route with the reason recorded; (b) one Help row on the watch that opens
  the phone's page through the existing "Open on phone" path, the routes `inherit` from it; (c) layers on the 7 routes.
  Recommendation: (b), (a) if the owner wants the watch left alone.
- **3. Page viewer:** ANSWERED 2026-10-25: (a), as recommended. Options: (a) an in-app view of the bundled pages with scripting off and Back returning to
  the screen (the contract's Android guidance); (b) the system browser on a copy of the pages. Recommendation: (a);
  (b) needs the pages copied out of the package first.
- **4. Gate adoptions:** ANSWERED 2026-10-25: yes to both, as recommended; `CHECK-VERDICT` / `CHECK-BASELINE` pointer `docs/contracts/AUTOMATED-CHECKS.md`. `CHECK-VERDICT` and `CHECK-BASELINE` are candidate consumers in the pointer README today
  (not adopted); the gate of rule 19 needs both. Likewise `APP-BEHAVIOUR` is "desktop, not binding on Android unless the
  owner says so" while `CONTEXT-HELP` cites its rules 4, 8, 9 and 11. Question: does the owner's request of 2026-10-16
  bind those rules as `CONTEXT-HELP` quotes them, and adopt the two check contracts? Recommendation: yes to both,
  recorded in the P0 run.
- **5. Port or write:** ANSWERED 2026-10-25: (a), as recommended; FastMediaSorter Android had landed it, so the resolver, map reader, viewer, generator and gate are ported with ledger rows. FastMediaSorter Android is a consumer of the same contract. Options: (a) port its
  resolver, map reader and gate with port-ledger rows once they exist; (b) write them here to the contract.
  Recommendation: (a) when FastMediaSorter Android lands first, otherwise (b); the standing law of `CLAUDE.md` section 2
  covers playback and broadcast code, so this is a choice, not an obligation.
- **6. The old help icons and the FastMediaSorter documentation link:** ANSWERED 2026-10-25 as recommended; `SupportDestination.HELP` now opens the help index; `TooltipDialog` / `HelpableRow` are left as they are (no layout sets their attributes, so there is no element-level use yet). Keep `TooltipDialog` / `HelpableRow` as
  the element level of the layer, or retire them; point `SupportDestination.HELP` at this product's own help instead of
  the FastMediaSorter documentation. Recommendation: reuse the dialog as the element detail, repoint `HELP` to the
  help index, both inside the slices that touch them.
- **7. Layer or page per surface:** the project's call, made in P1 and recorded in the map; the owner reviews the map
  once. Not asked now.
- **8. Slice order:** ANSWERED 2026-10-25: as proposed; this run built slices 1-4. Proposed: (1) the catalog and its sheets and dialogs, (2) the fullscreen player, (3) the
  broadcast screen, its settings panel and the QR views, (4) the QR scanner and the widget configuration, (5) the
  exchange account screens after S0027 and the rest of the exchange block, (6) the watch. Recommendation: as proposed.
- **9. Queue placement and release:** the owner orders the queue (`CLAUDE.md` section 4). The harness added this ticket
  to the end of package 1 in `PLAN/RELEASE_QUEUE.md` automatically when the record was inserted - that is the store's
  own projection of a new ticket into the current package, not a scheduling decision; the owner moves it, or leaves it,
  and says which release first ships the feature.
- **10. A `?` on the surfaces that inherit a page:** RESOLVED WITHOUT ASKING 2026-10-29, by the contract. Rule 2
  reads "a surface that has help carries a help button", rule 4 counts an `inherit` row as help, and the one
  exception the catalog discusses (`PROPOSAL-2026-10-25-tourist-android-findings`, an `ask: "off"` member) exists
  precisely because an `inherit` row otherwise wires both the key and the button. So channel actions, recently
  played, the sleep timer and the two import dialogs carry the `?` in their title row; it opens the catalog page at
  the row's anchor, as F1 already did.

### Assumed (unattended, 2026-11-12)

Each choice is reversible inside this ticket; the owner may overrule any of them.

1. **The Connection check dialog (`ExchangeDiagnosisDialog`) is an `inherit` row of `ExchangeActivity` at the anchor
   `diagnose`**, with the `?` in its title row and F1 through `HelpKeys.install`, the same shape as the change-password
   dialog. Weighed against: a `page` of its own (needs a demo state with a failed connection for the capture, and the
   exchange page already explains the check in its `diagnose` entry) and `none` (the dialog is a multi-step surface, not
   a one-question confirmation, so rule 4 does not excuse it). Resolved by item 10 of this section.
2. **The mark in the help is the vector form, not the PNGs the S0039 hand-over names.** `svg/exchange-mark-full.svg` at
   128 px above the title of the `exchange` page (rule 10) and `svg/exchange-mark-small.svg` at 28 px beside the title of
   the `broadcast` and `catalog` pages (rule 11), both copied byte for byte into `phone/src/main/assets/help/img/`
   (SHA-256 equal to the artwork manifest) and marked `binary` in `.gitattributes`. Weighed against `png/full-256.png`,
   `png/mark-24.png` and `png/mark-32.png`: a PNG shown at 128 or 28 CSS px is scaled up on a 2x or 3x screen, which rule 5
   forbids, and the SVG is a manifest file under any name. Opens as a plain `<img>`, which the viewer's scripting-off
   WebView renders.
3. **The `alt` of both pictures is the full name "Media Exchange Server" in every locale.** The page title of the
   `exchange` page reads "Exchange server", not the full name, so the `alt` is the full name (rule 10, 0.4 wording); the
   gate also refuses an empty `alt`. On the `broadcast` and `catalog` pages the `alt` gives the picture its name.
4. **Rule 11 reaches three pages: `exchange` (head form), `broadcast` and `catalog` (inline).** The catalog page
   explains `own-status`, whose line draws the mark about the server (`MainActivity`, `tvOwnStatus`); the broadcast page
   explains `publish-state`. Not added: `player` (the mark is on dialogs it opens, which the page does not describe) and
   `watch` (its screens' marks are S0022 states the page does not describe). One `"mark": "inline"` line in the page's
   source and one generator run add either; the catalog's picture will show the mark only in a state that names the
   server, which the capture's demo state does not have.
5. **The five stale picture sets are not accepted into the baseline.** `shot:` lines would turn the gate green without a
   recapture; the recapture is a release run (section 7, evidence), so the gate stays red on them until then.

## 4. Definition of done

Per slice (static predicates; each proof is the repository's check or a recorded command output):

- Every surface in the slice has a map row; no map row without a surface; every `none` / `inherit` row has a reason.
- F1 on each help-bearing surface (hardware keyboard attached) and its `?` action open the same target; the `?` action
  is the only control added to its corner - proven by a before / after dump of the control tree; F1 is not a global
  hotkey and no listed chord changed.
- Each page has the eight parts of rule 8 in order, an `alt` text, `lang` and `dir`, no request to any origin, no script
  needed to read it, and every link resolves (offline for local targets, online for the site's at release).
- Pages and screenshots exist in every shipped locale of item 1; the screenshot's language equals the page's.
- Screenshots come from the generator; none is hand-edited; demo content only.
- The gate runs in the repository's normal check and its verdict line follows `CHECK-VERDICT`; the baseline only falls.

Before the release that first ships the feature:

- The walk matrix (rung 3): every surface x locale x theme, F1 and the `?` action, one row per surface, in
  `temp/S0035/` (not committed) with the summary in the closing note.
- `docs/contracts/CONTEXT-HELP.md` and its row in `docs/contracts/README.md`; the registry row verified and dated
  (`partial` while it is partial); `.sza-canon.json` and `CLAUDE.md` name the help map; the user-visible entry in the
  ledger; the README and the how-to link to the help index (P5); the closing note recorded in this ticket (P6).
- `pwsh -NoProfile -File tools/harness.ps1 spec_catalog/validate` reports no new FAIL.

## 5. Not decided here (the project decides, and records why)

- Which form (layer or page) each surface gets; whether pages open in the product's own view or the system browser
  (item 3); the screenshot tooling; the slice order (item 8); whether the broadcast settings panel gets one page with a
  section per group or one page each.
- The wording of every page: drafted in the product's voice, reviewed by the owner.
- Anything outside `CONTEXT-HELP` section 5: tours and first-run onboarding (`APP-BEHAVIOUR` rule 11), single-control
  tooltips, the site's own structure, a command line's `--help`, a watch face.

## 6. Closing note of the first run (2026-10-25 / 26) - partial

Scope of the run, by the owner's answers (section 3.3): P0-P5 for the phone, slices 1-4 (catalog and its sheets and
dialogs, the fullscreen player, the broadcast screen and its settings panel, the QR scanner and the channel-tile
setup). The exchange screens (slice 5) and the watch (slice 6) are not built; the wording is drafted, not reviewed by
the owner.

**What exists.**
- P0: pointers `docs/contracts/CONTEXT-HELP.md` and `docs/contracts/AUTOMATED-CHECKS.md` (`CHECK-VERDICT` 0.13,
  `CHECK-BASELINE` 0.11), their rows in `docs/contracts/README.md`, the bindings in `tools/contracts/check-sync.ps1`,
  and the three own registry rows (`partial`, 2026-10-26).
- P1: `phone/src/main/assets/help/help-map.json` - 30 enumerated surfaces: 8 `page` (catalog, Filter & Sort, Add
  channel, video player, Broadcast Control, broadcast settings, QR scanner, channel-tile setup), 5 `inherit` with the
  parent's entry as the anchor (channel actions, recently played, sleep timer, import playlist, import broadcast), 7
  `none` with a reason (portal topic menu, broadcast password prompt, the viewer, four hand-off activities), 10 in the
  baseline (3 exchange surfaces, 7 watch routes). Decision for item 7: pages only; no surface got a layer, because
  every one is explained better by a picture with numbers than by an overlay on a live screen that changes with its
  state (the radio bar, the live controls).
- P2: `HelpOpenManager` is the one resolver entry; F1 is handled by `HelpKeys` in `dispatchKeyEvent` of the five
  help-bearing activities and, through a wrapped window callback, inside the sheets and dialogs; the `?` action is in
  the top bar of the catalog and the tile setup, in the title row of the broadcast screen, the filter sheet and the Add
  channel dialog, in the player's top bar and in the corner of the QR scanner. `SupportDestination.HELP` opens the
  help index. Placement decisions beyond the contract text: the catalog's visible actions (view mode, Filter & Sort)
  are `always` so the `?` does not push Filter & Sort into the overflow on a 360 dp phone (AppCompat's slot count is
  not configurable), with a `menu-w500dp` variant that keeps Broadcast Control in the bar there too; the player's top
  bar and the QR scanner's corner take the status-bar inset - found by the walk: the `?` and the player's existing
  buttons sat under the status bar and could not be tapped on API 35.
- P3: pages in EN / RU / UK generated from `docs/help/content/*.json` by `tools/help/generate-help-pages.ps1`
  (offline, no script, `lang` / `dir`, the eight parts of rule 8); 48 screenshots (8 pages x 3 locales x 2 themes) from
  `tools/help/capture-help-screenshots.ps1` with fictional demo channels (`HelpDemoSeedReceiver`, debug source set only,
  `.invalid` addresses) and markers from the UI tree; the viewer `HelpPageActivity` (WebView, scripting off, no network
  loads, external links confirmed and handed to the browser). Pictures are JPEG at 540 px (about 3 MB for the whole
  set with the pages): the tool has no WebP encoder. The other ten declared interface languages show the English pages
  (item 1a); a right-to-left locale mirrors the body of the English page (ported behaviour).
- P4: `tools/help/assert-help-map.ps1` (surfaces, rows, reasons, parents, anchors, pages per language, links,
  offline rules, page shape, baseline) with a 14-case self-test `tools/help/test-help-gate.ps1`, run by
  `tools/release/pre-release-sweep.ps1` as `HelpMap`.
- P5: README section Help, `CHANGELOG.md` entry, `docs/README.md`, `docs/PHONE_SHELL_ICON_MAP.md` (`app.help`),
  `CLAUDE.md` section 5 names the map and the tools. `.sza-canon.json` has no field for a help map and is unchanged.
- Port ledger: 12 rows (HelpMap, HelpTargetResolver, HelpOpenManager, HelpPageActivity, HelpTooltipManager,
  HelpPageBodyLoader, the resolver test, `activity_help_page.xml`, `help.css`, the generator, the enumerator, the gate).
  Ported files that were then edited here: `SupportIntentFactory` and `SupportDestination` (the help entry),
  `BroadcastControlActivity`, `FullscreenPlayerActivity`, `StreamLaunchWidgetConfigActivity`, `QrScannerActivity`,
  `MainActivity` (F1 and the `?`). The capture script is written here, not ported: its source depends on
  FastMediaSorter's device rig.

**Evidence (2026-10-26).**
- `pwsh -NoProfile -File tools/help/assert-help-map.ps1` exit 0: `PASS (30 surfaces, 20 rows, 10 baseline, 27 pages)`.
- `pwsh -NoProfile -File tools/help/test-help-gate.ps1` exit 0: `PASS (14 cases)`.
- `:phone:testStoreDebugUnitTest --tests 'com.sza.streamsplayer.ui.common.help.*'`: 17 tests, 0 failures, 0 skipped.
- `:phone:assembleStoreDebug` builds (with `-Pkotlin.incremental=false`, see the last paragraph).
- Control diff, `temp/S0035/control-diff.txt`: `diff-control-trees: PASS (8 surface(s), only help controls added)`; before
  = the build installed on the shared emulator at 14:36 on 2026-10-25, after = this tree. Named allowances: the
  tile-setup close icon gained its content description (an accessibility fix), the Add channel dialog's title view is
  now the layout's own (it carries the `?`), and `SettingsToggleRow` reports `CheckBox` instead of `Switch` - a change
  another session made at 21:24 (not S0035).
- Walk (rung 3), emulator Pixel_9 API 35, `temp/S0035/walk-final.tsv`: on every one of the 8 pages x EN / RU / UK x
  light / dark (48 rows) F1 opens the help viewer and the `?` opens the same page. One extra row of the log is a retry
  of a QR scanner run that then passed.
- The capture script's `-TreeDir` and `-F1Proof` modes and `tools/help/diff-control-trees.ps1` produce that evidence;
  rerun them after a layout of a mapped surface changes.

**Not done / open.**
1. Exchange screens (slice 5): `ExchangeActivity`, `ExchangeDiagnosisDialog`, `dialog_exchange_password` are baseline
   lines for S0027 / S0031; each of those tickets adds its map row in its own definition of done.
2. The watch (slice 6, item 2b): built 2026-10-27, see the addendum below.
3. The owner's review of the wording of the 8 pages x 3 languages and of the map (item 7).
4. Rung 2 as a gate: the capture script regenerates the pictures, but nothing fails when a committed picture is
   stale. Candidate: a layout hash per page recorded in `img/capture.json`.
5. Rung 3 on a device: TalkBack, gamepad and TV-remote sweeps of the `?` (the S0004 sweeps are pending as well); the
   physical phone was not touched. `INPUT-PARITY` evidence for the new buttons is the emulator walk only.
6. Surfaces that `inherit` (channel actions, recently played, sleep timer, the two import dialogs) carry no `?` of
   their own: F1 reaches the parent's entry and the parent's `?` is behind the sheet. Rule 2 may be read to want a
   button there; a decision for the owner.
7. `TooltipDialog` / `HelpableRow` (item 6): untouched; nothing sets their attributes, so there is no element-level
   use yet.
8. Publication on the product's site: waits for the site page of decision 4.7 (S0033 hosts only the privacy pages).
9. S0036 will move layouts: rerun the capture and the control diff after it, before the release that ships this.
10. No release build was made (R8 has not seen the new classes; the viewer was exercised only in the debug build).

**Addendum - slice 6, the watch (2026-10-27).**
- Watch: a Help chip on `WearRoutes.HOME` (`MainActivity`, string `sp_help`) sends the token `help:wear:WearRoutes.HOME`
  (`WEAR_HELP_TOKEN_HOME`, kind `help`) through the existing `WearOpenOnPhoneRepository`; its dialog says "Help opened
  on phone" / "Help could not be opened" (`sp_help_shown`, `sp_help_not_found`) and keeps the shared texts for a
  notification, notifications off and an unreachable phone. Strings in EN, RU and UK.
- Phone: `PhoneWearListenerService.openHelpPage` answers a `help:` token only for a surface id that starts with
  `wear:`, resolves it with the new `HelpOpenManager.intentFor` (the same resolver as F1 and the `?`, rule 17), starts
  the help viewer when the app is in front and otherwise posts the notification (`OpenOnPhoneNotifier` gained an
  `openIntent`). `NOT_FOUND` is the answer for any other id.
- Map: `wear:WearRoutes.HOME` is a `page` row for the new help id `watch`; the other six routes `inherit` from it with
  the anchors `channels`, `player` (audio and video), `broadcast`, `qr` and `tile`. The seven baseline lines are gone;
  the baseline holds the three exchange lines. Source `docs/help/content/watch.json` in EN, RU and UK (7 elements, 5
  steps, related catalog, player and broadcast); the page has no picture, as nothing here captures the watch.
- Evidence: `assert-help-map.ps1` exit 0 `PASS (30 surfaces, 27 rows, 3 baseline, 30 pages)`; `test-help-gate.ps1` exit
  0 `PASS (14 cases)`; `generate-help-pages.ps1` exit 0 `PASS (files=31 written=6 pages=9)`;
  `:phone:testStoreDebugUnitTest --tests 'com.sza.streamsplayer.ui.common.help.*' --tests '*ExchangePortalLinksTest'`
  28 tests, 0 failures, with two new tests (every watch route reaches the `watch` page with its anchor in the shipped
  map; the watch's token constants equal the phone's); `:wear:testStoreDebugUnitTest --tests '*domain.model.*'` passed.
  Nothing was run on a watch.

**Findings about the environment, for whoever builds next.** Parallel sessions share this tree. Gradle needs the
`Build.Main` lock (the harness queue helper); a stale Kotlin / Hilt incremental state shows up as `Unresolved
reference` or a missing `Hilt_*` class and is cured by `-Pkotlin.incremental=false` after a clean in its own
invocation; the Kotlin daemon keeps `R.jar` open on Windows; other sessions restart the adb server and close
emulators, and an emulator on a console port above 5584 must be re-attached with `adb connect localhost:<port+1>`.
Nothing in the contract text needs a change; no proposal to the catalog is filed.

## 7. Closing note of the second run (2026-11-12) - slice 5 and the exchange mark

S0027 (`BlockNeedUserTest`) and S0031 (`Implemented`) have left the work-remaining set, so the wait on them ended. The
exchange screen already carried its map row, page, `?` and F1 from S0027; this run built what was left of slice 5 and
the hand-over of S0039. Unattended pass; no build, no test run and no device run, by `CLAUDE.md` section 5a.

**What changed.**
- Connection check dialog: `HelpSurfaceIds.EXCHANGE_DIAGNOSIS`, the `inherit` row of `ExchangeDiagnosisDialog` in
  `help-map.json` (parent `ExchangeActivity`, anchor `diagnose`), the title row of `dialog_exchange_diagnosis.xml` with
  a 48 dp `?` (`btnDiagnosisHelp`, `@string/help`, `ic_help_outline`), and `ExchangeDiagnosisDialog.setupHelp()`
  (`HelpKeys.install`, the tooltip, the click opens the help). Its line left `tools/help/help-map-baseline.txt`, which
  now holds no surface. One probe line `Timber.d("S0035: ..")` stands at the `?` click while the ticket waits for the
  device test.
- Exchange mark: `generate-help-pages.ps1` reads an optional `"mark": "head" | "inline"` from a page source
  (`New-HeroHtml`), `help.css` carries `.mark-head`, `.hero-inline` and `.mark-inline` (with the fallback and the
  right-to-left rule of the old-WebView block), `exchange.json` is `head`, `broadcast.json` and `catalog.json` are
  `inline`, the two SVGs are vendored under `phone/src/main/assets/help/img/`, `.gitattributes` marks them `binary`,
  and `docs/DOCUMENT_REGISTRY.jsonl` has the record `help-exchange-mark`. The pages were regenerated (9 pages in 3
  locales, plus `help.css`). `generate-help-pages.ps1` and `help.css` are ported files edited here; their sources did not
  change, so the port ledger rows keep their hashes and the drift check is unaffected.
- `HelpTargetResolverTest` gained one test: the exchange screen and its two dialogs reach the `exchange` page, at the
  anchors `change-password` and `diagnose`, in the shipped map.
- `CHANGELOG.md` has the user-visible entry; `tools/help/README.md` no longer says the diagnosis dialog is unmapped.

**Evidence (2026-11-12, repository root).**
- `pwsh -NoProfile -File tools/help/generate-help-pages.ps1 -Check`: exit 0, `generate-help-pages: PASS (checked=37 pages=11 locales=en,ru,uk date=2026-10-29)`, 2.65 s.
- `pwsh -NoProfile -File tools/help/assert-help-map.ps1 -CommittedBaseline tools/help/help-map-baseline.txt`: exit 1,
  `assert-help-map: FAIL (5 finding(s))`, 0.75 s - exactly five `STALE-SHOT` (`broadcast`, `broadcast-settings`,
  `catalog`, `filter-and-sort`, `qr-scanner`), the same five before this run's first edit; no `UNMAPPED`, `PAGE-SHAPE`,
  `BROKEN-LINK`, `MISSING-PAGE` or `STALE-BASELINE`. Without `-CommittedBaseline` the gate exits 2, `COULD NOT VERIFY`:
  the working tree has no commit that carries `tools/help/help-map-baseline.txt`, so `BASELINE-RISE` cannot run.
- `sha256sum` of the two vendored SVGs: `420351bf93ea109af614c601d8a8ed026eb9ffa514ce59e46b51db7b5d495ef9` and
  `2e1e685017a553e48cdfaf1f7529b559d0cce96d9240df987057e284b80fe7cc`, equal to the artwork manifest entries.
- `tools/docs/assert-doc-registry.ps1` (1.27 s), `assert-doc-links.ps1`, `assert-doc-style.ps1`,
  `assert-translation-freshness.ps1`, `assert-termbase.ps1`: each exit 0 with its `PASS` line; the first run of the
  registry gate failed on the two new SVGs until their record existed. `tools/release/check-syntax.ps1`: exit 0.
- The edited layout and the map parse as XML and JSON. Compile gate: `.sza-profile.json` declares no
  `commands.compileCheck`; no compile command, so the Kotlin and the layout are not compiled and the test is not run.

## Last Audit

**Date:** 26-11-12 01:40 | **Mode:** static | **Outcome:** Partial
**Counts:** PASS 4 | WARN 2 | FAIL 0 | MANUAL 4 | UNCHECKABLE 1 | EXEMPT 0 | RELEASE 6

### Action items
1. WARN - The watch help page has no screenshot; nothing here captures the watch, and the contract treatment of a watch page without a picture is not settled (carried from the first audit).
2. PASS - `pwsh -NoProfile -File tools/help/generate-help-pages.ps1 -Check`, exit 0: `generate-help-pages: PASS (checked=37 pages=11 locales=en,ru,uk date=2026-10-29)`; the pages equal their sources, the mark pictures resolve.
3. WARN - `pwsh -NoProfile -File tools/help/assert-help-map.ps1 -CommittedBaseline tools/help/help-map-baseline.txt`, exit 1, verdict `FAIL (5 finding(s))`: only the five named `STALE-SHOT` findings, which the layout edits of S0036, S0039 and others after the 2026-10-29 capture caused and which item 7 closes; every surface is mapped, the baseline holds no line, and there is no broken link or page-shape finding. The same five were red before this run's first edit, so none is new.
4. PASS - the vendored `exchange-mark-full.svg` and `exchange-mark-small.svg` carry the SHA-256 of their artwork manifest entries (`sha256sum`).
5. PASS - `tools/docs/assert-doc-registry.ps1`, `assert-doc-links.ps1`, `assert-doc-style.ps1`, `assert-translation-freshness.ps1` and `assert-termbase.ps1`, each exit 0 with its `PASS` line; `tools/release/check-syntax.ps1` exit 0.
6. PASS - `grep -rn -E 'Timber\.[a-z]+\(.*S0035' phone/src wear/src watchface/src exchange-core/src`: one line, `ExchangeDiagnosisDialog.kt:83`, the probe of the status `BlockNeedUserTest`.
7. RELEASE - `pwsh -NoProfile -File tools/help/capture-help-screenshots.ps1 -Serial <emulator-serial>`, then `generate-help-pages.ps1`: recapture the five stale sets and take the `exchange` set (mark in place); also `-F1Proof` and `-TreeDir` with `diff-control-trees.ps1` for the control diff; runtimes unmeasured. Until it runs the help gate stays red on the five `STALE-SHOT`.
8. RELEASE - `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1`: release builds (R8 on the help classes and the viewer), the phone unit suites with the new `HelpTargetResolverTest` case, the tool self-tests; above 5 seconds or unmeasured.
9. RELEASE - `pwsh -NoProfile -File tools/docs/test-docs-gates.ps1` (7.889 s, 2026-11-08): the generator output changed, so the doc-gate self-test runs at release.
10. RELEASE - `pwsh -File <catalog>\exchange-mark\reference\Find-ExchangeMark.ps1 -RepoRoot . -Require svg/exchange-mark-full.svg,svg/exchange-mark-small.svg`: a repository walk with no recorded time; the hashes were checked directly (item 4).
11. RELEASE - `pwsh -NoProfile -File tools/help/test-help-gate.ps1` and `tools/port-ledger/check-drift.ps1`: not edited this run, listed for the release start as the standing checks of this ticket's tools.
12. RELEASE - the Kotlin and layout edits (`ExchangeDiagnosisDialog`, `dialog_exchange_diagnosis.xml`, `HelpSurfaceIds`, `HelpTargetResolverTest`) are not compiled: no compile command is declared and `CLAUDE.md` section 5a allows no build between releases.
13. UNCHECKABLE - the end-to-end walk of the Connection check dialog (`?` tap, F1, the page opening at the `diagnose` entry) and the look of the mark in the viewer: no mechanical check below a device or emulator run.

### Manual / on-target
- [ ] Owner reviews the map and the 11 help pages and the index in EN, RU and UK, including the mark on `exchange`, `broadcast` and `catalog`, and the Assumed choices above.
- [ ] On a device (or the emulator) provoke a failed connection, open the Connection check dialog, tap `?` and press F1 with a hardware keyboard: both open the exchange page at "Why won't it connect?"; TalkBack reads "Help"; the mark shows in the viewer in light, dark, API 27 and API 35, and mirrors correctly in a right-to-left locale.
- [ ] Walk the watch Help row with a paired phone, including background, notifications disabled and phone unreachable.
- [ ] Walk TalkBack, gamepad and TV-remote access on a physical device with the S0004 sweeps.
