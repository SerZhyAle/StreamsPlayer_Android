# S0053 - Broadcast end, pending link and tunnel ended: LIVE-BROADCAST 1.4-1.6 and DEVICE-EXCHANGE 1.8-1.10 gaps

**Ticket:** S0053
**Status:** BlockExternal
**Status note:** Required research awaits the catalog owner answer to S0037 R7 on missing descriptor defaults, and a FastMediaSorter Android watch pending-receiver port with tests or an authorized parity decision; see section 3.
**Complexity:** hard - confirmed at the spec pass on 2026-10-29: the work spans phone and watch, shared wire readers and producer ordering, lifecycle-bound network polling, localized UI, and a joint server run.
**Date:** 2026-10-27
**Phase:** device exchange and broadcast - independent of the build phases of handoff section 8
**Verification level:** gate - each item held by a unit test against the folded contract text and, for the wire, a joint run with the exchange server (S0034's stand).
**Owner:** the StreamsPlayer Android session of this repository.

## Captured request and migration scope

The S0037 audit (`PLAN/S0037_contract-sync-20261017/audit.md`, sections 6 and 9) read `LIVE-BROADCAST` 1.4-1.6 and `DEVICE-EXCHANGE` 1.8-1.10 against the tree. This ticket was filed 2026-10-27 as remediation for items S, T, U, DX and DY, with temporary-debt exceptions until 2026-12-31. It complements S0043 (receiver end state), S0044 (descriptor shape log), S0042 (protected directory box), S0028 and S0032; read their scope first, do not duplicate.

## 1. Work

1. **Producer order (items T, DX, MUST).** On a deliberate stop write `broadcast-end` before any stream of the broadcast closes, and never hold the stop for a missing control stream: phone audio (`BroadcastCaptureService.kt:357-359`), phone video (`VideoBroadcastService.kt:567-568`), watch (`VoiceRecordingService.kt:212-214`, `WearExchangeRuntime.kt:78-79`); inside `BroadcastExchangePublisher.endAndAwait` post `broadcast-end` before closing upload and tunnels. Test the order with a fake control stream.
2. **Sign-out is a deliberate end (item DX).** The phone's `ExchangeAccountManager.removeAccountEntry` ends the announcing broadcast first (the watch already does).
3. **Item DY text and reason.** Add the `ended` reason (`DeviceExchangeEnvelope.kt:85-96`), its EN / RU / UK message through the one table of messages, and re-vendor the item CZ table with its provenance; S0043 owns the receiver behaviour that uses it. Coordinate: the vendored `messages-item-cz.md` also carries three rows (`e2e-missing`, `server-no-e2e`, `sealed-failed`) added by S0032 that `check-sync.ps1` does not account for (it cuts only the first table); extend the check, do not hand-edit the vendored bytes.
4. **Pending descriptor (item S, receiver).** Parse `pending {until}`, keep it through the descriptor stores (`BroadcastDescriptorStore.kt:22`, `WearBroadcastDescriptorStore.kt:26` rebuild from the DTO), show waiting instead of failure, probe the relay status at most every 30 s while the screen is visible, stop at `until` with "link no longer valid". Playback stays the person's action.
5. **Compatibility law fixes** (audit section 9, answers 2, 3, 7): a descriptor with one unknown-shape endpoint keeps its known endpoints; check the version before the shape and show the `format-unsupported` text of the amendment of 2026-10-17 (once, not the generic toast); a different envelope `schemaVersion` gets a message; read `keyframeIntervalMs`; ask the catalog where the RTSP 200 ms and HTTP-audio defaults are written (recommendation R7).
6. **Not decided here:** preparation before the start (item DW, MAY) needs an owner decision; RTCP BYE and the closed port of the RTSP server depend on the library (RootEncoder) and need reading and a device.

## 2. Acceptance

- [ ] Tests for each item against the contract text at the version current when worked; real negative cases (a control stream that never answers, a refused `ended`, a malformed endpoint).
- [ ] A joint run with the exchange server before the wire claims move; none is implied by unit tests.
- [ ] The `LIVE-BROADCAST` and `DEVICE-EXCHANGE` rows advance only to what ran; no release, tag, upload or deployment is implied.

## 3. Spec pass and required research, 2026-10-29

This is a complex ticket: it changes shared-contract behaviour across several modules, requires new UI and
port work, and has unresolved research. The strategic scope remains the five items above; item DW producer
preparation is outside this ticket until the owner decides it. The existing contract amendments precede this
work: `LIVE-BROADCAST` 1.4-1.6 and `DEVICE-EXCHANGE` 1.8-1.10 govern the reader and producer changes. No new
wire shape is proposed here. The catalog owns its own amendments and registry; this session does not edit it.

- **Resolved - pending receiver contract:** `LIVE-BROADCAST` item S requires a visible waiting state, HTTP GET
  of the relay status no more frequently than every 30 seconds only while that state is visible, closure after
  the status line, 404 as not started, 503 as started without upload, 200 or 401 as started, and expiry at
  `until`. Playback remains the person's action unless their own setting says otherwise.
- **Resolved - phone donor:** FastMediaSorter Android S4247's receiver phase uses a visible waiting dialog and
  offers Play when started; its `WatchBroadcastStartUseCase` and tests supply a phone port candidate. The
  donor ticket remains `BlockNeedUserTest`; its two-phone live run has not been established here.
- **Required - watch donor and placement:** FastMediaSorter Android S4247's receiver phase describes phone UI;
  searches of its `wear/src` found no `BroadcastPendingDto`, `WatchBroadcastStartUseCase`, or corresponding
  waiting/expired string keys. The project's standing full-port rule requires an available source behaviour and
  tests or a separately authorized parity decision before the watch receiver is designed. Map the watch surface
  after that source exists; do not infer a placement from the phone dialog.
- **Required - catalog answer R7:** `PLAN/S0037_contract-sync-20261017/proposals.md` asks the catalog owner for
  missing-member defaults for RTSP and HTTP audio latency, endpoint transport and mode, and the consumer's use of
  `keyframeIntervalMs`. `LIVE-BROADCAST` section 2.5 states 200 ms for an emitted RTSP descriptor; it does not
  establish the receiver default when the member is absent. Item L already states absent
  `keyframeIntervalMs` means 2000 ms. An authoritative answer to the remaining defaults is still required;
  no local fallback is promoted to a contract decision.
- **Evidence still required:** tests for the folded text and negative cases, and the server joint run named in
  acceptance. The live stand requires the S0034 test server credentials and devices; it is not evidence from
  this spec pass. RTCP BYE and port closure also require library research and a device, as captured above.
- **Documentation context:** this blocked spec pass changes no registered user-facing document or surface.
  `docs/README.md` indexes the contract pointers and help sources; `docs/contracts/LIVE-BROADCAST.md` and
  `docs/contracts/DEVICE-EXCHANGE.md` remain unchanged until behaviour and evidence warrant an update. S0051
  owns the missing formal document registry.

## Last Audit

Task created only. No product code, build or device check ran for this ticket.
