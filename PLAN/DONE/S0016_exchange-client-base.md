# S0016 - Exchange client base: the device that enrols, keeps its token, lists and holds its control stream

**Status:** Implemented
**Status note:** Implemented and verified via unit tests and in-JVM TLS fake server (2026-10-11): codec, state-dependent frame bounds, trust rule, enroll with wiped CharArray password, hello/welcome, secret store seam, control stream with mobile presence rule, directory list/subscribe with revision-gap rule, records-v1 replay, Ask 2 dependency isolation test, log redaction test with 8-hex SHA-256 id hashing, inertness. External device run awaits designated test server.
**Phase:** device exchange, receiver side - independent of the build phases of handoff section 8
**Verification level:** unit and fake-server tests on the JVM (codec, trust rule, session, redaction); device run against the running exchange server when designated.

## 0. Source

- `DEVICE-EXCHANGE` 0.17, read in full 2026-10-10: sections 2, 3, 5.1-5.2, 6.1-6.3, 7.1-7.4, 7.9-7.10, 9, 11
  (rung 1), 14.1 item A, 14.3 item D, 14.4 item E, 14.5 items F-I, 15 items T and V.
- `PROPOSAL-2026-10-10-exchange-connection-string.md` (FMS_W, proposed): ask 2 (a consumer is complete without a
  producer and without a local agent) and the machinery of ask 5 steps 2-4. Read with
  `PROPOSAL-2026-10-10-streamsplayer-android-receiver-entries.md` (this repository: a receiver is such a device
  too, asks A, B, H).
- `docs/contracts/DEVICE-EXCHANGE.md` (this repository's pointer, created with this ticket).
- Facts read in this tree on 2026-10-10: **no exchange code exists** (no envelope, frame codec, enrolment, token
  store, entry or gate; `grep` for `exchange` finds only the portal link dialog and comments);
  `ImportStreamBroadcastUseCase` and `BroadcastDescriptorParser` exist and are tested but nothing in the UI calls
  them (listener role, second wave, `docs/contracts/LIVE-BROADCAST.md`); the manifest declares
  `allowBackup="false"` and `usesCleartextTraffic="true"` (LAN streams); OkHttp 4.12, Gson, Room, DataStore and
  Hilt are the stack; no Keystore wrapper exists.
- Donor, read-only: FastMediaSorter Android `data/remote/exchange/DeviceExchangeEnvelope.kt` (156 lines),
  `DeviceExchangeFrameCodec.kt` (53) with `DeviceExchangeEnvelopeTest` and `DeviceExchangeRecordVectorsTest`. Its
  enrol/hello client (S4116 phases 03-08 of that repository) does not exist yet.

## 1. Goal

**Role (owner, 2026-10-10):** this product is a receiver of other devices' broadcasts and a broadcaster to other
devices through the exchange; it publishes through **one** exchange server, and reaches any number of other
users' broadcasts, possibly on their servers, from the broadcast's own description (the descriptor), with no
account of its own on those servers. This ticket is the half both roles stand on; the broadcaster half is
S0021, the descriptor-driven receiving half is S0020.

This product can be a **complete device** of an exchange account with nothing else on the phone, no producer
of resources and no local agent:
it opens TLS to a server, applies the trust rule, enrols with a login and a password (or a pairing code), keeps
the device token in the Keystore and never the password, holds its control stream, and lists and subscribes to
the account's directory. No UI beyond a debug harness: the user's surfaces are S0019. There is no feature gate
(the owner's ruling): the code is inert for the plain reason that **no account exists** - a product with no
server account behaves exactly as it does today (`DEVICE-EXCHANGE` 9.7).

## 2. Scope

1. **Inert without an account.** With no entry: no socket, no worker, no notification, no foreground service,
   no scheduled job, no network call at start-up. Nothing about the exchange is reachable by an intent from
   another app (no exported component is added by this ticket). A test starts the app graph with an empty
   entry store and asserts that nothing opened.
2. **Port the donor, never rewrite.** `DeviceExchangeEnvelope` and `DeviceExchangeFrameCodec` with their tests
   into `phone/src/main/java/com/sza/streamsplayer/data/remote/exchange/`, package
   `com.sza.fastmediasorter` -> `com.sza.streamsplayer`, sorter references cut, one ledger row each
   (`tools/port-ledger/record-port.ps1`). The two frame bounds follow state: 16384 until `welcome` or
   `enrolled`, 262144 after, both directions; a record above 16 KiB refused (7.1, 15 item T).
3. **Transport and trust.** TLS only: this code never opens a plain stream and never sends the `0x00` plain
   opening (7.1, 15 item V; `usesCleartextTraffic` stays for media, the exchange socket is explicit TLS).
   Trust rule of 14.3 item D: the platform trust store for the host name first (nothing recorded); a leaf that
   fails it is `NeedsDecision(leafFingerprint)` and is pinned only after the user has accepted its
   fingerprint; a pin that no longer matches is `Refused(expected, actual)` with both shown and nothing
   changed; a pin is replaced only by the user's explicit act. A `fp` from a connection string (S0017) is the
   anchor: the leaf must equal it, no prompt. There is no "trust everything" switch, and the only hostname
   verifier that ignores the name is the one that has just compared the DER leaf's SHA-256, written
   `SHA256:` plus base64 without padding, with a held pin or `fp`.
4. **Session.** `enroll` (`login`, `password` or `pairingCode`, `deviceId`, `deviceName`, `product`
   `streamsplayer-android`, `productVersion`, `platform` `android`, `roles` as built - `["receiver"]` now,
   `broadcaster` added the day S0021 lands - `receiver.modes` and `transports` and `plays` stating exactly what
   this build plays and nothing it does not implement, so RELAY and TUNNEL are declared only when S0020
   lands), `keepaliveSeconds` (14.5 item F), `enrolled` -> token to the
   secret store, `hello` on every later stream, `welcome` (`features` hide what is absent). Reasons of 7.10 map
   to localized messages in EN/RU/UK; an unknown reason is a generic refusal. `id-collision` and `capacity`
   have their own texts. **The password is a `CharArray` that lives from the field to the one `enrolled`
   exchange and is then overwritten**; it is in no Bundle, saved state, log, intent extra or diagnostic export.
5. **Control stream and presence.** One control stream per device record; presence is the stream (7.3);
   keepalive proposed, clamped and echoed. While the receiver-lifetime ask H of the entries proposal is open the
   stream is held only while an activity of the app is started or a playback service runs, and is closed
   otherwise.
6. **Directory.** `list`, `subscribe`, `changed` with the revision-gap rule, `bye`. Records read leniently:
   unknown members ignored, an unknown `kind` skipped or shown unsupported, a record above 16 KiB refused,
   a resource without `access` listed (15 item X). The records-v1 vectors of the catalog are vendored with a
   SHA-256 `PROVENANCE.txt`, as `user-playlist/` is, and replayed (rung 1).
7. **Entry and secret seams only.** `EntryStore` and `SecretStore` are interfaces here, implemented in S0018;
   tests use in-memory fakes. This ticket defines no persistence.
8. **Logs.** Probes `Timber.d("S0016: ..")` per the canon; an id (`deviceId`, `resourceId`, `broadcastId`,
   `shareId`) is logged as the first 8 hex of its SHA-256 (7.9). A test runs the whole enrol, hello, list
   exchange with canary strings for a password, a token and a pairing code, captures every `Timber`, `Log` and
   exception message, and asserts none contains a canary.
9. **Ask 2 as a test.** The session and directory code (`exchange.session`, `exchange.directory`) imports
   nothing of `broadcast`, no `MediaSession`, no Wear code: a dependency test fails the build if it does. The
   broadcaster adapter of S0021 lives in its own package and may depend on `broadcast`; the receiver never
   needs it. The session works with the roles `["receiver"]` and nothing else installed.
10. **One account.** The device holds one account entry (S0018), the server it publishes through and lists
    from. Any number of other servers are reached by descriptors, outsider `connect`, no `hello` (S0020), and
    create no entry.

Not in scope: any screen or string of the user's flow (S0019), more than one entry, persistence and Keystore
(S0018), the connection string (S0017), playing what is listed and cast (S0020), the Drive channel (gated on the
premise of 8.2, section 14.2 item B), the watch as a device of the account (S0022).

## 3. Constraints and dependencies

### 3.1 Constraints

- Handoff section 6a item 1 and `CLAUDE.md` section 2: the donor classes are ported with ledger rows; what the
  donor does not have yet (enrolment, trust, session) is written new to the contract text and is compared
  with FastMediaSorter's when its phase lands, through the shared vectors - a divergence is a ticket, not a
  quiet fork.
- The contract changes before the code (`RULES.md` 6). The portfolio owner ruled the proposals accepted and
  removed the feature gate, so this code is written to them now; the fold into the catalog (0.18, with
  `vectors/connection-string-v1.json`) is the contract owner's and **blocks the release** that carries this
  code (`VERSIONING.md` 6.4). What the owner changes at the fold is a ticket here, not a deviation.
- No new Android permission; `INTERNET` is declared already.
- The codec, the trust rule and the session are written with **no Android types** (the Keystore, the
  clock and the sockets sit behind seams), so the watch (S0022) can take the same code from a plain-JVM
  module instead of a second copy; where the core finally lives is S0022's owner input.

### 3.2 Related tickets

S0017 (codec; independent, can start first), S0018 (the entry and Keystore; implements the seams of item 7),
S0019 (surfaces), S0020 (descriptor-driven playback, cast receive), S0021 (broadcasting through the server),
S0006 (the broadcast side the receiver code must not depend on), S0013 (registry and pointers).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0017, S0018, S0019, S0020, S0021, S0006, S0013.
- **Role:** answered by the owner 2026-10-10 - receiver and broadcaster, one publishing server, any number of
  foreign servers through descriptors. Written into the entries proposal (revision of 2026-10-10) as ask A.
- **Gate:** answered 2026-10-10 - the proposal is taken as accepted; there is no gate.
- **Source map:** answered 2026-10-10 - the donor classes (and `ExchangeTunnelProxy` for S0020) are ported
  under the same law, with ledger rows and no edit to the handoff.
- **Presence:** answered 2026-10-10 - the control stream is held while an activity of the app is on screen,
  playback is running or a published broadcast is on air, and closed otherwise; no background service.
- **Server:** which running server this is exercised against and who may create the test account (the device
  run of the definition of done needs one).

## 4. Definition of done

- `.\gradlew.bat :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest --console=plain` exits 0 with
  the new tests counted: codec and frame bounds, trust rule (platform-valid leaf, self-signed leaf accepted,
  changed leaf refused, `fp` equal and different), enrol/hello/list against an in-JVM TLS fake server, reasons,
  redaction canaries, the dependency test, the records-v1 replay.
- `tools/port-ledger/check-drift.ps1` exits 0 with the two new rows in step.
- The merged manifest of a release build shows no new exported component and the app, started with no
  account, opens no socket (verified by `ExchangeInertTest` and merged manifest analysis).
- Device run against the running server when designated: enrol, list, close.
- The registry row of `DEVICE-EXCHANGE | StreamsPlayer Android` is updated with what was built and when.
