# S0020 - Playing other people's broadcasts from any network: descriptor in, relay or tunnel out, no account needed

**Status:** BlockNeedUserTest
**Status note:** Development complete (code and JVM suites green in both phone variants, receiver transport drift rows decided). Only testing remains: live LAN/RTSP/RELAY/TUNNEL handshake and delay reports on the S4213 stand (S0034 part A), cast, and video on a device.
**Phase:** device exchange, receiver side - independent of the build phases of handoff section 8
**Verification level:** unit tests on the JVM; the handshake and end-to-end delay of `DEVICE-EXCHANGE` rungs 4 and 5 with a real producer and the running server, recorded as `LIVE-BROADCAST` section 6 asks.

## 0. Source

- Owner, 2026-10-10 (this session): connections to other users' broadcasts, possibly through their exchange
  servers, are any number, and are part of the broadcast description; the server for this product's own
  broadcasts is one (S0021).
- `DEVICE-EXCHANGE` 5.4 (a `broadcastId` is a capability; an outsider `connect` needs no `hello`), 7.5
  (tunnels by `broadcastId` plus `scheme`), 7.6 (the listen URL), 7.7 (endpoint order, `RELAY`, `TUNNEL`,
  `certFingerprint`), 7.8 and 14.5 item R (cast), 9.4-9.6, 14.3 item D, 15 item V (every outsider `connect`
  inside TLS), 11 rungs 4-5.
- `LIVE-BROADCAST` section 1 (consumer rules: start at the live edge, never let the delay grow, rejoin at the
  live edge), section 4 (the hand-off channels: link, file, barcode, and the directory) and items C-G of the
  amendment of 2026-10-07.
- `PROPOSAL-2026-10-10-streamsplayer-android-receiver-entries.md`, revision of 2026-10-10 (the trust state of a
  foreign endpoint belongs to the channel, not to an entry).
- `CONSUMER_PROMPT_streamsplayer.md` items 4 and 5, written for the Windows player; the same work, on this
  platform.
- Donor, read-only: FastMediaSorter Android `data/remote/sftp/anywhere/ExchangeTunnelProxy.kt` (71 lines, the
  loopback forwarder) with `ExchangeTunnelProxyTest`.
- Facts in this tree, 2026-10-10: `phone/src/main` has no `fmsx`, `RELAY` or `TUNNEL` symbol, so nothing
  resolves a tunnel URL or lists a relay endpoint; `ImportStreamBroadcastUseCase` imports a descriptor but no
  screen, link, file or scan calls it (the listener role is second wave in
  `docs/contracts/LIVE-BROADCAST.md`); the QR code is encoded and never scanned.

## 1. Goal

A person is handed a broadcast - a link, a file, a code - by someone whose server is not theirs, opens it in
this app and hears or sees it from wherever they are: the LAN endpoint first, then the relay, then the tunnel,
in the order the descriptor lists, with the delay rules of `LIVE-BROADCAST` held on every transport, and with
no account on that server. The same path plays a broadcast listed by the user's own account (S0019).

## 2. Scope

1. **Hand-off routes into the importer.** Wire `ImportStreamBroadcastUseCase` to the user: paste of a
   descriptor or a link, a file (`ACTION_OPEN_DOCUMENT`), and the directory of S0019; a barcode scan only if
   the owner takes a scanner (3.3). One importer for all of them, the `sourceId` rule replaces and never
   duplicates; an unsupported version or a malformed text is one clear message, never a partial import.
2. **Endpoint order.** Try the descriptor's endpoints in the listed order and skip a transport this build does
   not implement; a reconnect starts at the top. LAN `HTTP` and `RTSP` as today.
3. **`RELAY`.** An HTTPS stream on the server named in the endpoint. With `certFingerprint` present, pin it; if
   the player cannot pin, play through a loopback proxy that does - never with checking off. Without it, the
   platform trust store decides.
4. **`TUNNEL`.** Port the loopback forwarder (ledger row): every connection the player opens to the local port
   becomes one TLS stream, **no `hello`**, `connect {broadcastId, scheme}` (the id is the capability); the
   player gets `inner` with its host and port replaced by the forwarder's; RTSP over interleaved TCP. An
   unknown or ended id and a listener over the cap are `unavailable` and `503`, shown as "not available".
5. **Trust of a foreign endpoint.** There is no entry for it. `certFingerprint` of the endpoint is the anchor;
   a leaf that fails the platform store and has no fingerprint is shown to the user and, if accepted, pinned
   **to the channel and the endpoint** (`host:port`), kept with the channel row and replaced only by the user's
   explicit act. A changed leaf is refused with both fingerprints. No password is ever sent to such a server.
6. **Live rules on each transport.** Playing within 1 s of connecting on a LAN, rejoining at the live edge, a
   buffer raised only to the endpoint's own `targetLatencyMs`, a delay that never grows (drop, never stall).
7. **Receiver declaration.** `receiver.modes`, `transports` and `plays` (item E) updated to what this ticket
   really implements, and nothing else (only matters while an account exists).
8. **Cast receive (needs the one entry).** The control stream of S0016 handles `cast-offer`: the default is to
   ask the user; playing at once is a setting off by default and worded as what it does. `cast-answer` with
   `declined`, `timeout` or `unsupported` as 14.5 item R defines; `cast-stop` and the end of the broadcast
   stop playback. `cast-offer` carries no `access` and only comes from the same account.
9. Probes `Timber.d("S0020: ..")`; a broadcast id is logged as 8 hex of its SHA-256; a descriptor's endpoint
   URLs are not logged whole.

Not in scope: publishing a broadcast through the exchange (S0021), P2P, the Drive channel, Wear.

## 3. Constraints and dependencies

### 3.1 Constraints

- Handoff section 6a item 1: the forwarder is ported, not rewritten; the player behaviour stays in
  `STREAM-BANK` documents 03, 05, 06, 07 - nothing here changes routing or buffering of a plain stream.
- No certificate check is ever turned off to make a stream play.
- Items 1-6 need no account: they are the listener role of `LIVE-BROADCAST`. Item 8 needs the one entry and
  is inert without it.
- `P2P` is the owner's wish for this product (2026-10-10) and is **not** a transport this ticket plays: the
  contract reserves it (7.7, 12) and a descriptor endpoint of that transport is skipped like any transport
  this build does not implement, until S0024 and the amendment it waits on exist.
- A descriptor is someone else's text: hosts and titles in it are data, never rendered as markup, never
  followed anywhere but the endpoint list's transports.

### 3.2 Related tickets

S0016 (item 8 only), S0018 (the channel's binding columns), S0019 (the directory view and the import of a
listed broadcast), S0021 (the producer half), S0005 (player), S0006 (the producer whose descriptor this
plays), S0007 (watch broadcast as a source of AUDIO_ONLY).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0016, S0018, S0019, S0021, S0005, S0006, S0007.
- **Order:** answered 2026-10-10 - S0017, then this ticket, then S0016-S0019, then S0021.
- **Scanner:** answered 2026-10-10 - CameraX with `zxing-core`; S0023 reads a QR and hands this importer the
  text. Until S0023 lands the routes are paste, file and the link.
- **Cast default:** ask the user every time (the contract's default, recommended).
- **Acceptance partners:** rungs 4 and 5 need a producer on the exchange (the FastMediaSorter phone or FMS_W)
  and a server that relays; who runs them.

## 4. Definition of done

- `.\gradlew.bat :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest --console=plain` exits 0 with the
  tests: importer routes and refusals, endpoint order and skip, pin versus proxy, forwarder with no `hello`,
  trust of a foreign endpoint, live-edge rules, cast reasons.
- One handshake report per transport in the `LIVE-BROADCAST` section 6 form, with the measured delay against
  `targetLatencyMs`.
- Ported file rows in the ledger with `check-drift.ps1` exit 0.
- The registry rows of `LIVE-BROADCAST | StreamsPlayer Android` (listener) and `DEVICE-EXCHANGE` are updated
  with the evidence.

## 5. Handshake & Conformance Report

### 5.1 Fresh automated verification (2026-10-14)

Command:

```powershell
.\gradlew.bat :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest :phone:assembleStoreRelease --console=plain
```

- Exit **0**: `BUILD SUCCESSFUL in 10m 54s`; 142 actionable tasks, 71 executed and 71 up-to-date.
- Store JVM XML: **661 tests, 0 failures/errors, 1 skipped**.
- noLegal JVM XML: **663 tests, 0 failures/errors, 1 skipped**.
- Both skips are the existing `ExchangeConnectionStringVectorsTest` replay placeholder; they are not counted
  as passing conformance cases. S0017/S0026 own the corpus reconciliation.
- Release DI, Kotlin/Java compilation, R8, resource shrinking, `lintVitalStoreRelease` and APK packaging ran.
  Artifact: `phone/build/outputs/apk/store/release/phone-store-release.apk` (5,063,440 bytes).
- Expected: both suites and release assembly exit 0. Actual: exit 0, 1,324 tests, zero failures/errors,
  two explicitly reported skips. This is local build evidence, not a device or publication verdict.
- Full build output: `temp/S0020/verification-final.log`; earlier failed development runs are superseded by
  this final run. No source was edited during this final invocation.

Additional checks:

| Command/check | Exit | Expected / actual |
| --- | --- | --- |
| `pwsh -NoProfile -File tools/port-ledger/check-drift.ps1` | **1** | Expected 0 uncarried; actual `UNCARRIED: 52 of 710 row(s)`. Full-tree gate remains red. |
| `pwsh -NoProfile -File tools/contracts/check-sync.ps1 -CatalogRoot <catalog locator from CLAUDE.md>` | **1** | Expected current pointers; actual `FAIL: DEVICE-EXCHANGE pointer does not name current version 0.28`. S0026 owns the version reconciliation. |
| `pwsh -NoProfile -File tools/harness.ps1 spec_catalog/validate` | **0** | Expected valid catalog; actual `8 OK, 1 WARN, 0 FAIL`. Warning: existing journal paths for six completed specs under DONE. |
| XML/string-key check for the cast setting | **0** | Expected one key per EN/RU/UK; actual all three XML files parse, no duplicate keys, `cast_play_immediately` exists once in each. |
| Static receiver wiring checks | **0** | Expected importer/audio/video/TLS/cast/network/live-buffer/log wiring; actual 10 PASS. |
| `adb devices -l` | **0** | Actual no connected target. No device playback or UI observation is claimed. |

The ported forwarder, connector, envelope and donor tests retain their existing ledger rows. No source
hash was refreshed without carrying its change, and none of the 52 outstanding rows was hidden by a
blanket drift decision. Their output is recorded in `temp/S0020/drift-final.log`.

Catalog write boundary: `S0020_exchange-receiver-playback-and-cast/registry-evidence.patch` is the exact
proposed change to the two own registry rows, with the reason and evidence in each note. It preserves
implementation/read version cells and explicitly leaves measured conformance pending. The catalog was
not edited; S0034/canon applies or reconciles the proposal with the joint-run evidence.

### 5.2 Live handshake evidence - pending

The owner confirmed on 2026-10-14 that the S4213 stand is unavailable and authorized code and automated
checks only. The previous text in this section used example hosts and configured target offsets. It was
not a live handshake report and has been removed to avoid implying measured conformance.

| Transport | Producer/server run | Initial delay | Delay after forced reconnect |
| --- | --- | --- | --- |
| LAN HTTP, AUDIO_ONLY | Not run | Not measured | Not measured |
| LAN RTSP, VIDEO_AUDIO / VIDEO_ONLY | Not run | Not measured | Not measured |
| RELAY, HTTPS with endpoint trust | Not run | Not measured | Not measured |
| TUNNEL, HTTP and interleaved RTSP | Not run | Not measured | Not measured |

S0034 part A records the S4213 run in home Wi-Fi and mobile internet: producer/build, consumer/build,
transport and mode, endpoint target, connect-to-first-sound/picture, measured source delay before and
after forced reconnect, network switch and observed live-edge recovery. No endpoint capability, server
credential or full URL belongs in that report. Cast acceptance/decline/timeout, cast-stop and broadcast
removal must also be observed against the running server.

### 5.3 Re-verification and emulator observation (2026-10-23)

**Drift of the receiver transport.** The five rows of this ticket's transport (`ExchangeConnector`,
`ExchangeTunnelProxy`, `DeviceExchangeEnvelope` and the two tests) had drifted after 2026-10-11. Each donor diff
was read against the local file and decided with `decide-drift.ps1`, the reason written next to the row:

| Row | Donor change | Decision |
| --- | --- | --- |
| `ExchangeTunnelProxy` and its test | own-account routing for the SFTP JSch proxy (`connectByResourceId`) | not applicable: loopback media forwarder, no resource role |
| `DeviceExchangeEnvelope` and its test | register, password, account, grant, clipboard and administration types (32 to 49) | roles this product does not declare; S0027/S0028 carry what they use |
| `ExchangeConnector` | `adopt` hook, `RENEWED` verdict, trace lines, hand-rolled base64 | TLS 1.2 floor, handshake timeout and same-key renewal already present locally; connects are bounded by timeouts; phone minSdk is 26 |

`check-drift.ps1` lists none of the five any more. The whole tree still exits **1**: `UNCARRIED: 122 of 721`
(52 of 710 on 2026-10-14), none of them decided here. Receiver-adjacent rows among them (`BroadcastEndpointDto`,
`BroadcastDescriptorDto`, `StreamPlaybackHelper`, `StreamVideoRetryPolicy`, `StreamHeadlessPlayManager`) drifted
for the password-protected broadcast (FMS S4258, S0028) and for the sealed endpoint (`DEVICE-EXCHANGE` 27 item
DK, S0032), plus a headless-play gate for endpoint-list channels that the service-side resolution here already
covers (`AudioPlaybackService` looks the descriptor up by url). They are carried or decided by those tickets.

**Known gap, not this ticket's.** The donor endpoint now carries `seal`; this build ignores the member and would
try a sealed endpoint as clear bytes. S0032 owns it.

**Suites.** `.\gradlew.bat :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest :phone:assembleStoreDebug
--continue --console=plain` exits **1**: store 955 tests, noLegal 957, 0 skipped, **2 failed in each**, the
same two, and both fail alone too (`temp/S0020/verification-2026-10-23-continue.log`,
`temp/S0020/rerun-two-failed.log`):

- `ExchangeSignInOnlyTest.firstSignInStoresTokenUnderTheEnrolledDeviceAndNeverRegisters` - 10 s timeout waiting
  for `AccountReady` (S0027 area);
- `KeystoreBroadcastPasswordStoreTest.password is encrypted absent after key loss and removed with the channel` -
  DataStore `Unable to rename .. secrets.preferences_pb.tmp`; the test's `produceFile` lambda calls
  `temp.newFolder()` on every invocation, so each call names another directory (S0028 area).

`ExchangeViewModel.kt` and `KeystoreBroadcastPasswordStore.kt` were last edited by S0028 on 2026-10-23 14:25.
Neither failure is on this ticket's receiver path, so the definition-of-done command as written stays red until
S0027/S0028 fix them. This ticket's own classes pass in both variants, **exit 0, 16 classes, 104 tests, 0
failed, 0 skipped** (`temp/S0020/s0020-suites-2026-10-23.log`): `BroadcastPlaybackResolverTest`,
`CastReceiverHandlerTest`, `ExchangeTunnelProxyTest`, `DeviceExchangeFakeServerTest`,
`DeviceExchangeTrustRuleTest`, `DeviceExchangeEnvelopeTest`, `ImportStreamBroadcast*`,
`ExchangeReceiverRuntimeTest`, `ExchangeImportActivityTest`, `BroadcastLogRedactorTest`,
`BroadcastPlaybackLifecycleTest`, `ProtectedBroadcastTest`, `BroadcastDescriptorRoundTripTest`,
`CastResultReasonsTest`, `StreamVideoRetryPolicyTest`.

**Contracts check.** `tools/contracts/check-sync.ps1` exits 1 on `REPO-STAMP pointer does not name current
version 0.13`. The `DEVICE-EXCHANGE` failure of 2026-10-14 is gone (the pointer reads 1.7, S0013, 2026-10-23);
the `REPO-STAMP` pointer is not this ticket's.

**Emulator.** `sdk_gphone64_x86_64`, API 35, the store debug build of this tree, a test tone served from the
development machine over HTTP and over HTTPS with a self-signed leaf. It is not an exchange producer and not a
server, so it is **not** a handshake report in the `LIVE-BROADCAST` section 6 form and the table of 5.2 stays as
it is. What was observed (`temp/S0020/emu/`, screenshots 01-05 and the server logs, not committed):

| Step | Observed |
| --- | --- |
| `fmsbcast://import` link, LAN `HTTP` endpoint | descriptor saved, endpoint 0 tried, player ready and playing about 1.0 s after the resolver started; title from the descriptor; endpoint url redacted in the log |
| `RELAY`, HTTPS, self-signed leaf, no `certFingerprint` | "Untrusted server certificate" with the leaf fingerprint, equal to the one computed from the leaf DER outside the app; TRUST pins it to `host:port`, loopback relay proxy connects with the pin, playing 1.2 s after TRUST |
| the same link opened again | no dialog, pin reused, playing 0.8 s after start |
| the server's leaf replaced on the same `host:port` | "Server certificate changed" naming the pinned and the presented fingerprint, connection refused, no audio |

Not observed: video, RTSP, `TUNNEL`, cast, a network switch, any measured source delay. They stay with
S0034 part A (S4213), and the second manual item below stays open for them. The fullscreen player draws its title
row under the status bar on this device (screenshot 02) - noted for S0005, not changed here.

## S4203 decisions 2026-10-14

S4203 is the release-46 device-exchange milestone of FastMediaSorter Android (FMS_A). The owner's answers of
2026-10-14 to its questionnaire are final; what reaches this ticket:

- **In release 46.** This product receives and broadcasts through the exchange server fully (В25.2); FMS_A S4217
  records it in the roles table.
- **Status reconciled through the harness.** The header said `Implemented` and the journal said `Draft`;
  `spec_catalog/update -Status Partial` set both on 2026-10-14. The code and the JVM suites of section 5.1
  stand. Section 5.2 names placeholder hosts and configured latencies, not a measured run, so definition-of-done
  item 2 (one handshake report per transport with the measured delay) is open.
- **Where the measurement comes from.** Joint run #1 of FMS_A S4213 on the current transport, against the owner's
  test server (credentials in the FMS_A questionnaire, В1.1 - never copied here), recorded by S0034 part A; the
  final two-network run of FMS_A S4244 repeats it after encryption. This answers the open owner input
  "Acceptance partners".
- **Network change.** The owner's rule for every connection (В11): try the connection kinds fastest to slowest
  before saying "no connection", and switch on a network change. For a descriptor the listed order already runs
  LAN, relay, tunnel; a network change restarts the order from the top - checked against the code when the
  ticket is taken up, and added if missing.
- **What moves to other tickets.** A link with a password and the list that appears on open: S0028 (FMS_A S4215,
  S4217). Encrypted broadcasts: S0032 (FMS_A S4239). The receiver-declaration bounds of `DEVICE-EXCHANGE` 0.20
  section 18 touch item 7 here; S0026 reads them.

## Implementation plan (2026-10-14)

Complex rung: receiver integration spans TLS, playback lifecycle, audio, cast and network changes.
The existing contract covers the wire; no contract amendment is introduced.

1. Repair endpoint resolution and trust. Preserve descriptor order, perform TLS work off the main
   thread, handle the connector's typed untrusted/changed results, validate transport URLs, and leave
   proxy ownership with each playback session. Verify with resolver/trust/tunnel regression tests.
2. Connect playback surfaces. Resolve imported audio in the service, restart broadcasts from the first
   endpoint on network change, cancel obsolete resolutions, release proxies, and bound broadcast
   buffering to the endpoint target without changing plain-stream policy. Verify both phone flavors.
3. Connect cast receive to the authenticated control stream and foreground consent, preserve the
   publisher listener, handle stop/ended while consent is pending, and declare the receiver at enroll.
   Verify cast, control-stream and enrollment tests; build the target variant for DI wiring.
4. Record fresh commands and the static audit. The document index is unchanged; the affected records
   are this ticket and its evidence. Contract registry completion and measured handshake/delay reports
   remain with S0034/S4213: the owner confirmed the live stand is unavailable on 2026-10-14.

Each step consumes existing APIs or the preceding step's implementation. No live handshake is inferred
from a fake server, a configured target or a JVM test.

## Implementation result (2026-10-14)

- Shared importer: complete bounded file reads and clear failure feedback, malformed/null-field refusals,
  descriptor persistence after import acceptance, explicit mode routing and tunnel-only channel imports.
- Ordered resolution: typed untrusted/changed TLS results, channel/endpoint pin reuse, off-main handshake
  probes, URL/inner-scheme checks, per-session proxy ownership and endpoint fallback.
- Audio and fullscreen integration: endpoint resolution and fallback, target-bounded broadcast buffers,
  interleaved RTSP, cancellation of obsolete work and restart from the first candidate on network change.
  Plain-stream buffer/retry policy retains its existing path.
- Proxy lifetime: bounded TLS/response waits, upstream socket cleanup and bidirectional tunnel pump cleanup.
- Cast receive: application-owned activity/service lifetime, a control-stream observer alongside the
  publisher, default consent, persisted optional immediate acceptance, pending-offer cancellation,
  cast-stop/removal and playback launch with its descriptor already persisted. Enrollment omits the
  optional plays list instead of exceeding its 8-pair wire bound. The sender initializes its cast path
  before exposing Published, closing the race caught by the fake-server regression.
- Diagnostics: stream capability paths are redacted even in exception text; broadcast ids use hashes.
  AndroidKeyStore opens on token access rather than receiver startup.

| Surface | Kind | Locales | Result |
| --- | --- | --- | --- |
| Account cast setting and existing import/trust/cast UI | Authored resources | EN/RU/UK | Updated together; XML/key check passes. |
| README | Authored source | EN | Receiving flow, default consent, immediate option and pending live evidence documented. |
| Privacy policy | Authored source | EN | Foreign endpoint connections, local pins and incoming casts documented. No permission added. |
| Product site / listing / hub | Not configured in this repository | N/A | No configured remote/site or local listing source; product positioning unchanged. Shared portal remains its owner's surface. |
| Document index / contract pointers | Authored references | EN | Index unchanged; version catch-up belongs to S0026 and joint registry acceptance to S0034. |

## Last Audit

**Date:** 2026-10-14 | **Mode:** static | **Outcome:** Broken (overall DoD gates, not a JVM/build failure)
**Counts:** PASS 14 | WARN 0 | FAIL 2 | MANUAL 2 | UNCHECKABLE 0 | EXEMPT 0

**Re-read 2026-10-27 (pre-handoff, static; no new build or test run):** Outcome BlockNeedUserTest. Both FAIL items below are
whole-tree conditions owned by other tickets (port-drift rows by the tickets that carry them, pointer versions by S0041); this
ticket's own receiver rows are decided and its own suites are green (section 5.3). Only the MANUAL list is left: the live
handshake and delay reports on the S4213 stand, and cast, video, RTSP and tunnel on a device.

The implementation checks and final build are green. The journal remains Partial because the overall
acceptance gates below are not closed; neither Implemented nor Verified is claimed.

### Action items

1. FAIL - whole-tree port drift: 52 uncarried rows; reconcile the actual donor changes before claiming the
   DoD drift exit 0. Receiver and broadcast contract/source reconciliation is tracked with S0026.
   *2026-10-23: the five receiver-transport rows are decided, the whole tree is 122 of 721 (section 5.3).*
2. FAIL - contract-sync check: DEVICE-EXCHANGE pointer is behind 0.28; S0026 owns the catch-up. This ticket
   does not claim full conformance to the new catalog version.
   *2026-10-23: the pointer reads 1.7; the check now fails on the REPO-STAMP pointer (section 5.3).*

### Manual / on-target

- [ ] S0034/S4213: real LAN HTTP, RTSP, relay and tunnel handshakes in both networks; measure initial and
  reconnect source delay against the endpoint target, observe network switching and cast stop/removal.
  Owner confirmed the stand is unavailable; none of these measurements is replaced with a JVM result.
- [ ] Observe the import/trust/audio/video/cast UI on a target, then reconcile the proposed own registry
  rows with that evidence. 2026-10-23: import by link, LAN audio, the relay trust prompt, pin reuse and the
  changed-leaf refusal observed on an emulator (section 5.3); video, RTSP, tunnel and cast not yet.
