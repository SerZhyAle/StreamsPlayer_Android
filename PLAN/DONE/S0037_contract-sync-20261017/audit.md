# S0037 - clause-to-surface audit, 2026-10-27

Evidence file of `PLAN/S0037_contract-sync-20261017.md`. Written by the StreamsPlayer Android session of this
repository. Contracts are cited by id and section; the catalog is located only through `CLAUDE.md`.
Nothing here is a release, a tag, an upload or a deployment. "Held" means shown by code reading and, where
named, by a fresh test run; "needs device" means no physical-device, TalkBack, gamepad, TV-remote or paired-watch
evidence exists for it.

## 1. Method and authority

- The catalog was re-read on 2026-10-27 at its **current** versions, not at the targets the ticket was cut with on
  2026-10-17 (the catalog has moved: `DEVICE-EXCHANGE` 1.10, `LIVE-BROADCAST` 1.6, `UI-SIZING` 1.1, `HARNESS-PROFILE`
  0.13, `ICON-SET` 0.36, `MEDIA-CLASSIFICATION` 0.12). Targets of the ticket that the catalog has superseded are
  read at the newer version, as the owner's rule of 2026-10-14 (S0026) says.
- Overlapping tickets were mapped before anything was written, so that nothing is done twice:
  S0013 (closed 2026-10-23: `STREAM-BANK` 2.4, `LIVE-BROADCAST` 1.3, `DEVICE-EXCHANGE` 1.7 changelog read, `ICON-*`,
  `INPUT-PARITY` 0.4 read, `MEDIA-CLASSIFICATION` scope), S0026 and S0034 (`DEVICE-EXCHANGE` 1.7, vectors, joint
  runs), S0035 (`CONTEXT-HELP`, `CHECK-VERDICT`, `CHECK-BASELINE`), S0036 (unified UI, closed 2026-10-26), S0041 (open:
  `REPO-STAMP` / `HARNESS-PROFILE` 0.13, row shapes), S0027, S0028, S0032, S0042, S0043, S0044 (exchange and broadcast
  work in progress).
- Registry cells: this repository does not edit the catalog (`CLAUDE.md` section 6). The own-row changes and the
  dated exceptions are a patch for the canon session: `registry-sync.patch` and `registry-sync-reason.txt` beside
  this file.
- Rights retained (ticket section 4; catalog RULES section 15; canon CONTRACTS section 4): every deviation below is
  a justified exception recorded in this repository's own rows, with clause, scope, evidence, impact, compensating
  behaviour, responsible project and review condition; temporary debt has an `until` date and an open ticket. Where
  the catalog's own text looks wrong or incomplete, a recommendation is listed in section 9. A recommendation is not
  a waiver.

## 2. Contract table

| Contract | Ticket target | Current 2026-10-27 | Own row before | Outcome | Carrier |
| --- | --- | --- | --- | --- | --- |
| `BOOLEAN-OPTION` | 2.0 | 2.0 | none | partial: shared row on the broadcast settings panel, four boxes elsewhere not migrated, no captures | S0049 |
| `BUTTONS` | 2.0 | 2.0 | none | partial: shared flow layout in 5 of 33 layouts, 14 layouts not migrated, no captures | S0049 |
| `COLLAPSIBLE-GROUP` | 1.1 | 1.1 | none | component built and tested, used by no screen | S0049 |
| `CONTEXT-HELP` | 0.1 | 0.1 | partial 0.1 (2026-10-26) | unchanged, owned by S0035 | S0035 |
| `DEVICE-EXCHANGE` | 1.3 | 1.10 | partial 1.7 | see section 6 | S0043, S0042, S0028, S0032, S0021 |
| `DOC-EXTERNAL-QUALITY` | 0.12 | 0.12 | none (S0030 patch never applied) | partial: contributor of the shared portal, offline help, three hosted privacy pages | S0051 |
| `DOC-INTERNAL-QUALITY` | 0.10 | 0.10 | none | read, not implemented: no document registry, no link / style / reverse-coverage gate | S0051 |
| `HARNESS-PROFILE` | 0.12 | 0.13 | 0.12 (2026-10-23) | target 0.12 held since S0013; 0.13 is S0041's | S0041 |
| `ICON-RENDER` | 0.24 | 0.24 | 0.24 (2026-10-23) | unchanged; covered-component rendering rides on the unified-UI work | S0049 |
| `ICON-SET` | 0.34 | 0.36 | 0.36 (2026-10-23) | unchanged; target 0.34 is below the version already read | - |
| `INPUT-PARITY` | 0.4 | 0.4 | pending, Reads 0.4 | applicability sweep done (section 5): partial, 13 gaps | S0052 |
| `LIVE-BROADCAST` | 1.1 | 1.6 | partial 1.3 | see section 6 | S0043, S0042, S0044, S0028, S0032 |
| `MEDIA-CLASSIFICATION` | 0.11 | 0.12 | pending | not applicable, re-read at 0.12; declared | - |
| `SETTINGS-NAVIGATION` | 2.0 | 2.0 | none | components built and tested, no reachable settings surface or About page | S0049 |
| `STREAM-BANK` | 2.4 | 2.4 | partial 2.4 | artwork rules 9/12 stay a dated exception; carrier filed | S0050 |
| `UI-SIZING` | 1.0 | 1.1 | none | partial: profile record and token resolver, used by 3 layouts, no measurements | S0049 |

Also reviewed because the repository's own checks name them: `REPO-STAMP` / `RULE-DELIVERY` reader exceptions
(closed, section 7).

## 3. Unified UI: `UI-SIZING`, `BUTTONS`, `BOOLEAN-OPTION`, `COLLAPSIBLE-GROUP`, `SETTINGS-NAVIGATION`

S0036 closed on 2026-10-26 as Verified, with "all slices 0-7 implemented and verified". The tree read on
2026-10-27 supports a narrower statement. Counts are from `phone/src/main/res/layout` (33 files) and
`Grep` over `phone/src`:

| Clause family | Surface | What exists | What is missing | Verdict |
| --- | --- | --- | --- | --- |
| `UI-SIZING` sections 2-3: profile, token resolver, 48 dp floor | phone | `docs/design/UI_SIZE_PROFILE.md`; `UiSizeTokens` (8 tests); `dimens_ui.xml`; `ui_touch_target` used by 3 layouts | no measured target sizes, no scale / large-text captures | partial |
| `BUTTONS` content-width, row-equalised flow, visible answers | phone | `CommandFlowLayout` / `CommandFlowGeometry` in `activity_broadcast_control`, `activity_exchange`, `dialog_exchange_diagnosis`, `sheet_sleep_timer`, `view_settings_about` | 14 layouts still hold plain `MaterialButton` / `Button` / `ImageButton` rows: `activity_main`, `activity_qr_scanner`, `custom_stream_player_controls`, `dialog_import_broadcast`, `item_exchange_broadcast`, `item_recently_played_channel`, `item_stream_channel`, `sheet_recently_played`, `sheet_stream_filter`, `view_dialog_title_help`, `view_settings_dropdown_row`, `view_settings_input_row`, `view_settings_toggle_row`, `view_collapsible_section_header` | partial |
| `BOOLEAN-OPTION` leading checkbox, caption activation, help split | phone | `SettingsToggleRow` + `SettingsCheckBox` in `fragment_settings_broadcast` (portrait and landscape); no `MaterialSwitch` left in any layout | three `MaterialCheckBox` in `activity_exchange` and one `CheckBox` in `sheet_stream_filter` do not use the shared row | partial |
| `COLLAPSIBLE-GROUP` | phone | `CollapsibleSectionHeader`, `CollapsibleSectionsManager`, `CollapsibleSectionStore` (8 tests) | referenced by no activity, fragment or layout except `view_collapsible_section_header.xml` | not realised on any screen |
| `SETTINGS-NAVIGATION` sections 2-3: rail, ordered pages, About | phone | `SettingsNavigationRail`, `SettingsDestination`, `SettingsAboutDestinationView`, `DiagnosticReportExporter` (7 tests) | referenced only by their own tests; the manifest lists no settings activity; the only settings surface is the broadcast panel inside Broadcast Control, with no rail, no first page of theme / language, no About page. The contract applies "even to settings with only one or two editable options" (section 1) | not realised on any screen |
| all five, Wear | wear | permanent variant V2 recorded in `docs/design/UI_SIZE_PROFILE.md` section 4 (Compose chips, 48 dp, single-column page lists) | no per-surface applicability table; no captures | variant recorded, unverified |
| all five, watch face | watchface | resources only, no configuration surface | the applicability record is one line in the profile | not applicable, declared |
| evidence of sections 5/6 (captures, contrast, accessibility, page timings) | all | unit tests of geometry and state; the scratch folder of S0036 (not committed) holds four inventory scripts and a build log only, listed 2026-10-27 | no rendered capture, no contrast measurement, no TalkBack or keyboard walk, no page-switch timing (warm p95 < 150 ms, first < 500 ms) | not produced |

Outcome: **temporary migration debt**, until 2026-12-21 (the owner-approved deadline recorded in the registry's
unified-UI migration entry of 2026-10-26), remediation ticket **S0049** (filed today; S0036 is closed and cannot
carry it). No adoption cell advances to a claim beyond `partial`; `COLLAPSIBLE-GROUP` and `SETTINGS-NAVIGATION` are
claimed as `-` (built, not reachable) rather than `partial`.

Fresh run (section 10): the component unit tests pass inside the phone suites (1158 store / 1160 noLegal, 0
failures); that supports "the components behave", not "the screens conform".

## 4. `STREAM-BANK` 2.4, `MEDIA-CLASSIFICATION` 0.12, `ICON-*`, `HARNESS-PROFILE`

- **`STREAM-BANK` 2.4.** Held as recorded by S0013 on 2026-10-23: named columns, unknown-column tolerance,
  `General` topic fold, closed language / country folds, merge by stable key preserving user rows, 32 MiB playlist
  bound, publish-window 404 retry (`ImportStreamCatalogUseCase.kt:177`). Fresh phone suites 2026-10-27 pass
  (including the live-ZIP and `topic-fold-v1.tsv` tests). **Exception rules 9/12 (artwork-manifest reader,
  on-demand logo / preview tile packs) stays**, favicon-only rendering stays usable, the ZIP pass proves nothing
  about it. It had no feature carrier; **S0050** is filed and named in the exception. The "partial artwork verdict"
  the ticket asks to preserve is preserved unchanged.
- **`MEDIA-CLASSIFICATION` 0.12.** Re-read: outside primary-category classification (accepted scope, 0.11); AUDIO /
  VIDEO / RTSP routing implements `STREAM-BANK` document 03 section 4 only. Nothing owed. The declared scope is
  preserved. S0041's open question (how a not-applicable row spells `Verified`): the catalog already carries
  not-applicable rows with `-` / `-` and a date (the sza.od.ua hub rows for `BOOLEAN-OPTION`, `COLLAPSIBLE-GROUP`,
  `SETTINGS-NAVIGATION`, 2026-10-21), so the proposed row does the same.
- **`ICON-SET` 0.36 / `ICON-RENDER` 0.24 / `ICON-EXTERNAL` 0.12.** Rows stand as S0013 left them; shipped-surface
  evidence remains the 2026-10-10 emulator sweep. Not re-measured. Covered-component rendering (0.21-0.24) is part
  of S0049.
- **`HARNESS-PROFILE` 0.12 target.** Held since S0013 (row 0.12, verified 2026-10-23). The catalog is at 0.13; the
  pointer and the row move in S0041, not here.

## 5. `INPUT-PARITY` 0.4 - applicability sweep and audit

Code reading of the phone and Wear modules against sections 2-6 and the `VIEWER-KEYBOARD` profile (an independent
read-only pass over `phone/src/main`, `wear/src/main`; claims below were spot-checked, e.g.
`FullscreenPlayerActivity.kt:133-136` handles only F1). No device, no TalkBack, no gamepad, no TV remote, no paired
watch; the 2026-10-10 Pixel_6 emulator D-pad walk (S0004) is the only runtime evidence.

**Not applicable, with the contract's reason:** `INPUT-CHORD` (the product stores and captures no chord); the 0.4
additions for files and images (zoom, rotate, frame-step, speed, slideshow, destination-move, thumbnail, random,
jump, first / last, step-ten / hundred: no files or images); `seek`, `select`, multi-select and phone `volume`
(live streams, no seek bar, no multi-select, system volume); Pen column; Android TV (no `leanback`, no
`touchscreen required=false`: the app is not installable on a TV by default); watch face, tile and complications
(input belongs to the system); Wear keyboard / D-pad (rule 1 binds devices with a remote or gamepad).

**Held by code reading:** touch on catalog, player, Broadcast Control, Exchange, QR scanner and Wear; focus path
and activation by D-pad / keyboard on the catalog (`item_stream_channel.xml`, `activity_main.xml`), Broadcast
Control (`activity_broadcast_control.xml` explicit `nextFocus*`) and Exchange; `context` (Menu / Shift+F10 / Y,
`StreamCatalogInputManager.kt:23-32`); `search` (Ctrl+F / Start); gamepad stick navigation and page on the catalog
(`GamepadNavigationTranslator.kt`); `help` F1 on catalog, player, Broadcast Control, QR scanner, widget config and
five dialogs (`HelpKeys.kt`); Back not hijacked and cancel never destructive; radio-bar play / pause and
media-key play / pause through the Media3 session (`AudioPlaybackService.kt`); Wear back
(`SwipeDismissableNavHost`), rotary scroll (`WearListColumn.kt:339-350`).

**Gaps (each is a concrete defect against an applicable clause):**

1. Video player: `play-pause` has no Space / K binding (only the focused button reacts).
2. Video player: `next/previous` only through two buttons; no PageUp / PageDown, X / Y, CHANNEL_UP / DOWN,
   MEDIA_NEXT / PREVIOUS.
3. Video player: no `A` for audio track (`VIEWER-KEYBOARD`).
4. Video playback has no MediaSession: headset and system media keys do nothing for video (needs device to confirm).
5. The player's error dialog is not cancellable; Esc and Back do nothing, only a button leaves
   (`FullscreenPlayerActivity.kt:446-464`).
6. Esc and Alt+Left on activity screens other than dialogs: no code (needs device for the system fallback).
7. Catalog (about 19 thousand rows): PageUp / PageDown / Home / End not handled by the product (needs device for
   the RecyclerView default).
8. Gamepad stick navigation and key-repeat acceleration exist for the catalog only.
9. Broadcast Control dim overlay does not take focus, so Esc may not reach it.
10. Widget configuration search field: no Ctrl+F / Start.
11. Focus visibility (rule 2) is unmeasured; only two drawables define their own ring (needs device).
12. Wear: the rotary volume actions (`rotaryActionSteps`) are declared and never called; the video player has no
    volume control at all. (The contract has no rotary column: see recommendation R4.)
13. `F1` on the Exchange screens and the diagnosis dialog: recorded debt in `tools/help/help-map-baseline.txt`
    (S0027 / S0031), unchanged.

Outcome: `partial 0.4` on the applicable surfaces, **temporary debt until 2026-12-31**, remediation ticket **S0052**
(filed today). Physical gamepad, TV-remote, TalkBack, Bluetooth-headset, mouse (ChromeOS / DeX) and paired-watch
checks stay open in S0004 / S0007's own handoff and are not implied by any JVM test.

## 6. `LIVE-BROADCAST` 1.6 and `DEVICE-EXCHANGE` 1.10 - what moved since the 1.3 / 1.7 read

The amendments after 2026-10-23 are 1.4 (item S, pending descriptor), 1.5 and 1.9 (items T and DX, a deliberate
end), 1.6 and 1.10 (items U and DY, an ended broadcast at a tunnel), plus 1.8 (item DW, preparation before the
start). Read in full: `LIVE-BROADCAST` amendments 18-20, `DEVICE-EXCHANGE` amendments 31-33 and the note and
proposal of 2026-10-25. None of the six is implemented in this tree; the product behaves as a program that
recorded 1.3 / 1.7, which the contract says does less, not wrong - **except one MUST on the producer** (row 4).
A code-reading pass over `phone`, `wear` and `exchange-core` (claims spot-checked) gives:

| Item | Binds | Requirement | Code | Ticket |
| --- | --- | --- | --- | --- |
| S (1.4) | receiver | know `pending {until}`; show waiting, not failure; probe the relay status at most every 30 s while the screen is visible; stop at `until` | missing: no `pending` member in `BroadcastDescriptorDto.kt`; the stored copy is rebuilt from the DTO and drops it (`BroadcastDescriptorStore.kt:22`, `WearBroadcastDescriptorStore.kt:26`); a pending link ends in the "not responding, delete?" dialog (`FullscreenPlayerActivity.kt:446-464`) | none (S0044 only logs `pending`) -> S0053 |
| S + DW (1.8) | producer, MAY | fix id and link keys before the start, hand out the pending descriptor | not built; id and keys are made at the start (`BroadcastExchangePublisher.kt:176-179`) | owner decision, recommendation R6 |
| DW (1.8) | any broadcaster | `id-collision` refuses the broadcast, not the device | stream kept, `NotPublished(REFUSED)` (`BroadcastExchangePublisher.kt:380-386`); no fresh id | S0021 implicitly |
| T / DX (1.5, 1.9) | producer, MUST | write `broadcast-end` before any stream of the broadcast closes; the stop never waits for a missing control stream | **reversed**: the LAN server stops first (`BroadcastCaptureService.kt:357`), then `exchangePublisher.end()` (:359), which closes upload and tunnels (`BroadcastExchangePublisher.kt:260-262`) before `broadcast-end` is posted (:274-278). Same order on video (`VideoBroadcastService.kt:567-568`) and on the watch (`VoiceRecordingService.kt:212-214`, `WearExchangeRuntime.kt:78-79`) | none -> S0053 |
| T / DX | producer, MUST | signing out of the announcing account is a deliberate end | phone: not built (`ExchangeAccountManager.removeAccountEntry` never touches the publisher); watch calls `endAndAwait` (`WearExchangeAccountRepository.kt:97`) | none -> S0053 |
| T | producer, SHOULD | RTCP BYE on every RTP stream; closed port or `404` after the end | RTSP server is a library (RootEncoder), not read; LAN audio port closes (`BroadcastHttpServer.kt:96-103`) | needs reading and a device |
| T / DX | receiver, SHOULD | read directory removal, BYE or clean end then refused / `404` / `410` reconnect, relay `410` as ended; show the ended slot with an explicit reconnect | not built; HTTP status of broadcasts is not classified (`StreamPlaybackHelper.kt:348-356`); every failure falls to the next endpoint and ends in the delete dialog | S0043 (written against the proposal of 2026-10-25, not the folded 1.5 / 1.9 / 1.6 / 1.10 text) |
| U (1.6) | receiver | a directory removal ends nothing that still delivers and ends a bounded reconnect already running | violated on the cast route: `changed.removals` stops the cast player at once (`ExchangeReceiverRuntime.kt:150-153` -> `CastReceiverHandler.kt:119-125`) | S0043; its line "a removal with the device going offline reads as a lost connection" contradicts item U, which accepted the opposite as a recorded limit |
| U / DY (1.6, 1.10) | receiver | `connect` refused `ended` is a deliberate end; `unavailable` is not | no `ended` reason constant (`DeviceExchangeEnvelope.kt:85-96`); unknown reasons map to a generic refusal (`ExchangeMessage.kt:88-94`); the forwarder answers the player `503` (`ExchangeTunnelProxy.kt:116-122`); no EN / RU / UK text for it in the vendored item CZ table | behaviour S0043; text and table vendoring -> S0053 |
| R (1.3) + DV (1.7) | producer | outward-link password choice; `verifiers` on every announcement | not built (`BroadcastExchangePublisher.kt:366-370,424-435`) | S0028, S0021 (unchanged; release blocker for the producer) |
| DS-DU (1.6) | producer | outgoing entry assigned, every broadcast announced on it, panel names the server | not built; the publish switch is off by default (`BroadcastSettings.kt:57-58`) | S0021 (waits for an owner decision) |

Also found: `markBroadcastEnded` (`StreamSourceRepository.kt:456`) has no production caller; a sealed `RELAY`
without a pinned certificate falls through to the plain https URL (`BroadcastEndpointResolver.kt:97` against :148),
which rule 8 of the receiver forbids - for S0032 to read.

**Outcome.** Pointers move to `LIVE-BROADCAST` 1.6 and `DEVICE-EXCHANGE` 1.10 (read 2026-10-27). Row cells:
`Implements` `partial 1.3` / `partial 1.7` (what S0013, S0020, S0026 and S0034 proved, unchanged), `Reads` 1.6 /
1.10; the rows are re-spelled in the readable shape S0041 asks for. Dated exceptions (temporary debt, until
2026-12-31, responsible: this product; review: when the carrying ticket proves the behaviour by a test and, for the
wire, a joint run): items S, T / DX (producer order and sign-out) and U / DY (receiver), ticket **S0053**; the
existing rows for R, DV and DS-DU stay with S0028 / S0021. The producer publish switch is off by default, so the
MUST breach is bounded to people who opted in to exchange publishing; impact: an exchange listener may read a
deliberate stop as a lost connection; compensation: the directory removal still ends the slot, LAN reception is
unaffected. No two-network handshake, physical device or server transcript was run: S0034 keeps the joint runs.

## 7. `REPO-STAMP`, `RULE-DELIVERY`, `HARNESS-PROFILE`, canon checks

- **Reader exceptions (REPO-STAMP rule 4, RULE-DELIVERY rule 5): the closing condition is met.** They waited for "the
  canon publishes the accepted behavior and this product reruns the probes". Isolated probes rerun on plugin
  2026.1026.3 (canon 2026.10.26.3), files in `attachments/reader-probes-20261027/`: missing dates + differing digest
  gives `FAIL SZA-CANON03 ... unknown age, treated as past 180 days`; invalid dates + differing digest the same
  FAIL; equal digest (the current canon digest) + invalid dates draws no `SZA-CANON03` finding. The rung the
  contracts require is delivered. The other FAILs in each probe come from the bare isolation directory (no LICENSE,
  README, CHANGELOG, agent file), not from the stamp.
- **Real repository** (`check-compliance.ps1`, 2026.1026.3, `real-repo-compliance.txt`): exit 1, one error
  `SZA-VER03` (declared channel `play` has no committed manifest folder: S0047's, untouched here) and five warnings
  (`SZA-CANON03` digest re-sync lag, `SZA-CTR02` archived DONE/S0003, three `SZA-SURF02` og:image). The digest
  re-sync and `.sza-canon.json` stay with the adopt-canon run.
- **`HARNESS-PROFILE` / `REPO-STAMP` 0.13** and the unreadable rows `STREAM-BANK`, `RULE-DELIVERY`: S0041.

## 8. `DOC-INTERNAL-QUALITY` 0.10 and `DOC-EXTERNAL-QUALITY` 0.12

No row of this product exists in the catalog for either (the `StreamsPlayer` rows are the Windows product's; the
row drafted in S0030 was never applied), and `docs/contracts/` has no pointer for `DOC-INTERNAL-QUALITY` although
`DOC-EXTERNAL-QUALITY` section 4.1 requires the internal rules 1 and 3. Read-only pass, results:

- **Internal, rules 1-7.** Rules 1 (registry) and 2 (reverse coverage): gap - no `DOCUMENT_REGISTRY.jsonl`
  anywhere, `.sza-canon.json` `docRegistryShape` / `docRegistryFile` null, `docs/README.md` is a hand index missing
  three documents. Rule 3 (links, anchors): partial - help pages and the site have gates
  (`tools/help/assert-help-map.ps1`, `tools/site/build-site.ps1 -Check`), `.md` files none (a one-off read of
  relative links found no broken target). Rule 4 (generated documents in step): partial - the help generator has
  no `-Check` mode, and the site and permission checks are not in the pre-release sweep or CI. Rule 5 (house
  style): holds by reading, no gate. Rules 6-7: held for HTML by the site and help gates (`assert-help-map`
  rerun, exit 0; the site gate not rerun).
- **External, rules 1-7 and sections 8-9.** Contributor role in the shared portal (`PORTAL-DOCS` pointer): the
  three sections are delivered in three languages at once; applying them is the publisher's. Three hosted privacy
  pages: SEO block measured (titles 30 / 43 / 41, descriptions 136 / 157 / 152 code points, canonical, hreflang,
  sitemap). Gaps: no freshness mark for translations, no term base for prose, no glossary or subject index, help
  organised by screen not by task, screenshots not diffed on regeneration (S0035 rung 2).
- **Outcome.** `DOC-INTERNAL-QUALITY`: read at 0.10, not implemented, temporary debt. `DOC-EXTERNAL-QUALITY`:
  `partial 0.12` for the declared scope (portal contributor, offline help, hosted privacy pages). Both until
  2026-12-31, ticket **S0051**. The offline help pages stay governed by `CONTEXT-HELP`; publishing them on a site is
  S0045's decision D2.
- **Found while reading, not settled here:** the working-tree `.gitignore` now ignores `PLAN/` (`.gitignore:50`)
  while `CLAUDE.md` section 4 item 6 records "`PLAN/` is tracked" as an owner decision; 14 PLAN files are in the
  index and the other tickets are untracked. Owner question Q1.

## 9. Compatibility law (CONTRACTS section 5) - the seven answers

Boundaries: the stream-bank CSV and ZIP, Extended M3U8, the connection string, the broadcast descriptor, the
exchange envelopes and records. S0013 / S0026 answered the first three with tests; today's phone suites (1158 /
1160, 0 failures) rerun them; the descriptor and envelope readers were read afresh.

1. **By name, never by position: yes.** `StreamCatalogCsvParser.kt:18-25`; `BroadcastDescriptorDto.kt:9-26`,
   `BroadcastEndpointDto.kt:9-25`; `DeviceExchangeEnvelope.kt:22-31`; `DeviceExchangeRecordCodec.kt:282-300`.
2. **Higher MINOR accepted, unknown members skipped: mostly.** Unknown members skipped
   (`BroadcastDescriptorParser.kt:91`, `DeviceExchangeEnvelope.kt:142-146`); an unknown `transport` or `seal.v` is
   skipped (`BroadcastEndpointResolver.kt:60-65,96`). No: the structural pre-validation
   (`BroadcastDescriptorParser.kt:80-89`) rejects the whole descriptor when one endpoint lacks a string `url`,
   `transport` or `mode`, so a future endpoint of another shape would sink the descriptor. -> S0053.
3. **Higher MAJOR refused cleanly: partly.** No partial import (`ImportStreamBroadcastUseCase.kt:23-28`,
   `CastReceiverHandler.kt:38-48`). No: the version check (`BroadcastDescriptorParser.kt:98`) runs after the shape
   checks, so a foreign-shaped v2 reads `Malformed`, not unsupported; the message is
   `import_broadcast_unsupported_version` and not the `format-unsupported` text the `LIVE-BROADCAST` amendment of
   2026-10-17 names; the same toast shows for `Malformed`; a different envelope `schemaVersion` is a silent
   `DeviceExchangeWireException` (`DeviceExchangeEnvelope.kt:134-135`); the watch shows a generic "unavailable"
   (`WearBroadcastPlaybackResolver.kt:29`). -> S0053.
4. **Unknown enum degrades to the documented default: mostly.** `presence` -> offline
   (`DeviceExchangeRecordCodec.kt:310-311`); unknown `refused.reason` / cast reason -> generic refusal
   (`ExchangeMessage.kt:88-98`); unknown `auth.scheme` -> explicit refusal (`BroadcastPlaybackResolver.kt:33`). An
   unknown `mode` is filtered and the person sees the generic "not available"
   (`FullscreenPlayerActivity.kt:292,342-348`).
5. **User-authored data preserved: yes, one question.** Merge by exchange id / `sourceId`, only generated titles
   rewritten, rows never deleted (`ImportStreamBroadcastUseCase.kt:56-112`, `StreamSourceRepository.kt:452-472`). A
   re-import without `auth` removes the stored channel password (`ImportStreamBroadcastUseCase.kt:79`): intended for
   item R "none"; to be confirmed in S0028.
6. **Indices bounds-checked: yes.** `BroadcastEndpointResolver.kt:42,79,163-166`; `DeviceExchangeFrameCodec.kt:46`;
   `DeviceExchangeDirectoryClient.kt:117-137`; past the end `resolveNext` returns null.
7. **Defaults written in the contract: partly.** HTTP 1000 ms, RELAY / TUNNEL 2000 ms match amendment 13; the RTSP
   200 ms and HTTP-audio defaults were not found in the text; `keyframeIntervalMs` (absent = 2000) is never read; the
   `transport="HTTP"` / `mode="AUDIO_ONLY"` fall-backs in `BroadcastEndpointDto.kt:10-11` are local decisions.
   -> S0053 and recommendation R7.

## 10. Fresh evidence (2026-10-27; other sessions were editing the tree)

| Command | Exit | Observed |
| --- | --- | --- |
| `gradlew.bat :build-logic:convention:test :exchange-core:test testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest --console=plain --continue` under the `Build.Main` lock | 0 | BUILD SUCCESSFUL 4m 15s; JUnit XML: phone store 1158 tests, phone noLegal 1160, wear store 274, 0 failures / errors / skipped. `:exchange-core:test` (67) and `:build-logic:convention:test` (24) were UP-TO-DATE: inputs unchanged, results of 2026-10-25 / 2026-10-19. Log `attachments/gradle-closing-tests-20261027.log` |
| `check-sync.ps1 -CatalogRoot <catalog>` before any pointer change | 1 | 8 findings (section 12): `attachments/check-sync-20261027-before.log` |
| `tools/help/assert-help-map.ps1` | 0 | PASS (30 surfaces, 20 rows, 10 baseline, 27 pages) |
| `tools/audit/run-selftests.ps1` | 0 | PASS (3 steps; coverage 24 cases, rig 87 fixture tests; live checks not run) |
| `tools/port-ledger/check-drift.ps1` | 1 | 129 of 782 ledger rows changed in the source since port (122 of 721 on 2026-10-23); carries are release-time work; no source-parity claim |
| `harness spec_catalog/validate` | 2 | 1 FAIL: the journal holds S0031 at `PLAN/S0031_...md`, the file sits in `PLAN/DONE/` (moved before this run; the triage's; not touched) |
| `harness spec_catalog/release-queue -Validate` | 0 | 48 tickets, no drift (run before the tickets filed today) |
| isolated reader probes, plugin 2026.1026.3 | - | section 7; `attachments/reader-probes-20261027/` |

Not run: any device, emulator, TalkBack, gamepad, TV-remote or paired-watch check; any live two-network handshake;
any rendered capture or page-switch timing; `tools/site/build-site.ps1 -Check`; `tools/privacy/permissions.ps1`.

## 11. Exceptions, closures and tickets filed (the full text is the registry proposal)

| Contract / clause | Kind | Until | Remediation / owner |
| --- | --- | --- | --- |
| Unified UI: unmigrated surfaces, unused components, no measured proof | temporary migration debt | 2026-12-21 | S0049 (new); permanent variants V1, V2 of S0036 unchanged |
| `INPUT-PARITY` 0.4: thirteen gaps | temporary debt | 2026-12-31 | S0052 (new) |
| `LIVE-BROADCAST` items S, T, U; `DEVICE-EXCHANGE` items DW, DX, DY | temporary debt | 2026-12-31 | S0053 (new), S0043 |
| Descriptor and envelope compatibility-law answers 2, 3, 7 | temporary debt | 2026-12-31 | S0053 |
| `DOC-INTERNAL-QUALITY` 0.10, `DOC-EXTERNAL-QUALITY` 0.12 gaps | temporary debt | 2026-12-31 | S0051 (new) |
| `STREAM-BANK` rules 9/12 artwork | existing exception, carrier now named | 2026-12-31 | S0050 (new) |
| `REPO-STAMP` rule 4, `RULE-DELIVERY` rule 5 reader | closed 2026-10-27 | - | probes on plugin 2026.1026.3 |
| `LIVE-BROADCAST` item R, `DEVICE-EXCHANGE` items DV, DS-DU | existing rows, unchanged | 2026-12-31 | S0028, S0032, S0021 |
| `MEDIA-CLASSIFICATION` | declared not applicable | - | none |

No permanent supported variant is added by this run. Independent legal, store, security and data-safety release
blockers still apply and are not touched by any of the above (the producer's item R remains a release blocker).

## 12. Pointers, the check, and what a re-run shows

Pointers updated to the versions read today: `DEVICE-EXCHANGE.md` 1.10, `LIVE-BROADCAST.md` 1.6, `PORTAL-DOCS.md`
(adds `DOC-INTERNAL-QUALITY` 0.10), `INPUT-PARITY.md`, `STREAM-BANK.md` (carrier S0050); new `UNIFIED-UI.md` for
the five UI contracts; `README.md` of `docs/contracts/`. `tools/contracts/check-sync.ps1` binds the new pointer ids
(required-row list unchanged: the seven new rows join it when the registry proposal is applied).

| Run | Findings |
| --- | --- |
| before (catalog as it stands, old pointers): `attachments/check-sync-20261027-before.log` | 8: two pointers behind, `partial 1.3` / `partial 1.7` cells unreadable, `REPO-STAMP` / `HARNESS-PROFILE` 0.12, item CZ table and its provenance hash |
| after, against a scratch catalog holding the proposed registry: `attachments/check-sync-20261027-with-proposal.log` | 4: `REPO-STAMP` / `HARNESS-PROFILE` 0.12 against 0.13 (S0041); item CZ table differs and its provenance hash (three rows S0032 added to the vendored copy; S0053) |
| against the live catalog, after the pointer changes, before the proposal is applied: `attachments/check-sync-20261027-live-after-pointers.log` | 6: all 31 pointer versions match; four stale cells (`LIVE-BROADCAST`, `DEVICE-EXCHANGE`, which the proposal rewrites, and `REPO-STAMP`, `HARNESS-PROFILE`, S0041's); the item CZ pair |

The catalog itself was not edited. The scratch catalog is junctions to the real domain folders plus a copy of
`_meta` holding the proposed registry.

## 13. Questions for the owner

Q1. `.gitignore` in the working tree now ignores `PLAN/`, while `CLAUDE.md` section 4 item 6 says "`PLAN/` is
tracked" (an owner decision). Which one stands? It decides S0051's internal registry and whether the tickets, the
audit evidence and the registry patch are versioned.
Q2. Should this product prepare a broadcast before its start (item DW, a MAY)? It needs the pending-link user
experience; nothing is built or scheduled for it.
Q3. S0036 closed as Verified while the settings rail, the About view and the groups reach no screen and no
capture or timing exists. Keep it closed with S0049 as the successor (this run's choice), or reopen it?
Q4. The three conflicting registry proposals of this product (S0026, S0034, this one) wait for the canon session;
nothing here applies them.

## 14. Not verified, and what it would take

- Any behaviour on a device: focus rings, TalkBack, gamepad, TV remote, Bluetooth headset, mouse, paired watch and
  its rotary crown (a device pass for S0004 / S0007, and S0052's walks).
- The RTSP server's RTCP BYE and closed port after a stop (RootEncoder source and a device).
- Any live two-network handshake, any server transcript beyond S0034's replay (the owner's stand: S0034).
- Rendered captures, contrast, page-switch timings for the UI contracts (S0049).
- `tools/site/build-site.ps1 -Check`, `tools/privacy/permissions.ps1` (not rerun); `exchange-core` and
  `build-logic` tests were reported UP-TO-DATE, not rerun.
- Source parity with FastMediaSorter: the drift check lists 129 changed source files; carrying them is release-time work.

## 15. Last Audit (summary; the ticket carries the same text)

Date 2026-10-27, mode contract synchronization. Proven by commands in section 10: the tree builds and its unit
suites pass; the help gate, audit self-tests and ticket validators run as listed; the reader exceptions' closing
condition is met. Read-only audits: the INPUT-PARITY applicability sweep, the documentation-quality applicability, the
LIVE-BROADCAST / DEVICE-EXCHANGE deltas. Exceptions and recommendations are recorded and handed off (sections 11 and
`proposals.md`). Remaining unverified work is listed in section 14 and carried by S0049, S0050, S0051, S0052, S0053,
S0041, S0043 and the S0034 stand.
