# S0007 - Wear app, Data Layer pairing and watch microphone broadcast

**Status:** BlockNeedUserTest
**Status note:** Implementation complete; pre-handoff audit recorded. S0007 debug probes cover Wear entry, phone open outcome, and broadcast on-air/stop. Builds and unit tests pass; small Wear emulator verifies catalog, audio/pins/background and HTTP ADTS/QR/stop. Await paired product phone/watch and audible watch broadcast in StreamsPlayer for Windows.
**Phase:** 7 of 10 (handoff section 8)
**Verification level:** device pair - watch and phone, both installed from this product.

## 0. Source

`docs/BOOTSTRAP_HANDOFF.md` section 8 item 7, sections 2 (goal 2), 3 (Wear), 5 (watch module, watch
broadcast), 6a.

## 1. Goal

The watch app ported from FastMediaSorter's `wear` module: channel list, pinned channels, playback on the
watch (background, stall and display-hold policies), the tile, handing a channel to the phone over the Data
Layer, and the watch microphone broadcast.

## 2. Scope

- Own Data Layer pairing with this product's phone app; same application id and signing key on both sides
  (decisions 4.1 and 4.2).
- The secondary form factor is judged against its own store guidelines: read the store variant's merged
  manifest and walk the smallest supported screen (canon overlay B).
- Port ledger rows for every ported file.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0002, S0003, S0006 (broadcast core), S0011 (watch surface: short pin and channel
  list, large play controls, open on phone, microphone broadcast; no desktop grid or camera assumption).
- **Owner decisions:** none open for this phase (4.1 and 4.2 answered in S0002).

## 4. Acceptance

- [ ] Handoff section 9 item 2: list visible, a pinned channel plays on the watch, the channel opens on the
      paired phone.
- [ ] Handoff section 9 item 6, watch half: a watch microphone broadcast plays on StreamsPlayer for Windows.

## 5. Implementation and evidence (2026-10-10)

Implementation is complete: independent Wear navigation, catalog/pins, audio/video players, background
audio, stream tile, the product's Data Layer pairing/phone open handlers, and owner-started watch
microphone broadcast with QR descriptor and explicit stop. Port provenance is registered; the drift
check passes (665 rows in step).

Details, manifest/permission review and the remaining device procedure:
[Wear verification](../../docs/S0007_WEAR_VERIFICATION.md).

- Both modules' `storeDebug` and `noLegalDebug` APKs build successfully. Store release APKs also build
  successfully with R8 and vital lint; these release outputs are unsigned without a release key.
- Wear unit tests: 233 per debug variant, zero failures/skips. Phone: 409 store / 411 noLegal tests,
  zero failures, one existing ignored test in each variant. The final Wear rerun after UI polish passed.
- Debug application IDs and signing certificates match. All 71 shared Data Layer path/event constants
  match. Runtime third-party notices are packaged in both modules' APKs.
- Small Wear emulator (192 dp / API 37): catalog of 19,404 channels displayed, radio playback and pin
  verified, unpaired-phone result displayed, background playback/return verified. Microphone permission,
  absent-Wi-Fi refusal, on-air state, QR decoding, HTTP AAC capture and stop/service removal verified.
  The captured stream contains 214 complete ADTS frames (4.969 seconds), 44,100 Hz mono. Final cold
  launch: 1,095 ms, no AndroidRuntime crash.
- Scratch evidence: `temp/S0007-validation-final.log`, `temp/S0007-packaging-final.log`,
  `temp/S0007-final-wear.log`, `temp/S0007-final-probes.log`,
  `temp/S0007-final-phone-logging.log`, and `temp/S0007/` screenshots/capture/reports.

The acceptance boxes remain open: no physical paired phone/watch or StreamsPlayer for Windows listener
was available. Install matching product APKs on the pair and complete both device acceptance journeys;
the emulator's local HTTP capture does not establish physical LAN reachability or audible Windows playback.

## Last Audit

**Date:** 2026-10-10. **Mode:** pre-handoff. **Verdict:** implementation covers the task;
device acceptance is pending, so the ticket is not Verified.

| Goal / scope | Coverage and evidence |
| --- | --- |
| Channel list and pins | Donor catalog, projections and pin repositories connected; real catalog/radio/pin observed on the small emulator; catalog and pin tests pass. |
| Watch playback policies | Donor audio/video stream paths, network holds, stall/display policies and background service connected; audio/background observed; pure policy/network tests pass; both video variants compile. |
| Stream tile | Donor assignment, refresh, launch targets and exported provider wired; launch target tests pass and merged manifest binding checked. |
| Product pairing / open on phone | Matching app ID/debug certificate, 71 mirrored constants, GMS manifests/capabilities, pin synchronization, foreground/deferred open and correlated result wired; optional-kind DTO tests pass; absent-phone result observed. |
| Watch microphone broadcast | Donor capture/pipe/server/descriptor connected; permission, no-Wi-Fi refusal, on-air/QR, 214 valid ADTS frames and service stop observed; server/descriptor/lease tests pass. |
| Store / provenance | Store merged manifests inspected; 192 dp screens walked; R8/vital lint and APK builds pass; notices packaged; all 665 ledger rows in step. |

- [ ] Observe a pinned channel playing and opening on a physically paired product phone/watch.
- [ ] Observe two-way pins after reconnect and the foreground/background notification outcomes on that pair.
- [ ] Observe the assigned tile, video/RTSP playback and stall/display behavior on the target watch.
- [ ] Observe audible watch microphone broadcast in StreamsPlayer for Windows over the shared physical Wi-Fi, including delay, reconnect and stop.

Keep the `S0007:` debug probes until device verification: Wear entry, phone-open outcome,
microphone on-air and microphone stopped. No shared contract was edited and no owner decision is open.
