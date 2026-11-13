# S0044 - Broadcast open logs the descriptor shape without secrets

**Ticket:** S0044
**Status:** BlockByOtherTask
**Status note:** Blocker: S0053 - pending descriptor parsing and persistence on phone and watch are required before the open log can report pending accurately.
**Date:** 2026-10-25
**Phase:** receiving diagnostics - `LIVE-BROADCAST` item M ("never puts the password into a URL it logs"), item R
(no password in a log or a diagnostic dump), `DEVICE-EXCHANGE` 6.3 (the broadcast id is a capability and is not
logged); `DIAGNOSTIC-REPORT` once an exporter exists (S0036/S0037)
**Verification level:** unit test on the JVM with a planted tree: one line per broadcast open on the phone's video
screen, the audio service and the watch player, with every shape field present and no URL, host, user, password,
key, pin or clear broadcast id; the existing redaction canaries stay green.
**Complexity:** hard - assessed at the spec pass: shared phone/watch playback, descriptor persistence, provenance and secret-free diagnostics cross modules.
**Owner:** the StreamsPlayer Android session of this repository. Filed 2026-10-25 from a read-only check of this app
against the receiver defects StreamsPlayer for Windows found that day.

## Captured request and migration scope

The question: does each broadcast open log the descriptor's shape without secrets - `auth` present, endpoint order,
transports, seal flags?

### What the code does today (code read)

- **Phone, video screen.** `startPlayback` logs nothing about the descriptor
  (`phone/src/main/java/com/sza/streamsplayer/ui/player/FullscreenPlayerActivity.kt:240-283`); the only broadcast lines
  are `S0020: playing directory broadcast` (`ui/exchange/ExchangeActivity.kt:609`) and the candidate index after an
  error (`FullscreenPlayerActivity.kt:428`).
- **Phone, audio service.** `resolveBroadcastItem` logs nothing (`ui/player/AudioPlaybackService.kt:380-394`); only the
  exhausted walk is logged (`:423`).
- **Shared resolver.** `BroadcastEndpointResolver` imports the exchange log (`exchange-core/.../data/exchange/BroadcastEndpointResolver.kt:11`)
  but writes no line: which endpoint was skipped and why (unsupported, sealed without key or pin, refused leaf) is not
  recorded (`:42-47`, `:56-67`, `:96-100`, `:133-135`). `BroadcastPlaybackResolver` writes none either.
- **Watch.** `loadNetworkAudio` logs nothing on a broadcast open (`wear/.../ui/player/audio/AudioPlayerViewModel.kt:536-573`).
- **Where a line would go.** A tree is planted only in debug builds (`StreamsPlayerApplication.kt:22`); a release build
  logs nothing and has no diagnostic export yet (`docs/contracts/DIAGNOSTIC-REPORT.md`: no exporter claimed). The
  redactor exists (`data/exchange/BroadcastLogRedactor.kt:8-12`) and a hashed id form is in use
  (`data/exchange/CastReceiverHandler.kt:121`).

## What changes here

- One permanent line per broadcast open, without a ticket-id marker, written from the shared resolver so the
  phone and the watch say the same: where the descriptor came from (link, directory, cast, kept channel),
  `schemaVersion`, `mode`, `auth` present and its scheme (not the user), `pending` present, and per endpoint in order
  its transport, mode, sealed flag and whether its key or pin is in hand - then the chosen index, or why each
  candidate was skipped.
- Never a URL, host, port, user, password, key, pin or clear broadcast id; the hashed id only.
- The line reaches a release build only through the diagnostic export when S0036/S0037 build it; until then it is
  debug and test-lab evidence, and the ticket says so in its record.

## Related

S0042 and S0043 (same check of 2026-10-25), S0031 (connection diagnosis), S0036/S0037 (diagnostic export), S0025
(dynamic audit probes).

Blocker: S0053

## Spec-pass research - 2026-10-29

- **Rung:** complex; the change crosses the shared resolver and phone/watch playback paths, requires new tests,
  and cannot be confined to three existing files.
- **Shared-contract boundary:** the log consumes the existing `LIVE-BROADCAST` descriptor and follows
  `DEVICE-EXCHANGE` secrecy rules; it changes no descriptor or wire shape. `DIAGNOSTIC-REPORT` remains
  conditional on the separate exporter. S0053 owns the contract-conformant pending reader and persistence.
- **Required dependency - open:** S0053, item 4, must parse and retain `pending` on phone and watch. The current
  `BroadcastDescriptorDto` has no `pending` member; the parser deserializes into that DTO, and both descriptor
  stores serialize the DTO. A pending marker is therefore unavailable at playback, so reporting its absence
  would be false. S0053 is Draft and has not delivered the required reader. Do not duplicate its wire change.
- **Required research - open:** trace how link, directory, cast and kept-channel origin can be carried to each
  playback open, including retries and watch handoff. The current descriptor stores retain descriptor data,
  not an origin; do not infer provenance from an endpoint URL. Resolve before approving a plan.
- **Research:** the resolver filters supported endpoints before walking them, and playback may call it again
  for a later candidate. The plan must preserve the original endpoint order and distinguish one open from
  subsequent retries, including rejected candidates and failures before the shared resolver is reached. Any
  string derived from an untrusted descriptor must be allowlisted before logging; arbitrary scheme, mode or
  transport values cannot be printed as safe shape fields.
- **Probe discipline:** a temporary ticket-id probe exists only while the ticket is `BlockNeedUserTest`;
  permanent diagnostics must never carry the ticket id. The captured instruction to retain the marker is
  superseded by the invariant.
- **Documentation registry:** `docs/README.md` indexes the shared contract pointers and diagnostic policy;
  no registered document or user-facing surface changes at this blocked spec pass. Revisit at each phase.
- **Evidence deferred to release:** the JVM test suites and Android builds have no measured fast form. Once
  implemented, record skipped build and targeted test rungs as RELEASE items using
  `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1`; the change is not built or tested until release.
