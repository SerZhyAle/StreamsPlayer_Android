# S0043 - A deliberate end of a broadcast shown as ended, not as a lost connection

**Ticket:** S0043
**Status:** BlockByOtherTask
**Status note:** Blocker: S0053 - tunnel ended reason and localized message; FastMediaSorter Android S4473 - watch ended-state behaviour and tests needed for the required full port.
**Complexity:** hard - confirmed at the spec pass on 2026-10-29: the phone and watch players, directory, network routes, background audio and localized surfaces change together, and the source port and shared wire boundary constrain the work.
**Depends on:** S0053
**Date:** 2026-10-25
**Phase:** receiving - `LIVE-BROADCAST` 1.2 amendment (ended slot with a clear status and an explicit reconnect),
section 5.2 item 3 (bounded reconnect); the producer and relay halves wait on the open catalog proposal
`PROPOSAL-2026-10-25-streamsplayer-a-deliberate-end-a-consumer-can-tell` (live-broadcast)
**Verification level:** unit tests on the JVM for the end classifier and the screens' state; then a rung 4
observation per route (LAN, relay, tunnel): a broadcast stopped on a FastMediaSorter Android phone reaches the ended
state, and a producer whose Wi-Fi is cut for 3 s is reconnected to, not ended.
**Owner:** the StreamsPlayer Android session of this repository. Filed 2026-10-25 from a read-only check of this app
against the receiver defects StreamsPlayer for Windows found that day (its ticket SP-0250).
**Update 2026-10-27 (S0037 audit, `PLAN/S0037_contract-sync-20261017/audit.md` section 6):** the proposal named above was folded into the contracts - `LIVE-BROADCAST` 1.5 item T and 1.6 item U, `DEVICE-EXCHANGE` 1.9 item DX and 1.10 item DY - so this ticket reads the folded text, not the proposal. Three corrections: (1) the line "a removal that comes with the device going offline reads as a lost connection" contradicts item U, which accepted the opposite as a recorded limit (a removal ends nothing that still delivers, ends a bounded reconnect already running, and a producer lost past the keepalive window reads as ended); (2) item DY is not in this ticket: a tunnel `connect` refused `ended` is a deliberate end and `unavailable` is not (the `ended` reason constant, its EN / RU / UK text and the item CZ table vendoring are S0053); (3) the line numbers of `FullscreenPlayerActivity` moved: `onPlaybackEnded` is at :421-423, the endpoint walk at :435-444. The producer half (item T order, sign-out) and the pending link (item S) are S0053.

## Captured request and migration scope

The question: when the producer stops, does this app reconnect as if the connection were lost? Which of these does it
already read - the directory's `broadcast-end` or removal for the broadcast it plays, a refused reconnect on the LAN,
the relay's answer for an ended id?

### What the code does today (code read)

- **Phone, video screen.** Every player error of a broadcast goes straight to the screen
  (`phone/src/main/java/com/sza/streamsplayer/ui/player/helpers/StreamPlaybackHelper.kt:348-353`), which walks to the
  next endpoint (`ui/player/FullscreenPlayerActivity.kt:423-431`) and, at the end of the list, shows
  `"%1$s" is not responding. Remove it from your list?` with Retry, Delete and Cancel (`:434-452`,
  `res/values/strings.xml:89`). A stopped broadcast is offered for deletion as a dead stream.
- **Phone, clean end.** `STATE_ENDED` (`StreamPlaybackHelper.kt:312-315`) reaches `onPlaybackEnded`, which only logs
  (`FullscreenPlayerActivity.kt:409-411`): an HTTP broadcast that ends its body cleanly leaves a still screen with no
  status and no action.
- **Phone, stall watchdog.** It runs for broadcasts too (started on ready, `StreamPlaybackHelper.kt:303`) and
  re-prepares the same address within its budget (`:453-475`) before the error reaches the screen.
- **Phone, audio service.** An error walks the endpoints (`ui/player/AudioPlaybackService.kt:209-211`); exhausted, it
  shows the generic "Broadcast not available" and stops (`:421-425`, `ui/exchange/ExchangeReceiverRuntime.kt:98-101`).
  `STATE_ENDED` arms the donor auto-stop silently (`AudioPlaybackService.kt:157-161`).
- **Directory signals.** `broadcast-end` and `changed` removals are read (`ExchangeReceiverRuntime.kt:147-153`) but act
  only on the broadcast of an accepted cast (`data/exchange/CastReceiverHandler.kt:119-125`), and then close the
  player without a status (`ExchangeReceiverRuntime.kt:66-69`). A broadcast played from the directory list
  (`ui/exchange/ExchangeActivity.kt:604-614`, no cast id), a kept channel or a link never learns of its end.
- **Refused reconnect, relay `404`.** Not read. The endpoint walk turns any `IOException` into "try the next one"
  (`exchange-core/.../data/exchange/BroadcastEndpointResolver.kt:42-47`); the relay's status reaches the player only
  as a playback error and is not classified.
- **Watch.** `STATE_ENDED` on a set of one stops silently (`wear/.../ui/player/audio/AudioPlayerViewModel.kt:213-234`);
  an error walks the endpoints (`:245-251`) and ends in the generic "playback unavailable" (`:551-554`). The watch
  reads no directory end for the stream it plays.
- **RTCP BYE.** Media3 1.11.0 RTSP (`gradle/libs.versions.toml:29`) carries no RTCP parser: its classes hold no RTCP
  packet type and no `BYE` (checked in `media3-exoplayer-rtsp-1.11.0.aar`). A BYE from the producer (the proposal's
  ask 2) is not observable through the current player.

So: the app does not loop forever - every path is bounded - but it reads a deliberate end as a lost connection, spends
the walk over the remaining endpoints (each a TLS connect, possibly a certificate question), and ends in a message
that calls the stream dead and offers to delete it.

## What changes here

- **An ended state** on the phone's video screen, the audio service's notification and screen, and the watch player:
  a clear status ("the broadcast has ended") with an explicit Reconnect; no Delete offered for it; no further attempt.
- **Read the folded contract:** a directory removal of the played broadcast is held while the stream delivers and is
  read when it stops; a removal during bounded reconnect ends that reconnect. A descriptor without a directory uses
  the transport answers instead. A clean end followed by one refused, `404` or `410` reconnect, relay `410`, and a
  tunnel `ended` refusal identify an end. Tunnel `unavailable` is not an end. A removal after the producer's
  keepalive window can also mean a lost producer; the contract records this indistinguishable case as a limit.
- The receiver behaviour belongs here; producer order, the tunnel `ended` reason and localized reason text belong to
  S0053. The producer does not forward `broadcast-end` to directory subscribers.
- Strings EN/RU/UK in one edit (S0030's terms test); the player's help source and help-map row change with the screen
  (S0035, `CONTEXT-HELP`).

## Related

S0020 (receiver playback and cast), S0022 (watch player), S0028 (directory list lifecycle), S0038 (an ended
broadcast is not resurrected; a saved channel is not declared ended merely because no record matches), S0034 (live
runs), S0042 and S0044 (same check of 2026-10-25).

## Spec pass 2026-10-29

- **Rung:** complex. The change spans substantially more than three existing files, adds behaviour and crosses the
  shared-contract boundary. `LIVE-BROADCAST` 1.5 items T and 1.6 U and `DEVICE-EXCHANGE` 1.9 DX and 1.10 DY are
  already adopted; no new wire format is proposed here.
- **Phone source:** FastMediaSorter Android S4444 contains a phone consumer implementation and its tests. Its policy
  includes the item U removal hold. The fullscreen and inline-audio surfaces in the source are not a complete map of
  this product's separate audio service and notification; the port plan must map those hosts before editing.
- **Watch source, required research open:** FastMediaSorter Android S4473 is Draft and records that its watch player
  shows neither a source error nor an ended state. No watch end policy was found in its watch source on 2026-10-29.
  This repository's standing full-port rule prevents inventing the missing watch playback behaviour here. Re-read
  the source and its tests after S4473 supplies the behaviour; then map the existing watch status and control surfaces.
- **Local prerequisite:** S0053 owns the tunnel `ended` reason and its EN/RU/UK message. S0043 consumes that reason;
  `unavailable` must remain a lost-connection result.
- **Placement research, required and open:** map the existing phone audio notification and screen, fullscreen player,
  and watch controls to the ended status and explicit reconnect, including background and accessibility behaviour.
  Resolve from the port and existing surfaces where possible; record any remaining owner choice before implementation.
- **Acceptance:** on phone video, phone audio and watch audio, preserve an ended slot with a clear localized status and
  explicit reconnect, offer no deletion for the end, and stop automatic attempts; distinguish the folded-contract
  signals above from lost connectivity and do not stop a delivering LAN stream on directory removal. Test the policy,
  route and screen states; observe LAN, relay and tunnel with a stopped producer and a three-second Wi-Fi cut. Carry
  player help and all authored locales together. Build and tests above the fast tier run at release.
- **Documentation registry:** the contract pointers and phone player help sources are affected when their represented
  behaviour is implemented; the handoff, privacy policy and data-safety records are unchanged by this spec pass.
  No product or generated documentation changed in this pass.
