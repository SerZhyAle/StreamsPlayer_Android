# S0032 - End-to-end encryption of broadcasts, sending and receiving, on phone and watch

**Status:** BlockByOtherTask
**Status note:** Blocker: S0028 - the phone's link, QR, file and watch hand-over carry the sealed keys only once S0028 phases D (link password shown) and F (watch takes a protected hand-over) exist; also open: item DP no-clear-fallback and keys-at-keep wait for FastMediaSorter parity, and the remaining test list (open items 2-4).
**Phase:** device exchange, release-46 milestone (FastMediaSorter Android S4203) - independent of the build phases of handoff section 8; starts after joint run #1
**Verification level:** unit tests on the JVM against the contract's encryption vectors; a fake-server test that proves the server sees no content; the final joint run of FastMediaSorter Android S4244 (S0034).
**Complexity:** complex - confirmed 2026-10-29 at the queue pass: a shared-contract boundary (`DEVICE-EXCHANGE` section 27), the shared `exchange-core` publisher and directory observer, DI on two devices, list and account UI on the phone and the watch, and a port from FastMediaSorter's `ExchangeSealedContentOpener`.

## Remaining development

*Rewritten 2026-10-29 at the unattended queue pass, from a static read of the tree and the edits of that pass; nothing was built or run (Android has no measured fast rung, CLAUDE.md section 5a). Built before that pass: the E2E primitives and vectors in `exchange-core` (`data/security/e2e/`), the sealed relay upload for audio and MPEG-TS video, the pinned tunnel, `SealedRelayProxy`, the device-key stores, the server-without-`sealed` refusal (`SERVER_NO_E2E`), the publish notice in EN/RU/UK, section 6 of the privacy policy, the site pages and the Data safety source.*

*Done at the 2026-10-29 pass (not built, not tested until the release):*

- *Scope 1, fail closed: a sealed record lists no `TUNNEL` without a tunnel identity and no `RELAY` without a relay key (`BroadcastEndpointsBuilder.build(tunnel=)`, `BroadcastExchangePublisher.buildRecord`); the relay upload does not start without a sealer; `BroadcastTunnelAcceptor(sealed=)` refuses to bridge a sealed broadcast in clear. Test: `DeviceExchangeE2eFakeServerTest` "sealed broadcast without a tunnel identity lists no TUNNEL endpoint".*
- *Scope 2, receiving: the broadcast half of FastMediaSorter's `ExchangeSealedContentOpener` ported to `exchange-core` as `ExchangeBroadcastOpener` (own-account box, grant box with first-sender trust, registering box hidden), run for the phone and the watch by `SealedDirectoryOpener` inside the shared `AccountBroadcastDirectory` before any screen sees a directory; the opened keys stay in memory on the records. The phone's `cast` offer opens its box too (`CastReceiverHandler.openSealed`). The watch's unused `WearSealing.openBroadcast` and `WearExchangeE2e.trustedSealing` were deleted. Test: `ExchangeBroadcastOpenerTest` (5 cases).*
- *Scope 2, one clear message: `DeviceExchangeBroadcastRecord.sealIssue` (`ExchangeSealIssue`, ported); the phone's directory row is not playable and shows the `sealed-failed` / `e2e-missing` / `key-changed` text, the phone's Own list marks it not playable; the watch's Own list shows `WearLiveLimit.SEALED_FAILED` / `E2E_MISSING` and the matching notice, with the existing EN/RU/UK strings.*
- *Scope 3, key trust: `ExchangeE2eTrust` (exchange-core), implemented by the phone's `ExchangeE2eTrustStore` and the watch's `WearExchangeE2eTrust` (grant senders added to the watch store); the directory opener and the publisher learn first-seen keys and drop removed devices' keys, so a replaced key is `key-changed` and sealed nothing.*
- *Scope 5: the account screen shows `exchange_server_honest_text` (`tvServerHonest`); `ExchangeHonestTextTest` checks the three locales against item DE word for word and the layout reference.*
- *Item 9 settled: Hilt injects `ExchangeRelayKeys`, `BroadcastTunnelIdentityProvider` and `DeviceExchangeE2eKeyStore` on both devices (bound in `ExchangeModule` and `WearExchangeModule`), so sealing is not switched off by a null; LAN endpoints carry no `seal` and play as before (item DK). The release sweep's `AllUnitTests` step runs `:exchange-core:test`, so the core E2E tests run at release.*

*Done at the second 2026-10-29 pass (unattended, run-spec-queue pq1; not built, not tested until the release):*

- *Item 2, watch key at rest (item DF): `EncryptedWearExchangeE2eStore` takes the watch's `AndroidKeyStoreCipher` (bound in `WearExchangeModule`) and keeps the private key encrypted under `exchange_e2e_key_v2/`; a clear `exchange_e2e_key_v1/` key of an older build is re-written encrypted on first read and removed, so the watch keeps its `deviceId`; a refused re-write keeps the clear key for the next read. Test: `EncryptedWearExchangeE2eStoreTest` (6 cases, `wear/src/test`).*
- *Item 1, own key on a member change (item DL): `BroadcastExchangePublisher.buildRecord` calls `ExchangeRelayKeys.membersChanged` with the box's recipients, as the donor's `SftpExchangeProducerProvider` does, so a device that left takes the own group key with it. For that to work, `BroadcastRelayUploader` now draws a new epoch for every upload stream (`sealers`, a factory, as the donor's `ExchangeRelayUploader` does) instead of one sealer for the uploader's life, and `ExchangeRelaySealer.EpochReplaced` ends the stream without counting as a wire failure, so the replacement upload opens the next epoch at once. Test: `ExchangeRelayKeysTest` "a device that left replaces the own key and its stream gives way to a new epoch".*
- *Item 1, the watch's answer key (item DG): the watch's hand-over answer carries `e2eKem` (`WearExchangeHandoverReceiver`, from its `DeviceExchangeE2eKeyStore`), and the phone pins it with `ExchangeE2eTrustStore.pin` (`ConnectWatchToExchangeEntryUseCase.trustWatchKey`, ported from the donor's `trustHandedOver`); a value that is not a P-256 point pins nothing. Tests: `WearExchangeHandoverReceiverTest` (the answer names the key), `ConnectWatchToExchangeEntryUseCaseTest` (2 new cases).*
- *Item 4 was built by S0028 plan phase C (`BroadcastDescriptorDto.credentials`, the opener copies the box's pair). Item 7, item EI: `BroadcastCredentialsDto.user` also reads `username`. Test: `ExchangeBroadcastOpenerTest` "a pair written username is read as the user (item EI)".*
- *Item 6, vendored vectors: `exchange-core/src/test/resources/device-exchange/PROVENANCE.txt` names `e2e-v1.json` with its hash at 1.11, and `tools/contracts/check-sync.ps1` compares its bytes with the catalog's and checks the row.*

*Still open:*

1. **The phone's hand-out surfaces carry the keys (scope 3, item DK) - waits for S0028.** The share link, QR and file of `BroadcastControlManager` / `BroadcastShareManager` and "send to watch" hand over `live.descriptor`, the LAN descriptor, so no surface hands out an exchange endpoint at all; `outwardDescriptor` and `ownDeviceDescriptor` keep no caller outside tests. The donor shares the announced descriptor with its keys (`ExchangeBroadcastAnnouncer.shareDescriptor`, `BuildWatchBroadcastHandoffUseCase`). Since S0028 phase C an announced broadcast is protected by a generated password that no screen shows before S0028 phase D, and the watch takes a protected hand-over only after phase F, so a link holder or the watch handed the sealed endpoints today could open nothing. After S0028 D and F: build the outward descriptor from the published endpoints (`published`) through `outwardDescriptor` for the link, QR and file, and through `ownDeviceDescriptor` with the own pair as `pass` for the watch; the contract's QR protection; the password path of `SealedRelayProxy` (`authorization`) for a protected sealed link. `seedLink` and `dropLink` keep no caller because this product has no prepared link (item DW) and no guest grant; they stay as ported.
2. **No clear fallback through the server (scope 6, item DP) - FastMediaSorter first.** An unsealed `RELAY` or `TUNNEL` endpoint is still played, as the donor does (`BroadcastExchangeEndpoint`: `seal == null` plays). Parity law (section 6a): the donor's gap goes to the canon session / FastMediaSorter first, then both products refuse unsealed exchange endpoints together.
3. **Keys at keep time (scope 3) - FastMediaSorter's decision.** Keeping or playing an opened directory broadcast passes the descriptor with its keys to `ImportStreamBroadcastUseCase` and `BroadcastDescriptorStore`, which persist it in clear preferences, as the donor does; scope 3 says no key in a saved state. Decided with the donor whether the opened keys are stripped before saving.
4. **Tests and probes (scope 7, definition of done).** A fake-server run through a real tunnel identity that records every byte (the current one covers relay only); a canary that captures the real relay key, epoch key and tunnel pin; key cases in the S0016 canary tests; mixed-version tests; watch sealing tests in `wear/src/test`; a phone test of `SealedDirectoryOpener` through `AccountBroadcastDirectory`; an unknown-epoch reconnect probe.
5. **Contract 1.11 pointer.** Items EH and EI are in the code; the pointer still reads 1.10. Parked with the other stale pointers as S0057.

Testing only (not development): the live delay with encryption against `targetLatencyMs` per transport and mode; the final joint run of FMS_A S4244 (S0034, `BlockExternal`); interop of sealed relay and tunnel with StreamsPlayer (Windows) and the FastMediaSorter watch; physical-watch Keystore, audibility and battery with sealing on.

## 0. Source

- Owner, 2026-10-14: answers to the questionnaire of FastMediaSorter Android (FMS_A) milestone S4203, final:
  - End-to-end encryption of content - files, broadcasts, clipboard - so that the host of the exchange server
    cannot see it; metadata (account, device, folder and broadcast names, presence) stays visible to the server,
    and the programs say so honestly.
  - Order: what is built is verified first on the current transport (joint run #1, FMS_A S4213); encryption is
    a separate layer; the final joint run comes after encryption (FMS_A S4244).
  - For this product only broadcasts apply: it serves no folder and takes no part in the server clipboard.
- FMS_A S4239 (`DEVICE-EXCHANGE`: end-to-end content encryption) - the text this ticket is written to. The same
  layer elsewhere: FMS_A S4240 (phone and FastMediaSorter watch), FMS_W 234, StreamsPlayer (Windows)
  StreamsPlayer SP-0226.
- Facts in this tree, 2026-10-14: the relay upload (S0021) sends ADTS AAC chunks and the tunnel bridges the
  local HTTP or RTSP server; the receiver (S0020) plays relay and tunnel endpoints through loopback proxies; the
  phone's publish notice and `docs/PRIVACY_POLICY.md` say the server sees the broadcast's content while it
  passes it on.

## 1. Goal

A broadcast that goes through the exchange server, from this phone or this watch, can be heard or seen only by
the receivers it is meant for: the server passes it on without being able to read it, and the person is told
plainly what the server still sees.

## 2. Scope

1. **Sending.** The phone's audio and video and the watch's audio, through relay and tunnel, encrypted as S4239
   defines (keys, framing, how a joinable chunk and a header chunk stay joinable), as a layer between the
   encoder or local server and the existing upload and bridge, so that the S0021 and S0022 transports keep their
   shape.
2. **Receiving.** The phone and the watch decrypt in the loopback proxy of S0020 before the player; a broadcast
   the receiver holds no key for is one clear message, never a broken stream.
3. **Keys.** Distributed to the account's own devices and to the holder of a link with a password (S0028)
   exactly as S4239 says; kept in the Keystore; never in a log, a saved state, a QR without the contract's
   protection, or the copied diagnosis (S0031).
4. **LAN.** Whether a direct LAN stream is encrypted too is the contract's; this ticket implements what S4239
   says and nothing beyond it.
5. **Honest words.** The publish notice, the account screen and the privacy page say that the server cannot see
   the content and does see the account, the device, the broadcast's name and that it is live; the privacy page
   and the Data safety answers change in the same edit (canon invariant 14, S0033's shape).
6. **Compatibility.** A receiver or broadcaster of the family without encryption meets what S4239 prescribes for
   mixed versions; this product never silently falls back to clear content.
7. Probes `Timber.d("S0032: ..")`; no key material in a log; the canary test of S0016 extended to keys.

Not in scope: folders and the clipboard (this product has neither), encryption of what the server must read
(metadata), P2P (S0024).

## 3. Constraints and dependencies

### 3.1 Constraints

- Written to the text FMS_A S4239 folds and replayed against its vectors; not started before that fold and not
  before joint run #1 (FMS_A S4213) has recorded this product's results on the current transport (S0034).
- No home-grown cryptography: the primitives are the platform's, chosen by the contract.
- The live rules of `LIVE-BROADCAST` hold through the layer: no added delay beyond what S4239 states, a joinable
  chunk stays joinable, rejoin at the live edge.
- Handoff section 6a: where FastMediaSorter's phone (FMS_A S4240) builds this layer first in a portable form,
  it is ported with a ledger row, not written twice.

### 3.2 Related tickets

S0020 (receiving), S0021 (phone sending), S0022 (watch sending and receiving), S0028 (link holders), S0030
(words), S0031 (diagnosis), S0033 (privacy and Data safety), S0034 (joint runs).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0020, S0021, S0022, S0028, S0030, S0031, S0033, S0034.
- **Encryption rules and order:** answered 2026-10-14 (FMS_A S4203 questionnaire) - section 0.

## 4. Definition of done

- Between releases inspect and directly exercise the changed area where possible; Android has no measured fast
  Gradle test. RELEASE: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` runs phone and wear suites with
  the tests: the contract's encryption vectors replayed, a fake server that
  records every byte it relays and finds no clear content, no key in a log, mixed-version behaviour.
- The live delay with encryption measured against `targetLatencyMs` per transport and mode in the
  `LIVE-BROADCAST` section 6 form.
- The final joint run of FMS_A S4244 passes with encryption on, recorded by S0034.
- The privacy page, the Data safety answers and the publish notice say what the server sees and does not see.

## Last Audit

**Date:** 26-10-29 | **Mode:** static | **Outcome:** Partial (development remains; status BlockByOtherTask on S0028)
**Counts:** PASS 15 | WARN 1 | FAIL 4 | MANUAL 4 | UNCHECKABLE 0 | EXEMPT 1 | RELEASE 2

PASS, each by a fresh grep of a declaration or call line (1 hit each, expected 1): `exchange_e2e_key_v2/` in the watch store; `EncryptedWearExchangeE2eStore(.., cipher)` in `WearExchangeModule`; `relayKeys?.membersChanged(id, recipients` in `buildRecord`; `sealers = sealers` in the publisher; `val sealer = sealers?.let` and `catch (e: ExchangeRelaySealer.EpochReplaced)` in the uploader; `class EpochReplaced : IOException` in the sealer; `e2eKem = kem` in `WearExchangeHandoverReceiver`; `e2eTrust.pin(entry.deviceId, deviceId, kem)` in `ConnectWatchToExchangeEntryUseCase`; `alternate = ["username"]` on `BroadcastCredentialsDto.user`. The old single-sealer parameter: 0 hits (expected 0). `S0032: ` probes in main source: 0 (expected 0 outside BlockNeedUserTest; the five lines of the first pass keep their text without the id). `e2e-v1.json` SHA-256 equal in the tree and the catalog (`5391a9ae..`). `check-sync.ps1 -CatalogRoot P:\Contracts`: its new block printed `PASS: 1 DEVICE-EXCHANGE E2E vector matches current catalog bytes and its provenance row` in 0.43 s. `assert-help-map.ps1` exit 0 `PASS (30 surfaces, 29 rows ..)` in 0.674 s; `check-syntax.ps1` exit 0 `release-syntax: PASS` in 0.257 s.

WARN: `check-sync.ps1` exits 1 on 29 findings outside this diff (16 pointers and 11 registry cells behind the catalog, the item CZ table) - known red, parked as S0057 (CZ already S0053 item 3).

### Action items
1. Open item 1 (hand-out surfaces carry the keys) - after S0028 phases D and F.
2. Open item 2 (no clear fallback, item DP) - FastMediaSorter / canon session first, then both products.
3. Open item 3 (opened keys persisted at keep and play) - FastMediaSorter's decision.
4. Open item 4 (tests and probes) - the list above.

RELEASE: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` compiles phone, wear and exchange-core and runs `EncryptedWearExchangeE2eStoreTest`, `ExchangeRelayKeysTest`, `ExchangeBroadcastOpenerTest`, `WearExchangeHandoverReceiverTest`, `ConnectWatchToExchangeEntryUseCaseTest` and the earlier `DeviceExchangeE2eFakeServerTest` and `ExchangeHonestTextTest`; not built, not tested until the release. `capture-help-screenshots.ps1 -Serial <emulator>` re-takes the exchange picture set so the `server-sees` marker is drawn.

EXEMPT: LAN endpoints carry no `seal` (item DK).

### Manual / on-target
- [ ] A sealed broadcast from one phone or watch plays on another device of the account through relay and tunnel; a device without its own key shows the `e2e-missing` line; a device removed from the account stops opening the next epoch.
- [ ] The live delay with encryption against `targetLatencyMs` per transport and mode (`LIVE-BROADCAST` section 6 form).
- [ ] The final joint run of FMS_A S4244 with encryption on (S0034).
- [ ] A physical watch: Keystore (an upgraded watch keeps its `deviceId` and key), audibility and battery with sealing on.
