# S0038 - Own streams: saved channels and live broadcasts from the account's devices

**Ticket:** S0038
**Status:** BlockNeedUserTest
**Status note:** Development complete on phone and watch (JVM suites green in both variants, R8 packages build). Only testing remains: two-device walk against the exchange server on phone and enrolled watch, and the help screenshots.
**Date:** 2026-10-19
**Scope:** Specification only; implementation is a later task.

## Remaining development

*As of 2026-10-27, checked against the tree. Built on the phone: the one shared observation of the account directory
(`AccountBroadcastDirectory` in `exchange-core`, taken by a lease while a screen is visible, used by both the Own
category and the exchange screen), the saved/live projection (`OwnStreamsProjector`), the Own category with its two
labelled groups, device headers, Saved indicator, explicit Save, Refresh, status banner (loading, stale with the time of
the last update, failure with Retry, no server with Set up, sign-in required with Sign in), selection that revalidates
the exact account and broadcast before playing, and strings and help in EN, RU and UK.*

*As of 2026-10-28 the watch is built too (section 3.4): the Own filter of the watch's streams list shows the live
broadcasts of the account's other devices, grouped by device, above the personal channels, through the same
`AccountBroadcastDirectory` (no second subscription), held only while Own is in front. Status line (no server, sign in
again, loading, unreachable with Retry, last known list with its time), Refresh that asks the directory and never the
catalog, Save, the Saved indicator, audio-only playability with a reason on every other row, rotary and touch through
the shared list column, and EN/RU/UK strings and the watch help page. Development is complete; what is left is testing.*

1. **Wear OS (section 3.4).** Built 2026-10-28 (see the audit below). It also fills the watch list of S0028 item 2;
   the password step on the watch stays S0028's.
2. **Observation (section 7).** Nothing here has been seen on a device against the exchange server: opening Own with two
   broadcasting devices, start and end of a producer, an address change, drop and reconnect, sign-out, audio and video
   playback from a live row, TalkBack and D-pad walk, themes and long text. Builds and JVM tests do not prove it. The
   same holds for the watch: Own on an enrolled watch with two broadcasting devices, a live audio row played and
   saved, a video and a password row explaining themselves, rotary scroll through the live group, sign-out.
3. **Help pictures.** The two new elements of the catalog help page (`own-status`, `own-refresh`) have no marker on the
   screenshots until `tools/help/capture-help-screenshots.ps1` is run on an emulator.
4. **Saved address follow-up.** A saved channel moves to the source's current address when it is chosen or saved; the
   directory view itself never writes. If the owner wants the saved address refreshed in the background on every
   snapshot, that is a decision to take.

## 0. Captured request

The owner uses Own streams for manually added streams and for broadcasts from their devices when an
exchange server is connected. The person should immediately see those broadcasts, be able to refresh
the list, and choose one to watch or listen to without copying a link for each session.

## 1. Problem and outcome

Own streams currently means locally saved manual channels. Account broadcasts are exposed separately
through the exchange screen. The person must understand that distinction and may need to save a
broadcast before finding it among their channels. A saved session URL can also become obsolete.

Own streams becomes the normal place to find both saved personal channels and current broadcasts from
the other devices of the connected account. Opening this section loads the current directory
automatically; Refresh explicitly requests a fresh list. Selecting a broadcast starts its audio or
video through the existing playback route. Discovery and refresh never start playback or capture.

"Own" describes the personal section, not proof of who owns a manually entered URL. This feature
discovers published live broadcasts; it does not synchronize the user's entire manually authored
channel library between devices.

## 2. Current evidence

Source inspection on 2026-10-19, not a runtime verdict:

- The phone's Own filter matches only `sourceOrigin == MANUAL`. Broadcast descriptor imports can
  produce either `MANUAL` or `IMPORTED` records depending on their transport, so origin alone does
  not reliably express membership in this section.
- The exchange screen already groups directory broadcasts by device, offers playback and saving, and
  carries account-entry and broadcast bindings. Its playback action currently goes through saving.
- Directory loading sends `list` and `subscribe` inside a session which is then closed. Sending a
  subscription alone therefore does not prove that the visible list follows subsequent changes.
- Stored broadcast imports can refresh an address by `sourceId` and preserve a user-authored title.
  Different sources on one device must remain distinct under the current contract.
- Wear OS already has an Own filter for channels transferred from the phone. Independent account
  discovery on the watch is planned in S0022 and S0028.

## 3. User experience

### 3.1 Placement and contents

Keep the existing Own streams category on the streams screen. It contains two visibly labelled
groups: Live from my devices, grouped by friendly device name, followed by Saved streams. A saved
channel that matches a current directory broadcast appears once, in the live group, with a Saved
indicator. When the broadcast ends, its saved channel remains in Saved streams.

Saved streams includes existing manual entries, saved broadcast descriptors and personal channels
transferred to the watch. Ordinary curated catalog channels and playlist imports do not become Own
solely because their database origin is `IMPORTED`. Membership follows provenance and explicit saving
as a personal broadcast; it must not be inferred from transport or from a URL's resemblance to another.

Each live row shows its title, source device, audio/video kind, presence or availability, and whether
it is saved. Use a localized fallback for a missing title or device name. Unknown presence is unknown,
never assumed online. Unsupported broadcasts remain visible with an explanation and cannot be played
on that receiver. Resource shares are not broadcast rows. The receiving device's own broadcasts are
excluded, as required by DEVICE-EXCHANGE section 9 item CI.

Current search and applicable filters include both groups. Filters for catalog-only metadata must
not silently hide live rows: entering Own clears those incompatible facets and collection selection,
while retaining search text and applicable personal filters. A filtered empty result has Clear filters;
it must not say that nobody is broadcasting. Preserve pin order for saved channels and use stable
device/title ordering for live rows; background updates must not move keyboard or rotary focus to a
different broadcast.

### 3.2 Opening, updating and returning

- Opening Own with an authenticated exchange entry immediately displays saved channels and requests
  `list`, then maintains `subscribe` / `changed` while that section is visible. No Refresh tap or
  visit to exchange settings is required. Render results as they arrive, without intentional delay.
- Refresh is always available in Own. With an entry, it reloads the account directory without
  downloading the public catalog. Without an entry, it reloads local saved channels and explains that
  device discovery requires connecting an exchange server, with an action to its existing setup.
- Refresh retains existing rows while loading and shows progress. Coalesce repeated taps into one
  request. A failed refresh preserves saved channels and the last successful live snapshot, marks
  the snapshot stale, and offers Retry. Show when the last successful directory update occurred.
- Returning from playback or reopening the section revalidates the directory. Reconnect and revision
  gaps trigger a full resynchronization under the contract. Ignore duplicate/older revisions and
  results from a previous account/session; a delayed response must not resurrect an ended broadcast.
- Leaving the section releases its directory observation. Reuse any connection legitimately held
  for playback, publishing or another visible consumer; closing Own must not interrupt those uses.
  No new always-running background polling service is introduced.

The UI distinguishes initial loading, a successfully loaded empty directory, no exchange entry,
connection failure/stale data, authentication required and no filter matches. Empty live discovery
does not hide usable saved streams. A successful empty snapshot removes stale live rows, not saves.

### 3.3 Choosing and saving

Tap a playable row to watch or listen. The phone plays one selected broadcast at a time; switching
uses the existing player behavior. Two or more broadcasts, including distinct sources from the
same device, remain independently selectable. Nothing automatically chooses the first broadcaster,
switches to another broadcaster after failure, or follows a changed list position.

Live playback does not require saving a permanent channel. Keep an explicit Save action and a Saved
indicator; saving again is idempotent. The exchange screen and Own use the same discovery, save and
playback behavior. Existing link, QR and file imports remain available without an exchange server.

On selection, resolve the current descriptor for that exact account and broadcast. If it ended
before selection completes, report Broadcast ended and refresh; do not play a different row. A stale
directory row must be revalidated before being treated as currently live. A manually saved URL can
still be attempted independently and is not declared ended merely because no directory record matches.

Failure or end of the selected stream uses the existing player status and retry behavior. Directory
refresh neither restarts healthy playback nor starts another stream. An explicitly saved channel
can remain unavailable until its source returns or the user edits/removes it.

### 3.4 Wear OS

The same Own concept applies on the watch once its account discovery is supplied by S0022/S0028.
Show saved/transferred personal channels immediately and merge current account broadcasts on open;
offer Refresh with touch, rotary navigation and accessible labels. Only supported modes are playable
(audio today); unsupported modes explain their limitation. Discovery uses the watch's authorized
entry, not the phone's device token. Closing the list stops its observation, not ongoing listening.
The watch face does not gain a new discovery surface in this ticket.

## 4. Identity, retention and account boundaries

1. Live selections use account-entry identity plus `broadcastId`, never device name, device id alone,
   current URL or list position. Join device labels by `deviceId`. A device can publish several sources.
2. Saved-source replacement follows LIVE-BROADCAST section 3.1 and amendment G: `sourceId` is opaque
   and can identify a particular source of a device. When applicable, use the later `updatedAt`.
   Honor DEVICE-EXCHANGE section 9's cross-entry binding and replacement rules. Equal labels or URLs
   alone never authorize a merge of independently identified live broadcasts.
3. Merging a saved channel with its live record is a presentation operation. Refresh may update the
   remote descriptor/address but preserves the local id, user title, pins/order, usage/history,
   preferences and bindings. A conflict is handled explicitly without overwriting another saved row.
4. Missing/expired/ended broadcasts remove transient live rows only. Server failure, sign-out or
   removal of an exchange entry never deletes manually authored or explicitly saved channels.
5. On sign-out or account/server change, immediately clear that account's transient directory data
   and observation. Keep saved channels with their documented unavailable/unbound state; never
   silently rebind them to the next account. Account-specific live metadata cannot leak into its view.
6. Guests see only server-authorized grants. Preserve protected descriptors, secret-store handling,
   sealed content, certificate checks and the existing endpoint order. No raw credentials, tokens,
   descriptor URLs or decrypted payloads enter logs or diagnostic exports.

## 5. Contract and ticket ownership

This touches shared discovery, identity and playback behavior, but proposes no new wire format.
The specification follows DEVICE-EXCHANGE 1.5 sections 6.3, 7.4, 7.7 and 9 item CI, and its
2026-10-19 recommendations section D; LIVE-BROADCAST 1.2 sections 3.1 and 5 and amendments G and
2026-10-19; STREAM-BANK documents 05, 06 and 07 for the existing screen and playback routes.
Use existing `list`, `directory`, `subscribe` and `changed`; do not invent `broadcast.list` or new
descriptor members. Single-stream reception remains supported. Local combined placement needs no
contract amendment. Any discovered incompatible shared behavior requires a catalog proposal/versioned
amendment before boundary code, or an explicit justified exception under catalog RULES section 15.

| Ticket | Responsibility relative to S0038 |
| --- | --- |
| S0028 | Owns account-directory lifecycle, guest visibility and protected-link behavior on phone/watch. S0038 consumes it and owns combined placement, refresh semantics and saved/live projection. Implement shared lifecycle once, with coverage referenced by both tickets. |
| S0022 | Owns independent watch enrollment and directory capability; prerequisite for watch acceptance here. |
| S0020 | Supplies descriptor playback and endpoint resolution; adapt reuse for an unsaved live selection, without a second player implementation. |
| S0021 | Publishes device broadcasts; this ticket discovers already published broadcasts and never enables capture or publishing. |
| S0027, S0031, S0032 | Own account lifecycle, connection diagnosis and encryption respectively; preserve their boundaries rather than redesign them here. |
| S0029 | Adds Drive announcements later; no Drive discovery is required here. |
| S0030, S0035 | Reuse terminology and update applicable help when implementation lands. |
| S0037 | Coordinates contract migration; reading current versions here is not a conformance claim. |

No counterpart repository or shared contract is edited by this specification. During implementation,
carry relevant source behavior from FastMediaSorter under the project's port ledger and drift rules.

## 6. Acceptance criteria

- [ ] With two other devices broadcasting under the connected account, opening Own shows both without
      opening exchange settings or pressing Refresh. Saved manual channels are visible during loading.
- [ ] Start/end a broadcast with Own open: the live row appears/disappears through change notifications.
      An explicitly saved channel survives the end. Two sources on one device remain distinct.
- [ ] Refresh obtains a fresh directory, reports progress and last successful update, coalesces taps,
      preserves playback and local data, and never invokes public-catalog refresh in this category.
- [ ] Open without a server, with an empty directory, while offline, with expired authentication and
      with filters hiding all rows: each case has the distinct behavior specified above.
- [ ] Drop/reconnect the control stream and introduce a revision gap; the resynchronized list is correct.
      Delayed responses, old revisions and account switching cannot revive or leak obsolete rows.
- [ ] Play an unsaved audio broadcast and an unsaved video broadcast, then explicitly save one. No
      permanent channel is required for playback; repeat save creates no duplicate. Reopening Own
      represents its matching saved/live source once and preserves user metadata after an address change.
- [ ] End a broadcast between rendering and selection; report the end without starting a sibling.
      Unsupported modes and unknown presence do not become playable/online through fallback guesses.
- [ ] Sign out/remove/change the account with saved entries present: no user-authored channel is lost,
      no prior live snapshot is displayed as the new account, and saved channels are not rebound.
- [ ] Guest restrictions, protected/sealed descriptors and endpoint trust rules hold in Own exactly as
      in the existing exchange flow. The current device's broadcast is excluded. No LAN discovery,
      unsolicited publishing/capture or automatic playback occurs.
- [ ] Phone and enrolled watch follow the same personal-section model within their playback capabilities;
      touch, TalkBack, D-pad on phone and rotary input on watch can reach refresh, selection and saving.
- [ ] Implementation lands strings/help in EN, RU and UK together, including stale/empty/error states,
      and is observed in supported themes, long text and phone/watch layouts.

## 7. Verification and implementation handoff

First reconcile S0028/S0020/S0022 ownership and the current source implementation. Then implement the
shared lifecycle and identity projection, wire Own on phone, and integrate the enrolled watch. Do not
build a second directory subscription or duplicate the playback resolver for this category.

Use focused JVM tests with a controllable directory/session for initial load, deltas, refresh, revision
gaps, lifecycle cancellation, response races, account isolation and saved-data preservation. Run the
affected phone tests in both store and noLegal variants, and watch tests for its integration.

Observe the actual phone and watch with at least two broadcasting devices against the exchange server:
open Own, refresh, choose audio/video where supported, save, stop/restart a producer, change its address,
disconnect/reconnect and sign out. Record commands, exit codes and observed results in the ticket;
keep local evidence under its ignored evidence directory. Builds or fake-server tests alone do not
prove this user-visible workflow. Do not copy server credentials into the specification or evidence.

## 8. Owner decisions and open questions

The owner supplied the placement (Own), immediate discovery, explicit refresh and user selection.
The draft proposes two labelled groups and explicit saving as the presentation described above.
It requires no new server configuration or account model and no unanswered input to create this draft.
Implementation must reconcile existing ticket coverage before coding; this request does not implement
the feature or mark any existing dependency complete.

## 9. Last Audit

### Implementation pass, 2026-10-28 (watch)

What changed in the tree: `WearExchangePresence` gains `listing()` and `connection` - the Own list holds the control
stream without ever allowing a watch broadcast to relay (`wanted` is unchanged); `WearExchangeRuntime` follows
`connection` and loses its unused directory watcher, so the watch reads the account directory only through the shared
`AccountBroadcastDirectory`. `WearStreamChannel` gains nullable `broadcast_source_id`, `exchange_entry_id`,
`exchange_broadcast_id` (old channels.json files read as null); the phone hand-over now stores the source and broadcast
ids. New `ui/streams/own/`: `WearOwnStreamsProjector` (grouping, newest record per source, saved match by entry and
broadcast then by `sourceId`, audio-only playability with VIDEO / PROTECTED / FORMAT reasons), `WearOwnStreamsSource`
(account state, keep in place and idempotent, play an unsaved row through the stored descriptor and the ephemeral
player path), `WearOwnStreamsItems` (status line, notice, live group with device headings, Save chip, saved heading).
`StreamsViewModel` takes the lease while the screen is resumed on Own, clears topic, language and collection on entering
Own, routes Refresh in Own to the directory, revalidates a tapped row and never plays another one. Strings in
`strings_own_streams.xml` (EN, RU, UK) and the `channels` element of the watch help page in three languages.

Commands run (all under the `Build.Main` lock through `temp/S0038/gradle-locked.ps1`), expected and actual:

- `gradlew :wear:testStoreDebugUnitTest --tests 'com.sza.streamsplayer.wear.ui.streams.*' --tests
  'com.sza.streamsplayer.wear.data.exchange.*' --tests '..StoreTransferredStreamUseCaseTest'`: first run exit 1, one
  failure in the new `StreamsViewModelOwnTest` - the test, not the code: an empty catalog at start triggers the
  existing first download; the test now seeds a catalog row. Second run exit 0: `WearOwnStreamsProjectorTest` 11,
  `WearOwnStreamsSourceTest` 5, `StreamsViewModelOwnTest` 12, `WearExchangePresenceTest` 3,
  `StoreTransferredStreamUseCaseTest` 11, 0 failures.
- `gradlew :wear:testStoreDebugUnitTest :wear:testNoLegalDebugUnitTest :wear:assembleStoreRelease
  :wear:assembleNoLegalRelease --continue`: exit 0; expected all green and both R8 packages; actual store 304 tests and
  noLegal 304 tests (274 before), 0 failures, 0 errors, 0 skipped; `wear-store-release.apk` and
  `wear-noLegal-release.apk` written.
- After one more screen edit (a kept video channel opens the video player): `gradlew :wear:compileStoreDebugKotlin
  :wear:compileNoLegalDebugKotlin`: exit 0.
- `pwsh -NoProfile -File tools/help/generate-help-pages.ps1`: exit 0, `PASS (files=31 written=3 pages=9)`.
- `pwsh -NoProfile -File tools/help/assert-help-map.ps1`: exit 0, `PASS (30 surfaces, 27 rows, 3 baseline, 30 pages)`.

Not proven, stated plainly: nothing ran on a phone, a watch or an emulator against an exchange server. The phone and
the watch code are complete; the section 6 boxes stay unticked until they are observed.

Manual checks (pre-handoff):

- [ ] MANUAL phone: Own with two other devices broadcasting, start/end of a producer, an address change, drop and
      reconnect, sign-out, an unsaved audio and video row played, Save twice, TalkBack and D-pad, themes, long text.
- [ ] MANUAL watch: Own on an enrolled watch - live group appears without Refresh, Refresh, an audio row played and
      saved, a video and a password row explain themselves, rotary scroll, leaving Own drops the connection.
- [ ] MANUAL help: `tools/help/capture-help-screenshots.ps1` on an emulator for `own-status` and `own-refresh`.

### Implementation pass, 2026-10-27 (phone)

What changed in the tree: `exchange-core` gains `AccountBroadcastDirectory` (one observation of the account
directory, taken by a lease while a screen is visible; refresh coalesced; stale snapshot and failure kept apart; data
dropped when the account entry changes; this device's own records removed), `DirectorySource` /
`DirectoryUpdate`, a typed `observe()` on `DeviceExchangeDirectoryWatcher` that ignores a delayed older `directory`
answer (`watch()` for the watch is unchanged in meaning), and `DeviceExchangeControlStream.entry`. The phone gains
`OwnStreamsProjector`, `StreamListRow`, the Own rows and status banner, `StreamsViewModel` Own logic, the exchange
screen moved onto the shared observation, and `ImportStreamBroadcastUseCase` lookup rules (restarted source found by
`sourceId`; an equal address alone joins nothing). Strings in `strings_own_streams.xml` (EN, RU, UK) and the catalog
help page in the three languages.

Commands run (all under the `Build.Main` lock), with expected and actual:

- `gradlew :exchange-core:test :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest
  :wear:testStoreDebugUnitTest :wear:testNoLegalDebugUnitTest --continue`: exit 0; expected all green; actual
  exchange-core 83 tests, phone store 1197, phone noLegal 1199, wear store 274, wear noLegal 274, 0 failures, 0 errors,
  0 skipped. The new tests are `AccountBroadcastDirectoryTest` (16), `OwnStreamsProjectorTest` (16),
  `StreamsViewModelOwnTest` (18) and four cases added to `ImportStreamBroadcastUseCaseTest`.
- After that run, one small change (accessibility heading set in code instead of a layout attribute) and
  `StreamChannelAdapterOwnRowsTest` (5, Robolectric: rows, status banner and `activity_main` inflate with the app
  theme and tap callbacks reach the view model): `gradlew :phone:testStoreDebugUnitTest --tests
  'com.sza.streamsplayer.ui.streams.*'` and the same for `:phone:testNoLegalDebugUnitTest`: exit 0 and exit 0.
- `pwsh -NoProfile -File tools/help/generate-help-pages.ps1`: exit 0, `generate-help-pages: PASS`.
- `pwsh -NoProfile -File tools/help/assert-help-map.ps1`: exit 0, `assert-help-map: PASS (30 surfaces, 20 rows, 10
  baseline, 27 pages)`.

Not proven, stated plainly: nothing was run on a phone or an emulator against an exchange server. No two-device
walk, no producer start/stop, no address change, no drop and reconnect, no sign-out, no audio or video played from a
live row, no TalkBack, D-pad, theme or long-text observation, and no help screenshots for the two new elements. The
watch (section 3.4) is not built. The acceptance boxes in section 6 stay unticked for that reason.

Specification-time record (2026-10-19):

Specification created from source and contract inspection only. No product code, runtime behavior,
contract adoption row or implementation status was changed. Product builds and device checks are
deferred to implementation.

Registration checks on 2026-10-19:

- `pwsh -NoProfile -File tools/harness.ps1 spec_catalog/validate`: exit 0; expected a valid catalog,
  actual 38 records, 8 OK, 1 WARN, 0 FAIL. The warning names six older implementation records whose
  declared specification paths are missing; none is S0038.
- `pwsh -NoProfile -File tools/harness.ps1 spec_catalog/release-queue -Validate`: exit 0; expected a
  synchronized release projection, actual `release-queue: OK - 38 ticket(s), no drift`.
- `rg -n '^#|S0038' PLAN/S0038_own-streams-unified-device-broadcasts.md PLAN/spec-catalog.jsonl
  PLAN/RELEASE_QUEUE.md`: exit 0; expected the specification, Draft record and queue entry, all present.
