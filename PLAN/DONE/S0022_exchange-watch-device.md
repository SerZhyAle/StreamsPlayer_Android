# S0022 - The watch as a device of the account: pairing through the phone, its own broadcasts and its own token

**Status:** BlockNeedUserTest
**Status note:** Development complete (shared exchange-core, pairing, publishing, presence, leaving; 841 scoped tests green, R8 packages build, standalone emulator smoke passed). Only testing remains: paired phone/watch Data Layer hand-over and leave against the real server, physical-watch audibility and the battery note (FMS_A S4213 method).
**Phase:** device exchange, watch side - independent of the build phases of handoff section 8
**Verification level:** unit tests on the JVM; the small Wear emulator paired with the phone emulator for the hand-over; a physical watch for battery and audibility.

## 0. Source

- Owner, 2026-10-10 (this session): the watch is an exchange device.
- `DEVICE-EXCHANGE` 3 (the FastMediaSorter watch: broadcasts `AUDIO_ONLY` with the cap of 4, receives
  `AUDIO_ONLY`, enrols through the phone with a pairing code), 5.3 and 14.5 item J (pairing codes: 8 characters
  of Crockford base32, 10 minutes, single use, counted per login), 7.4 (`pairing-request`), 7.6 (the watch's
  cap holds across LAN, tunnel and relay, and its relay upload takes one LAN slot), 14.5 items M and R.
- `PROPOSAL-2026-10-10-streamsplayer-android-receiver-entries.md`: ask G (the watch's product value), ask H
  (presence lifetime), revisions of 2026-10-10.
- `LIVE-BROADCAST` 2.2 (the watch's audio broadcast, the cap of 4).
- Facts in this tree, 2026-10-10: the `wear` module serves `AUDIO_ONLY` on its own LAN server
  (`LiveAudioLanServer`, cap 4), builds descriptors with its own `BroadcastDescriptorSerializer`, and talks to
  the phone through the Data Layer under `/streamsplayer/`; the phone and the watch carry **separate copies**
  of the descriptor code (ported from FastMediaSorter, which does the same); no Keystore wrapper exists on
  either side.

## 1. Goal

A user who has signed the phone into their exchange server can, from the phone, make the watch a device of the
same account - with no password typed on the watch - and then broadcast the watch's microphone through the
server from any network and hear the account's audio broadcasts on the watch, under the cap of 4 listeners.

## 2. Scope

1. **Pairing hand-over.** On the phone: `pairing-request` on its control stream, `pairing-code` `{code,
   expiresAt}`; the code and the server's `x`, `fp` (when the string carried one) and the login go to the watch
   over the Data Layer; the phone shows what it is doing and for how long. On the watch: `enroll` with the
   login and the code, its own `deviceId` (16 random bytes), `product` `streamsplayer-wear`, `platform`
   `wearos`; `enrolled` -> its token to its own Keystore. The code is single use and never logged, shown only
   to the person who asked, and the phone never keeps it after the hand-over.
2. **Roles as built.** `broadcaster` (`AUDIO_ONLY`, `maxListeners` 4) and `receiver` (`AUDIO_ONLY`), declared
   with `receiver.modes`, `transports` and `plays` for exactly what the watch implements.
3. **Publishing.** The watch publishes its own broadcast record, uploads one relay stream (ADTS chunks from its
   encoder, the bounds of 7.6) and attaches tunnels to its own LAN server; its relay upload takes one of the 4
   slots, and `maxListeners` is updated as the LAN count changes (item M). Publishing is off by default and
   per broadcast, as on the phone (S0021).
4. **Presence.** The control stream is held only while the watch's broadcast or player screen is on, and closed
   otherwise (battery; the owner's answer on presence applies). No background service, no complication that
   wakes the radio.
5. **Listening.** The directory (`list`, `subscribe`) is read on the watch for audio broadcasts only; playing
   goes through the same endpoint order and trust rules as the phone (S0020); a descriptor is also handed
   over from the phone as today.
6. **Leaving.** Removing the account on the phone tells the watch over the Data Layer; the watch does `bye` and
   `revoke` of its own `deviceId`, forgets its token and returns to LAN-only. Never depends on the server
   being reachable; if the watch is out of reach, the phone revokes the watch's `deviceId` from its own session
   (any enrolled device may revoke another) and says so.
7. **Strings** EN/RU/UK on the watch and on the phone side of the hand-over; round-screen layouts; the
   existing watch input parity.
8. Probes `Timber.d("S0022: ..")`; the canary test of S0016 runs against the watch's session and the hand-over
   messages; neither a code nor a token appears in a Data Layer path.

Not in scope: video on the watch, the watch face, a tile that wakes the exchange, a watch that signs in on its
own without the phone.

## 3. Constraints and dependencies

### 3.1 Constraints

- Battery and the radio are the budget: no wake-up, no polling, no keepalive outside the screens of item 4.
- The watch's token never leaves the watch and is not synchronised through the Data Layer.
- The Data Layer carries the hand-over only: `x`, `fp`, login, a one-time code and an expiry; nothing durable.

### 3.2 Related tickets

S0016 (the client core), S0018 (the phone's entry), S0019 (where the phone offers "add the watch"), S0021
(the publish path whose watch half this is), S0007 (the watch app), S0020 (endpoint order and trust).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0016, S0018, S0019, S0020, S0021, S0007.
- **Resolved by the owner, 2026-10-23:** shared plain-JVM `:exchange-core`, consumed by phone and wear, with Android storage adapters in each application. Original choice rationale: the phone and the watch each held a separate copy of the broadcast
  descriptor code. Duplicating the exchange client core (codec, TLS and trust, session) would double the part
  most likely to drift. Recommended: the core is a plain-JVM Gradle module (`:exchange-core`, no Android
  types) consumed by `phone` and `wear`, which the law of section 2 of `CLAUDE.md` does not forbid (it forbids
  sharing with FastMediaSorter, not between this product's own modules). S0016 is written Android-free for
  this reason. The alternative is a copy per module, as the donor does.
- **Product value resolved:** `streamsplayer-wear`, `platform` `wearos`, fixed by the catalog owner at the
  DEVICE-EXCHANGE item BI fold.
- **Physical device:** a watch and a phone for the battery and audibility check.

## 4. Definition of done

- `.\gradlew.bat :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest :wear:testDebugUnitTest
  --console=plain` exits 0 with the tests: hand-over messages, code never in a path or a log, enrol on the
  watch, `maxListeners` arithmetic, leave sequences with the server up and down, presence lifetime.
- Emulator pair walkthrough of hand-over, broadcast and leave, recorded in `temp/S0022/`.
- A physical-watch note on battery draw over a fixed listen and a fixed broadcast time, with the method.
- The registry row of `DEVICE-EXCHANGE | StreamsPlayer Android` is updated.

## S4203 decisions 2026-10-14

S4203 is the release-46 device-exchange milestone of FastMediaSorter Android (FMS_A). The owner's answers of
2026-10-14 to its questionnaire are final; what reaches this ticket:

- **In release 46:** the watch's own device record on the account and its audio broadcast through the server
  (Р’25.3). FMS_A S4217 writes it into the roles table and fixes the watch's `product` value at the fold, which
  answers the owner input "Product value" of section 3.3.
- **The list on the watch.** The account's broadcast list on the watch - shown on open, the broadcaster picked by
  the person - is S0028; this ticket keeps the pairing hand-over, the roles, publishing, presence, the directory
  read it builds on, and leaving. S0028 waits for this ticket.
- **Password change.** Changing the account's password signs out every device, the watch included (S0027): the
  watch returns to "sign in through the phone" and re-enrols with a fresh pairing code; no password is ever
  typed on the watch.
- **Encryption.** The watch's audio broadcast and its listening gain the end-to-end layer of S0032.
- **Battery.** Joint run #1 (FMS_A S4213) measures the FastMediaSorter watch's battery during an audio broadcast
  and sets a limit of broadcast minutes or records that none is needed; the physical-watch note of this
  ticket's definition of done uses the same method, so the two watches answer alike.
- **Core location resolved, 2026-10-23:** owner selected shared JVM `:exchange-core` in response to the section 3.3 question.

## Implementation and acceptance record - 2026-10-23

The owner selected shared plain-JVM `:exchange-core`. Existing Android-free session/TLS/trust,
directory, relay/tunnel, descriptor and endpoint algorithms were extracted without sharing code with
FastMediaSorter. Android Keystore, preference and UI adapters remain in each application. The donor is
read-only; ledger destinations follow moved sources and retain their original provenance hashes.
`streamsplayer-wear` is fixed by DEVICE-EXCHANGE item BI, not an unresolved product decision.

### Scope coverage

| Scope | Implementation and evidence | Remaining acceptance |
| --- | --- | --- |
| 1. Pairing | Phone account Connect watch action; companion/node check before requesting a code; correlated ACK with bounded 45-second wait; transient payload bytes cleared. Watch version/expiry validation, pinned TLS enrollment and own 16-byte identity/token. Payloads, pairing client, handover use case, watch receiver/store/account and canary tests pass. Real Keystore enrollment passes on Wear emulator against local TLS fixture. | Actual paired phone/watch Data Layer handover. |
| 2. Roles | Watch declares broadcaster/receiver, streamsplayer-wear/wearos and only AUDIO_ONLY HTTP/RELAY/TUNNEL plays. Declaration asserted by tests and local server. | Production server/cross-product joint run belongs S0034. |
| 3. Publication | Shared publisher, bounded ADTS relay from the watch LAN source, tunnel attachment, human listener accounting and finite cap floor. Relay takes one local slot; not advertised until ready. Per-broadcast toggle defaults off. Own Wi-Fi/cellular transport; cellular uses a loopback-only source and advertises no fake LAN endpoint. Emulator encoder sent AAC-LC mono 44100 Hz; ffprobe accepted capture. | Real server fan-out/tunnel walkthrough and physical audibility. |
| 4. Presence | Activity resumed + allowed broadcast/QR/audio-player route + interactive display. No background holder for watch exchange. Cancellation closes in-flight/held sockets, upload and proxies; existing local microphone service is retained. JVM lifecycle tests and emulator Home/display-off teardown pass. Stop remains above server settings on the small round screen. | Physical battery measurement. |
| 5. Receiving | Audio-filtered list/subscribe/revision watcher exposed to S0028; shared endpoint ordering/trust and relay/tunnel proxies; encrypted descriptor handoff and player candidate/network fallback. Certificate changes fail closed. | Directory UI is S0028; protected/E2E watch broadcasts are S0032 and refused in this phase. Actual paired descriptor playback remains manual. |
| 6. Leaving | Correlated device-specific leave prevents a stale message removing a new account. Watch ends publication, closes control, best-effort revokes and erases locally. Phone fallback revokes acknowledged watch ids before erasing its own token, with a localized result. Tests cover reachable/unreachable cases. Local TLS fixture saw watch and phone revokes; Android instrumentation confirms watch token/entry erased. | Paired leave delivery and absent-watch return walkthrough. |
| 7. UI | Account action/status, round Wear toggle/status and privacy notice. New phone 12 and watch 7 keys have identical EN/RU/UK sets. Small-round emulator walkthrough and screenshots recorded. | Physical input/audibility acceptance. |
| 8. Secrecy | Canary covers enrollment/control/handover, encrypted prefs and constant paths; no token synchronization. Redacted payload types, core log bridge and R8 DTO rules. No S0022 probes while ticket is Partial; add acceptance probes when entering BlockNeedUserTest, per probe lifecycle rule. | Paired-device trace. |

### Verification

Evidence: `temp/S0022/` (ignored). Final source verification:

```powershell
.\gradlew.bat :exchange-core:test :phone:testStoreDebugUnitTest --tests '*data.exchange.publish.*' --tests '*DeviceExchange*' --tests '*BroadcastPlaybackResolverTest' --tests '*ExchangeTunnelProxyTest' --tests '*ExchangeAsk2DependencyTest' --tests '*ExchangeInertTest' --tests '*WatchExchangeLeaveCoordinatorTest' --tests '*ConnectWatchToExchangeEntryUseCaseTest' --tests '*WearExchangeHandoverCodecTest' --tests '*ui.exchange.*' :phone:testNoLegalDebugUnitTest --tests '*data.exchange.publish.*' --tests '*DeviceExchange*' --tests '*BroadcastPlaybackResolverTest' --tests '*ExchangeTunnelProxyTest' --tests '*ExchangeAsk2DependencyTest' --tests '*ExchangeInertTest' --tests '*WatchExchangeLeaveCoordinatorTest' --tests '*ConnectWatchToExchangeEntryUseCaseTest' --tests '*WearExchangeHandoverCodecTest' --tests '*ui.exchange.*' :wear:testStoreDebugUnitTest :wear:testNoLegalDebugUnitTest --console=plain
```

Exit 0, BUILD SUCCESSFUL in 3m 26s (`final-tests.log`): core 17, phone 146 per flavor,
watch 266 per flavor - **841 tests, zero failures/errors**. The original DoD task
`:wear:testDebugUnitTest` is not a flavored task here; both actual watch tasks are above.

Full unfiltered command (`full-tests.log`), before the final focused additions:

```powershell
.\gradlew.bat :exchange-core:test :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest :wear:testStoreDebugUnitTest :wear:testNoLegalDebugUnitTest --continue --console=plain
```

Exit 1, BUILD FAILED in 3m 49s: 2535 tests, four failing instances, exactly the two previously
recorded phone failures per flavor: `ExchangeSignInOnlyTest.firstSignInStoresTokenUnderTheEnrolledDeviceAndNeverRegisters`
(timeout) and `KeystoreBroadcastPasswordStoreTest.password is encrypted absent after key loss and removed with the channel`
(FileStorage IOException). These remain tracked outside S0022; the full suite is not claimed green.
`full-test-summary.txt` preserves those results before filtered XML replacement.

Package command (`packages.log`):

```powershell
.\gradlew.bat :phone:assembleStoreDebug :wear:assembleStoreDebug :wear:assembleStoreDebugAndroidTest :phone:assembleStoreRelease :wear:assembleStoreRelease --console=plain
```

Exit 0, BUILD SUCCESSFUL in 6m 6s; includes both R8 release packages and Wear test APK.
The final Wear action-order adjustment is separately rebuilt/retested in `final-layout.log`:
`:wear:testStoreDebugUnitTest :wear:testNoLegalDebugUnitTest :wear:assembleStoreDebug :wear:assembleStoreRelease --console=plain`,
exit 0, BUILD SUCCESSFUL in 4m 28s. Final no-account UI starts LAN-only and reports the emulator's lack
of Wi-Fi with an actionable caption (`watch-final-layout.xml`, `watch-final-no-wifi.xml` / `.png`).

`python temp/S0022/final-evidence.py` exits 0: checks the 841 test XML results, current hashes and
destinations of 21 dated port rows, moved core destinations, EN/RU/UK key parity, fixture assertions
and the absence of a fatal exception in captured device runtime logs. An Android-free core source
guard is among the core tests. Earlier intermediate compile/fixture failures are retained as diagnostic
history, not successful evidence.

Harness `spec_catalog/validate` exits 0 (`catalog-validation.log`): 8 OK, 1 existing missing-spec
hygiene warning, 0 FAIL. `spec_catalog/check-probe-absent -Id S0022` exits 0; no ticket probe remains
outside the needs-user-test lifecycle.

### On-device evidence and limits

- Phone emulator-5554 updated preserving data; disposable Small Round Wear emulator-5556 installed.
- Actual Hilt graph, TLS connector, watch Keystore and repository exercised by
  `WatchExchangeDeviceSmokeTest#enroll` / `#remove` with `localFakeServer=true`;
  `device-enroll.log` and `device-remove.log` each say OK (1 test).
- `fake-server-events.jsonl`: final run enrolls streamsplayer-wear, publishes AUDIO_ONLY with cap 4,
  opens relay upload, receives 876 ADTS chunks, sees bye/closed on Home; resume starts another
  upload, 656 chunks, bye/closed on display-off. Both devices later revoke successfully.
  The pre-restart TypeError was a fixed fixture logger error; final-run assertions exclude it explicitly.
- `watch-relay.aac` contains the first 120 encoder frames; ffprobe: AAC, 44100 Hz, one channel.
  This proves framing/encoding, not physical audibility. `watch-onair.png` records the small-round UI.
- Phone account UI Connect watch without a paired watch shows an actionable refusal before
  pairing-request; `phone-watch-connect.xml` / `.png`. The emulator has no configured Wear companion;
  no Google sign-in/pairing was attempted. This is not paired Data Layer evidence.
- Disposable accounts erased/revoked, app processes stopped, adb reverses removed and local fixture
  servers stopped. No real credentials were used or logged.

### Documentation and registry

README, changelog, privacy policy, CLAUDE module layout and the DEVICE-EXCHANGE pointer updated.
No website is declared as an owned publication surface; the existing shared portal is not modified.
The exact own-row registry proposal and reason are in `temp/S0022/registry-proposal.diff` and
`registry-proposal-reason.txt` for the canon session under CLAUDE section 6; the catalog is not mutated.
No full DEVICE-EXCHANGE 1.7 conformance is claimed: later producer-state/verifier requirements,
S0032 E2E and the joint run remain explicit debt.

## Last Audit

**Date:** 2026-10-23 | **Mode:** implementation + JVM/package + standalone emulator | **Outcome:** Partial
**Counts:** PASS 6 | WARN 2 | FAIL 0 | MANUAL 2 | UNCHECKABLE 0 | EXEMPT 0

**Re-read 2026-10-27 (pre-handoff, static; no new build or test run):** Outcome BlockNeedUserTest. WARN 7 is closed (the full
phone suites are green in both variants since 2026-10-25); WARN 8 is the canon session's registry application. One acceptance
probe, `S0022: watch enroll started`, sits at the entry of `WearExchangeAccountRepository.enroll` so the paired-device run can be
told from a run that never reached the new code. Only the two MANUAL items below are left.

1. PASS: owner architecture decision implemented with one Android-free shared core and application adapters.
2. PASS: pairing, role, presence, cap, leaving, receiver/trust and secrecy gates pass in the final 841-test scope.
3. PASS: phone/watch debug and R8 release packages build; watch test APK builds.
4. PASS: standalone watch TLS/Keystore enrollment, encoder relay, Home/display-off teardown and revoke/erase pass.
5. PASS: EN/RU/UK parity, donor provenance and moved destinations checked; user-facing docs synchronized.
6. PASS: phone no-connected-watch behavior is exercised without issuing a code.
7. WARN: full phone unit suites retain two tracked failures per flavor; full green DoD is not met.
8. WARN: registry proposal awaits canon-session application. Later DEVICE-EXCHANGE amendments remain scoped debt.

- [ ] MANUAL: paired phone/watch handover, descriptor playback, broadcast and leave against the real server;
  verify both reachable and absent-watch fallback and reconnect after revoke. Record Data Layer and server evidence.
- [ ] MANUAL: physical-watch audibility and fixed-duration listen/broadcast battery note using the FMS_A S4213
  joint-run method (same initial charge, duration, network, screen/brightness and baseline); report charge delta
  and interruptions. No synthetic battery estimate is substituted.

The code paths are wired, but the complete headline paired-device behavior has not been demonstrated.
S0022 remains Partial; neither Implemented nor Verified nor a release-readiness claim is made.
