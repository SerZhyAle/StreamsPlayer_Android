# S0021 - Broadcasting through the user's exchange server: publish, relay, tunnel, cast

**Status:** BlockNeedUserTest
**Status note:** Development complete 2026-11-10 (DS-DU: outgoing entry, every broadcast announced from its start, server-and-state line; DV on the maxListeners update), not built: needs the release sweep, the help captures of broadcast and broadcast-settings, the device run and the rung 4/5 handshake reports against the owner's test server (S0034 part A, FMS_A S4213).
**Complexity:** hard - rewritten at the spec pass of 2026-11-10 and confirmed by the plan: items DS to DU touch both broadcast services, the settings store and its retired keys, three screens (Broadcast Control, the broadcast settings, the exchange server card), the help of three pages, the privacy page and the portal text in three languages; the protocol side is unchanged (1.6 is "no wire change").
**Phase:** device exchange, broadcaster side - independent of the build phases of handoff section 8
**Verification level:** unit tests on the JVM with a fake server; the handshake and end-to-end delay of `DEVICE-EXCHANGE` rungs 4 and 5 with the running server and a real listener, recorded in the `LIVE-BROADCAST` section 6 form.

## Remaining testing

*As of 2026-11-10 (unattended queue pass). Development is complete; what is left is the release build and suites, and runs that need a device, the owner's test server and a listener of another product.*

1. **Items DS, DT and DU (`DEVICE-EXCHANGE` 1.6, section 29)** - built 2026-11-10 (section "Outgoing entry landed 2026-11-10"), not built or tested until the release.
2. **Item DV (1.7, section 30)** - found in the tree on 2026-11-10: `broadcast-start` and every record `broadcast-update` already carried `verifiers` (S0028 phase C); the `maxListeners`-only update did not, and carries it since 2026-11-10.
3. **Optional endpoint member `keyframeIntervalMs`** - decided 2026-11-10, not written: absent means 2000 ms (`LIVE-BROADCAST`), which is the bound this producer holds (a keyframe is asked for after 1.8 s), so the member would say nothing a listener does not already assume.

**Queue pass 2026-10-29 (unattended).** Open items resolved by quiz on 2026-11-10 (see "Quiz decisions (2026-11-10)").

## 0. Source

- Owner, 2026-10-10 (this session): StreamsPlayer Android is a receiver of other devices' broadcasts and a
  broadcaster to other devices through a relay or tunnel exchange server; the server for its own broadcasts is
  **one**; connecting to other users' broadcasts, possibly through their servers, is any number and is part of
  the broadcast's description (the descriptor), not of an account.
- `DEVICE-EXCHANGE` 6.3 (broadcast record), 7.3 (control stream and presence), 7.4 (`broadcast-start`,
  `broadcast-update`, `broadcast-end`, `maxListeners`), 7.5 (tunnels: `open`, `attach`, bridging to the local
  service), 7.6 (upload, chunks, formats per mode, the 2 s rule), 7.7 (endpoints and their order,
  `certFingerprint`), 7.8 and 14.5 item R (cast), 14.5 items H, M and N, 15 items T, U and V.
- `LIVE-BROADCAST` 0.18: the descriptor, `sourceId` (item G), the HTTP MPEG-TS shape (item C), `RELAY` and
  `TUNNEL` endpoints (item D), the keyframe and first-picture proposals filed beside it.
- `PROPOSAL-2026-10-10-streamsplayer-android-receiver-entries.md`, ask E and the revision of 2026-10-10.
- Facts in this tree, 2026-10-10: the audio broadcast is ADTS AAC served by `BroadcastHttpServer`
  (`BroadcastCaptureService`, `BroadcastAacEncoder`); the video broadcast is served by `VideoBroadcastService`
  with an RTSP address (`RtspEndpointAddress`); there is **no MPEG-TS muxer** (no `mpegts` or `video/mp2t`
  symbol in `phone/src/main`); descriptors list LAN endpoints only; the watch serves AUDIO_ONLY on its own LAN
  server (S0007) with the cap of 4.
- Donor, read-only: FastMediaSorter Android `data/remote/sftp/anywhere/SftpServerTunnelManager.kt` (314 lines,
  the producer side of tunnels for a share) - a pattern to port from where it fits, not a copy of a share.

## 1. Goal

A broadcast started on this phone can be heard or seen from another network: the account's other devices see
it in their directory, and anyone who holds its descriptor reaches it through the exchange server the user
chose, in the order the descriptor lists, with the delay rules of `LIVE-BROADCAST` held. The user chooses
whether a broadcast is published (off by default) and through which one server.

## 2. Scope

1. **Which server.** The one account entry of S0018 is the one publishing server. Publishing is a per-broadcast
   choice, off by default and worded as what it does ("also reachable from other networks through your
   server"); with it off a broadcast is today's LAN broadcast and nothing else.
2. **Declaration.** `roles` gains `broadcaster` the day this ticket lands (S0016 declares what is built, never
   ahead); `receiver` stays.
3. **Records.** `broadcast-start` with the section 6.3 record and `maxListeners` (`0` none, the watch's cap
   `4` held across LAN, relay and tunnel by `broadcast-update` carrying `maxListeners`, item M),
   `broadcast-started` -> the `broadcastId` the server confirms, `broadcast-update` when the LAN address or
   the endpoints change, `broadcast-end` on stop. A new id per broadcast, in the share id form. `sourceId` is
   the id of the *source* (item G).
4. **Descriptor.** The endpoints list is LAN first, then `RELAY` when this phone uploads, then `TUNNEL`;
   `certFingerprint` on both exactly when the deployment's leaf is not platform-valid (7.7); `TUNNEL.inner`
   is never omitted and is the documentation host `192.0.2.1` when there is no LAN address (item J of
   `LIVE-BROADCAST`).
5. **Relay upload (7.6).** A data stream, `hello`, `upload {broadcastId, contentType}`, then chunks: the
   length counts the flag byte plus the payload, at most 1048576 (15 item U); `0x01` joinable, `0x02` header
   chunk. `AUDIO_ONLY`: ADTS AAC-LC `audio/aac`, every chunk whole frames and joinable - buildable from what
   `BroadcastAacEncoder` produces. `VIDEO_AUDIO` and `VIDEO_ONLY`: MPEG-TS `video/mp2t`, H.264 and AAC-LC,
   PAT and PMT in the header chunk, an IDR at the start of every joinable chunk, a keyframe at least every
   2 s. **This tree has no TS muxer**, so video relay is its own step (3.3).
6. **Tunnel (7.5).** On `open {tunnelId, broadcastId, scheme}` on the control stream: open a TLS data stream,
   `hello`, `attach {tunnelId}`, bridge to the local `BroadcastHttpServer` (scheme `http`) or to the RTSP
   server (scheme `rtsp`, interleaved TCP, base URL taken from the request - the RTSP server must not
   depend on this phone's LAN address). A bridge has an idle timeout; the producer never logs the tunnel id.
7. **Cast sender (7.8, item R).** `cast {broadcastId, receiverDeviceId}` to a receiver of the same account
   that the directory lists as covering one of the broadcast's endpoints (`plays` first, else `modes` and
   `transports`); `cast-result` reasons `unavailable`, `declined`, `timeout`, `unsupported` shown as words,
   `cast-stop`. A cast never carries `access`.
8. **Being reachable.** The control stream is held for the length of a published broadcast, by the broadcast's
   own foreground service, which already exists for capture; there is no new permission and no always-on
   socket. When the server is unreachable the LAN broadcast continues and the user is told that it is not
   published; it is never silently re-tried in a loop (a bounded, backed-off retry, visible state).
9. **Watch.** The watch is an exchange device of the same account (owner, 2026-10-10) and publishes its own
   broadcasts itself with its own `deviceId` (S0022); this ticket gives the watch's broadcast record the
   cap of 4 across LAN, relay and tunnel by `maxListeners` and changes nothing on the phone's own path.
10. Probes `Timber.d("S0021: ..")`; ids hashed as in S0016; the canary test of S0016 is extended to the
    publish path.

Not in scope: resource publishing (this product has no folder to serve), P2P and STUN (reserved by the
contract), the Drive channel, the receiving side (S0020).

## 3. Constraints and dependencies

### 3.1 Constraints

- Handoff section 6a: what FastMediaSorter's phone does for these steps is ported when it exists, not written
  twice; today its broadcast side has no exchange publishing, so this is written to the contract text and
  compared by the shared vectors and the rung 4 handshake.
- Invariant 11: a listening-port or tunnel feature ships off, behind an explicit, auditable opt-in - the
  per-broadcast switch of item 1, off by default, is that.
- The relay is content-agnostic and visible to the operator (7.9): the setup surface says so in the user's own
  words when publishing is first switched on; the privacy page is updated in the same edit (invariant 14).

### 3.2 Related tickets

S0016, S0018 (the one entry), S0019 (where the switch and the account live), S0020 (the receiving half and
the pin per endpoint), S0006 (phone broadcast), S0007 (watch broadcast), S0014 (privacy page).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0016, S0018, S0019, S0020, S0022, S0024, S0006, S0007, S0014.
- **"Relay or packet server":** answered 2026-10-10 - `RELAY` and `TUNNEL` **and also P2P**. The contract
  reserves `P2P` and puts STUN out of v1 (7.7, 12), so P2P cannot be built to a sealed text: it is S0024,
  with its own proposal beside the contract, and items 1-10 here do not wait for it.
- **Order of work:** audio first - relay and tunnel for `AUDIO_ONLY` - then video through the tunnel (RTSP
  interleaved), then video relay with an MPEG-TS muxer (new code, no donor). Recommended: that order, one
  ticket each when taken up.
- **Watch:** answered 2026-10-10 - an exchange device, enrolled through the phone with a pairing code (5.3):
  S0022. The product value (`streamsplayer-wear`, ask G) becomes necessary rather than nice.
- **Publish default:** answered 2026-10-10, amended 2026-11-10 (quiz decision) - follow `DEVICE-EXCHANGE` 1.6 as written:
  drop the per-broadcast switch, assign the outgoing entry automatically (DS), announce every broadcast from its start (DT),
  and add the server-and-state line (DU). Explicit opt-in for Invariant 11 is the act of adding/enrolling the server entry;
  removing the entry is the opt-out.

### Quiz decisions (2026-11-10)

- Items DT/DU against per-broadcast publishToExchange opt-in -> Follow DEVICE-EXCHANGE 1.6 as written: drop the switch, assign the outgoing entry by itself (DS), announce every broadcast from its start (DT), and add the server-and-state line (DU) (Opt-in of canon Invariant 11 is satisfied by the explicit user act of adding/enrolling the server entry; removing the entry is the opt-out; conforms to contract 1.6 without exception)

## 4. Definition of done

- Between releases inspect and directly exercise the changed area where possible; Android has no measured fast
  Gradle test. RELEASE: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` runs both phone suites with
  the tests: record lifecycle, `maxListeners` updates, chunk framing and the 1 MiB bound on the length field,
  header chunk, joinable rule, tunnel attach and bridge, endpoint order, cast reasons, redaction canaries.
- One handshake report per transport and per mode in the `LIVE-BROADCAST` section 6 form, with the measured
  delay against `targetLatencyMs`, against a listener of another product (the Windows player or the
  FastMediaSorter phone).
- The registry rows of `DEVICE-EXCHANGE` and `LIVE-BROADCAST | StreamsPlayer Android` are updated.

## Video relay landed 2026-10-23

Scope 5 for `VIDEO_AUDIO` and `VIDEO_ONLY`, the third step of the order of work, which the S4203 decisions below
keep in this ticket. "This tree has no TS muxer" in section 0 and scope 5 is the fact of 2026-10-10 and no
longer holds.

- **Muxer.** `MpegTsMuxer` (pure Kotlin): H.264 Annex-B and ADTS AAC-LC into one program; PAT on PID 0, PMT on
  `0x1000`, video `0x100` (also the PCR PID), audio `0x101`; PES with a PTS, an unbounded length for video and a
  bounded one for audio; a PCR on the first packet of every video unit, 100 ms behind its timestamp; per-PID
  continuity counters; CRC-32/MPEG-2 on the sections. PAT and PMT go ahead of every keyframe and again after 200 ms
  without them (item H allows 250 ms). SPS and PPS are put in front of a keyframe that carries none.
- **Chunker.** `TsRelayChunker`: the header chunk is PAT and PMT (376 bytes, replayed by the server); a chunk that
  opens with a keyframe is flagged joinable, the rest of the group of pictures goes out as ordinary chunks (flag
  `0x00`, the server's `flags-ordinary` transcripts) once a chunk holds 32 KiB or 100 ms of media, so the chunking
  adds no group of pictures to the delay. A chunk never passes the bound of item U; a larger unit is split at a packet
  boundary and only the piece with the keyframe's start stays joinable. A keyframe is requested from the encoder
  after 1.8 s without one, so "at least every 2 s" holds when a slow camera stretches the encoder's own interval.
- **Uploader.** `BroadcastRelayUploader` sends the header chunk first on every upload stream and, after a reconnect or
  a full queue, sends nothing until a joinable chunk makes the stream decodable again (item N: a new upload resumes
  at the next joinable chunk). When the relay accepts a stream the encoder is asked for a keyframe, so the first
  picture does not wait for the next scheduled one. A re-asserted record (item H) lets the older uploader go with
  `abandon()`, without the zero length that would end the broadcast under the new stream.
- **Tap.** `VideoRelayTap` is a pluggable record controller of the RTSP library (`setRecordController`), installed
  before the stream starts so the encoder's SPS and PPS are not missed. It reports itself neither recording nor
  running, because the library reads both to decide whether it owns its encoders. The library calls it before the
  RTSP packetizer gets the same buffer, so it catches everything it throws: a relay fault cannot take the LAN
  broadcast down.
- **Descriptor fix.** `RELAY` is listed exactly when this phone uploads one: the server must offer the `relay`
  feature, the session must have something to upload, and the bound must not be spent. Before, an audio record listed
  `RELAY` even for a server without the feature, and a dropped relay did not leave the record it announced.
- **Switch.** The publish row of the broadcast screen shows and writes the choice of the mode the chips are set to:
  the audio broadcast keeps its own, the two camera modes share theirs (before this the camera value had no writer,
  so a video broadcast could not be published). The 12 strings of this ticket were missing from `values-ru` and
  `values-uk`; they are there now, with the summary reworded for both kinds.
- **JVM evidence.** Publish package: 52 tests, 0 failures in both variants - `MpegTsMuxerTest` (11, read back by
  `TsTestReader`, which shares no code with the muxer: every size around the packet boundaries, PSI cadence,
  continuity wrap, PCR, SPS/PPS and delimiter not doubled), `TsRelayChunkerTest` (8), `AdtsFrameTest` (3),
  `BroadcastEndpointsBuilderTest` (6), `RelayChunkWriterTest` (7), `CastResultReasonsTest` (6) and
  `BroadcastPublishFakeServerTest` (11, with the video upload: header first, ordinary chunk before a keyframe
  skipped, zero-length end; the relay left out for a feed-less session and for a server without the feature).
  Full phone suites: 983 tests (store) and 985 (noLegal) with the same 2 failures in both, outside this ticket:
  `ExchangeSignInOnlyTest` (S0027, a 10 s timeout in the test's own wait) and
  `KeystoreBroadcastPasswordStoreTest` (S0028, a DataStore temp-file rename refused on this machine). Neither
  touches the publish path. The DoD command therefore does not exit 0 on the tree as it stands.
- **Third-party demuxer check, 2026-10-25.** The "read by the in-tree reader only" limitation above is closed.
  ffmpeg (libx264) produced 6 s of Constrained Baseline 320x240 30 fps Annex-B with an AUD and repeated
  SPS/PPS per IDR, and ADTS AAC-LC 44100 Hz mono; a scratch JVM generator pushed them through `MpegTsMuxer`
  and `TsRelayChunker` exactly as the producer does (header chunk, then video/audio units on one media clock)
  and wrote the listener stream (header + all chunks concatenated; 57 chunks: 1 header of 376 bytes, 6 joinable
  of 12.4-13.5 KiB, 50 ordinary, 403824 bytes total). ffprobe read it as H.264 on PID 0x100 and AAC on 0x101,
  188-byte packets, 6.0 s; full decode with `-xerror` exits 0 for both tracks - zero demux or decode errors.
  A mid-join stream (header + everything from the third joinable chunk) also decodes with zero errors and all
  120 remaining video frames counted, so a listener that joins on a joinable chunk starts on a decodable
  keyframe as 7.6 requires. Evidence and the sample files: `temp/S0021/` (ts-in, ts-out, gen-ts.log); the
  scratch generator test was deleted after the run and `:exchange-core:test` re-run green.

- **Not verified.** The tap and the upload have not run on a device (the camera and encoder path is compiled, not
  exercised). The handshake and
  end-to-end delay reports of `DEVICE-EXCHANGE` rungs 4 and 5 - audio relay and tunnel, video tunnel, video relay -
  need the owner's test server and a listener of another product: S0034 part A with FMS_A S4213, credentials
  outside this repository. The optional endpoint member `keyframeIntervalMs` is not written (absent means 2000).
- **For the canon session.** 7.6 says a joinable chunk "starts with an IDR access unit" and item H says PAT and
  PMT repeat at least every 250 ms; this producer writes the PAT and PMT packets in front of the keyframe inside the
  joinable chunk. If the text is meant to forbid anything before the IDR in a joinable chunk, the repeat has no
  place to go; the catalog should say which reading holds. Item DV (`verifiers` on `broadcast-start` and
  `broadcast-update`, 2026-10-22) is not implemented here; the password link is S0028.

## S4203 decisions 2026-10-14

S4203 is the release-46 device-exchange milestone of FastMediaSorter Android (FMS_A). The owner's answers of
2026-10-14 to its questionnaire are final; what reaches this ticket:

- **Fully in release 46, video through the server relay included** (В25.2; FMS_A S4217 records it in the roles
  table). The third step of the order of work in section 3.3 - the video relay with an MPEG-TS muxer, new code
  with no donor - is part of release 46 and stays in this ticket rather than becoming a ticket of its own.
- **Handshake reports.** The rung 4 and 5 reports this ticket waits for come from joint run #1 of FMS_A S4213
  (audio relay and tunnel, video tunnel), recorded by S0034 part A; the video relay, once built, is reported in
  the final run of FMS_A S4244 or in a run of its own before it.
- **A link with a password.** The broadcaster's link gains a password (FMS_A S4215, outward access); built in
  S0028 for giving and receiving alike.
- **Guests announce nothing.** Signed in with a guest invitation (S0027), the publish switch is not offered.
- **Encryption.** The relay upload and the tunnel bridge gain the end-to-end layer of S0032 (FMS_A S4239); the
  operator-visibility notice (`DEVICE-EXCHANGE` 7.9) and the privacy page then say what the server still sees (S0032,
  S0033). The word "relays" in that notice leaves the UI (S0030).
- **Sources.** The new broadcast sources FastMediaSorter's phone builds for release 46 (phone screen, a ready
  file, the background camera of the store build - FMS_A S4233-S4235) are not on this product's release-46 list;
  under the port law of `CLAUDE.md` section 2 they become port candidates once they exist there, unscheduled.

## Hand-over from S0026 (2026-10-25)

The DEVICE-EXCHANGE round S0026 recorded two dated registry exceptions (until 2026-12-31) that this ticket carries;
the proposal is `PLAN/DONE/S0026_contract-sync-2026-10-12/registry-sync.patch`.

- **Items DS, DT and DU (1.6, section 29).** The device assigns its outgoing entry by itself (the first entry recorded
  is outgoing, the flag passes on when it is removed); a broadcast is announced on it from its start; the broadcast
  control surface names the server and the state of the broadcast there. Today the per-broadcast switch
  `publishToExchange` (off by default) decides, and no line names the server. Item DT ("no separate switch") and
  canon invariant 11 (an explicit opt-in for a relay or a tunnel) disagree here: the owner decides before any code,
  either to keep the opt-in (then a proposal beside the contract, the catalog moves first) or to drop it (then DS to
  DU are built as written).
- **Item DV (1.7, section 30).** `verifiers` on every `broadcast-start` and `broadcast-update`; the empty array is the
  wire of an open link. Built together with the link password of S0028.

## Outgoing entry landed 2026-11-10

Items DS, DT and DU of `DEVICE-EXCHANGE` 1.6, built by the unattended queue pass of 2026-11-10 under the quiz decision of the
same day. They supersede the per-broadcast switch of scope 1, the last sentence of section 1 ("off by default") and the
opt-in reading of section 3.1: the explicit act is adding the server entry, removing it is the opt-out (section 3.3).

### Plan (coverage inventory and topology)

| Line | Phase / step | Static check |
| --- | --- | --- |
| DS: the one recorded entry that may produce is outgoing, with no step; a guest's is not; removal leaves none | A1 `OutgoingExchangeEntry` over `ExchangeEntryDao.observeEntry()` | file exists; `OutgoingExchangeEntryTest` (4 cases) at the release |
| DT: every broadcast announced from its start, sound and camera, no switch | A2 `OutgoingEntryPublication`; A3 both services bind it, the relay tap is always installed and started only while announced | `publishToExchange` greps to nothing in `phone/src` |
| DT: an entry recorded while live announces at once; removed or replaced ends it there | A2 `collectLatest` on the entry id: `endAndAwait`, then `startAndAwait` | read of `OutgoingEntryPublication.bind` |
| Retired switch and notice leave the stored settings | A4 `BroadcastSettings`, `AppSettings`, `BroadcastSettingsStore` (retired keys removed on write) | `DriveChannelScopeAndDefaultsTest` at the release |
| DU: one line names the server and the state; without a server "this network only", leading to the server screen | B1 `BroadcastControlManager.renderPublishState` over publisher state, published endpoints, outgoing entry and broadcast state | five `broadcast_publish_state_*` strings in EN, RU, UK |
| DU: no secret on the line | B1 shows the label or `host:port` only | read |
| 7.9 operator visibility said where the explicit act is | B3 server card line `exchange_broadcasts_on_server` with the relay term, and **Learn more** to the portal page `broadcast-start` | `ExchangeTermsDictionaryTest`, `ExchangePortalLinksTest` at the release |
| The two settings rows, their notice and the exchange-screen switch removed | B2, B3 layouts, `BroadcastSettingsPanelManager`, `ExchangeActivity`, nine strings in three languages | grep of the removed keys and ids returns only the retired-key list |
| Help, privacy, portal, data safety, icon map, README, CHANGELOG in one edit | B4, B5 | `generate-help-pages`, `build-site -Check`, the five document gates |
| DV gap: the `maxListeners` update without `verifiers` | C1 `BroadcastExchangePublisher.updateLanListeners` | `BroadcastPublishFakeServerTest` lifecycle case at the release |
| The watch's own switch (`WearExchangeRuntime.mutablePublish`) | out-of-scope: S0022's code, scope 9; parked as S0069 | - |
| Registry rows (`DEVICE-EXCHANGE`, `LIVE-BROADCAST` of StreamsPlayer Android) and retiring the two S0026 exceptions DS-DU and DV | out-of-scope until the rung 4 observation the amendment names as the evidence owed (its "Compatibility"); then a patch on top of S0057 | - |

No phase consumes what a later one produces: A1 before A2 and B1, A2 before A3, B4 after B1 to B3.

### What changed

- `phone/.../data/exchange/OutgoingExchangeEntry.kt` (new): the outgoing server, named by its label or by the `host:port` typed.
- `phone/.../broadcast/OutgoingEntryPublication.kt` (new): follows the outgoing entry for a live session; probe
  `S0021: outgoing entry present|absent`.
- `BroadcastCaptureService`, `VideoBroadcastService`: bind the publication instead of reading the switch, unbind on every
  stop path; the video tap is installed with every camera session and runs only while the broadcast is announced.
- `BroadcastControlManager`: the item DU line; `ServerLine` holds what it draws.
- `BroadcastSettingsPanelManager`, `fragment_settings_broadcast.xml` (both orientations): the two publish rows and the
  first-switch-on notice are gone; the Drive rows stay.
- `ExchangeActivity`, `activity_exchange.xml`: the switch becomes the line `tvBroadcastsOnServer` and the button
  `btnHelpBroadcastStart` (portal `broadcast-start`); both are hidden for a guest.
- `BroadcastExchangePublisher.updateLanListeners`: `verifiers` on the `maxListeners` update (item DV); the two
  `broadcast-start` probes (unanswered, refused) reshaped into one-line statements, as the probe gate requires.
- Strings EN/RU/UK: removed `settings_broadcast_publish_title`, `_summary`, `_video_title`,
  `broadcast_publish_notice_title`, `_text`, `broadcast_publish_state_publishing`, `_published`, `_not_published`,
  `exchange_publish_switch_label`; added `broadcast_publish_state_announcing`, `_on_server`, `_on_relay`,
  `_not_on_server`, `_network_only`, `exchange_broadcasts_on_server`.
- Text: help sources `broadcast`, `broadcast-settings`, `exchange` (pages regenerated, translations recorded),
  `docs/PRIVACY_POLICY*.md` section on the exchange server (site regenerated), `docs/PORTAL_SECTIONS_STREAMSPLAYER.md`,
  `docs/play/PLAY_DATA_SAFETY.md`, `docs/PHONE_SHELL_ICON_MAP.md`, `README.md`, `CHANGELOG.md`;
  `tools/help/capture-help-screenshots.ps1` scrolls to the Drive rows.
- Tests: `OutgoingExchangeEntryTest` (new), `DriveChannelScopeAndDefaultsTest`, `ExchangeTermsDictionaryTest`,
  `ExchangePortalLinksTest`, `BroadcastPublishFakeServerTest`.

## Last Audit

**2026-11-10, unattended queue pass, static (pre-handoff).** Not built, not tested until the release: this repository
has no measured fast Gradle form (CLAUDE.md section 5a).

Evidence run in this pass:

- `pwsh -NoProfile -File tools/help/generate-help-pages.ps1` - exit 0, `generate-help-pages: PASS (files=37 written=12 ..)`; then `-Check` exit 0, `PASS (checked=37 pages=11 ..)`.
- `pwsh -NoProfile -File tools/site/build-site.ps1 -Check` - exit 1 before the rebuild (three privacy pages differ), exit 0 after `build-site.ps1`: `OK site: 3 pages ..`.
- `assert-doc-registry` exit 0 `PASS (records=30 files=269 ..)`; `assert-doc-links` exit 0 `PASS (documents=42 pages=3 links=174)`; `assert-doc-style` exit 0; `assert-termbase` exit 0; `assert-translation-freshness` exit 1 with 13 findings (the rewritten RU/UK blocks), exit 0 `PASS (blocks=220 ..)` after `-Record`.
- `tools/release/check-syntax.ps1` exit 0 `release-syntax: PASS`.
- `tools/help/assert-help-map.ps1 -CommittedBaseline tools/help/help-map-baseline.txt` exit 1, 5 `STALE-SHOT`: `broadcast` and `broadcast-settings` are this ticket's (layouts and strings changed); `catalog`, `filter-and-sort`, `qr-scanner` were stale before it (S0049).
- XML parse of the three `strings.xml`: the exchange string set is 303 in EN, RU and UK alike.
- Grep: `publishToExchange`, `publishAudioToExchange`, `publishVideoToExchange`, `rowPublishToExchange`, `switchPublish`, `broadcastPublishNoticeShown`, the removed string keys - no hit outside `PLAN/` and the retired-key list of `BroadcastSettingsStore`.

Verdict: development complete; the headline behaviour is written end to end in code and text but proven by no build or run.

- [ ] RELEASE: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` - both phone variants compile (Hilt graph with `OutgoingExchangeEntry` in two services and `BroadcastControlManager`), the phone suites including `OutgoingExchangeEntryTest`, `DriveChannelScopeAndDefaultsTest`, `ExchangeTermsDictionaryTest`, `ExchangePortalLinksTest`, `BroadcastPublishFakeServerTest`, and `:exchange-core:test`.
- [ ] RELEASE: `tools/help/capture-help-screenshots.ps1 -Help broadcast` and `-Help broadcast-settings` on an emulator, then `generate-help-pages.ps1` and `assert-help-map.ps1` - clears this ticket's two `STALE-SHOT`.
- [ ] MANUAL: a phone with one `member` entry and no prior choice starts an audio broadcast and a camera broadcast; the account's directory on another device lists each without any step, and Broadcast Control names the server and shows announcing, then on the server (with the relay through the server where the server lists `relay`). Logcat shows `S0021: outgoing entry present` and `S0021: broadcast started`.
- [ ] MANUAL: the same phone with the server removed while on air: the broadcast leaves the directory, the LAN broadcast keeps playing, the line says "this network only" and a tap opens the exchange server screen; adding the server again while on air announces the broadcast at once.
- [ ] MANUAL: a guest entry: no line on the server card, the broadcast screen says "this network only", nothing is announced.
- [ ] MANUAL: the device run of the video tap and upload, and the rung 4 and 5 handshake reports (audio relay and tunnel, video tunnel, video relay) against the owner's test server and a listener of another product - S0034 part A with FMS_A S4213; credentials outside this repository.
- [ ] After the rung 4 observation: a registry patch on top of S0057 that retires the two S0026 exceptions (DS-DU, DV) and updates the `DEVICE-EXCHANGE` and `LIVE-BROADCAST | StreamsPlayer Android` rows.

Parked: S0069 (the watch's own publish switch against item DT).
