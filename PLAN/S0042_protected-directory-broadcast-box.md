# S0042 - Protected broadcast from the account directory: credentials and seal keys from the record's box

**Ticket:** S0042
**Status:** BlockByOtherTask
**Status note:** Blocker: S0032 - phone and watch receiving must open directory sealed boxes and supply the keys; reconcile remaining credential, keep and refusal requirements afterward.
**Complexity:** Complex - confirmed 2026-10-29: shared-contract receiving, phone and watch flows, and localized user-facing messages span multiple modules and files.
**Depends on:** S0032
**Date:** 2026-10-25
**Phase:** device exchange, receiving - `LIVE-BROADCAST` item M (14 amendment) with item Q (16 amendment) and
`DEVICE-EXCHANGE` 6.3, items BF and DK; independent of the build phases of handoff section 8
**Verification level:** unit tests on the JVM (a sealed directory record with `credentials`, a sealed `TUNNEL` and a
LAN RTSP endpoint resolves both routes with the record's pair and asks nothing); then a rung 4 observation against a
FastMediaSorter Android phone broadcast on the LAN and through the tunnel.
**Owner:** the StreamsPlayer Android session of this repository. Filed 2026-10-25 from a read-only check of this app
against the three receiver defects StreamsPlayer for Windows found that day against a FastMediaSorter Android phone
broadcast (its tickets SP-0249 and SP-0250).

## Captured request and migration scope

The question: a descriptor with `auth` (item M) whose playable endpoints are LAN RTSP and a sealed `TUNNEL` only -
does this app answer the producer's Digest challenge, ask for the password once and play; or does it refuse, and then
say which route needs what? It must never fall back to an unauthenticated URL.

### What already holds (code read, not proven live)

- **A descriptor handed by link, file or barcode.** The password is required before any endpoint is touched
  (`phone/src/main/java/com/sza/streamsplayer/data/exchange/BroadcastPlaybackResolver.kt:33-39`, covered by
  `ProtectedBroadcastTest` "a password is required before any endpoint and unknown protection never connects"), asked
  once on the player screen (`ui/player/FullscreenPlayerActivity.kt:256-258`, `:291-302`) and kept for the endpoint
  walk of that screen (`:242`, `:429`).
- **LAN RTSP.** The pair is bound to the played address (`domain/player/BroadcastPlaybackPairs.kt:19-22`) and given
  to Media3 as the RTSP userinfo (`BroadcastPlaybackPairs.kt:52-55`, `ui/player/helpers/StreamPlaybackHelper.kt:175-176`);
  Media3 answers the RTSP digest challenge from it. HTTP endpoints answer Digest or Basic in the data source
  (`ui/player/helpers/StreamDataSourceFactoryProvider.kt:105-117`).
- **Sealed `TUNNEL` from a handed descriptor.** The pin travels in the endpoint (item Q); the forwarder rewrites the
  inner URL to loopback (`exchange-core/.../data/exchange/ExchangeTunnelProxy.kt:195-202`) and the pair is bound to
  that loopback address (`BroadcastPlaybackResolver.kt:41-42`).
- **No unauthenticated fallback.** With a descriptor on file every branch returns before the plain-URL fallback
  (`FullscreenPlayerActivity.kt:250-283` against `:285-287`); a wrong password is asked again, not retried
  (`FullscreenPlayerActivity.kt:417-420`).

### The defect - a protected broadcast picked from the account directory

1. **The record's box is never opened on the phone.** The directory item carries the record's descriptor only
   (`ui/exchange/ExchangeViewModel.kt:486-496`); `DeviceExchangeBroadcastRecord.sealed`
   (`exchange-core/.../data/exchange/directory/DeviceExchangeRecords.kt:126-127`) has no reader in `phone/src/main`.
   The watch opens it (`wear/.../data/exchange/WearExchangeE2e.kt:34`, `E2eBroadcastSeals.withKeys`); the phone never
   calls `withKeys`.
2. **So the sealed endpoints are skipped without a word.** A sealed `RELAY` without `seal.key` and a sealed `TUNNEL`
   without `seal.pin` resolve to nothing (`exchange-core/.../data/exchange/BroadcastEndpointResolver.kt:96-100`,
   `:133-135`), and the walk swallows it (`:42-47`). In the case asked about, only LAN RTSP is left.
3. **And the record's `credentials` are never used.** `BroadcastCredentialsDto`
   (`exchange-core/.../data/broadcast/BroadcastAuthDto.kt:28-33`) is referenced by no main code. The pair comes only
   from a typed or a stored password (`BroadcastPlaybackResolver.kt:34-39`), so the person is asked for a password the
   record already carries - for an own broadcast the own-devices pair of item M, which the person was never told. Keep
   asks the same way (`ui/exchange/ExchangeActivity.kt:623-625`, `ExchangeViewModel.kt:573-575`).
4. **The refusal names no route.** When nothing resolves the screen says "Broadcast not available"
   (`FullscreenPlayerActivity.kt:279-281`, `res/values/strings.xml:265`): not that the relay and the tunnel are sealed
   for keys this device did not open, nor that the LAN endpoint needs the producer's network.

Net effect for the case asked about: from a link the app should play on both routes; from the directory it asks a
password the person may not have, plays only on the producer's LAN, and off the LAN reports "not available".

### The watch (recorded, not reopened here)

The watch refuses every descriptor with `auth` (`wear/.../data/exchange/WearBroadcastPlaybackResolver.kt:29-31`) - the
phase decision recorded in S0022 (section 5 row "Receiving": "protected/E2E watch broadcasts are S0032 and refused in
this phase"). It never plays an unauthenticated URL, but its message is the generic "playback unavailable"
(`wear/.../ui/player/audio/AudioPlayerViewModel.kt:551-554`); item M's explicit deny should say the broadcast is
protected. Carried as a wording point of this ticket.

## What changes here

- Open the record's sealed box on the phone (`DEVICE-EXCHANGE` item DK; own context and `grant:<grantId>` of item DJ),
  write the keys into the endpoints as the watch does, and carry `credentials` on the opened descriptor in memory.
- Use the record's pair for playing and keep (item M "with the `credentials` of the directory record when it has
  them"); ask the person only when no pair exists; store a kept pair in the secret store as today.
- When no endpoint resolves, say which route needed what - in the words of item CZ (S0030's terms test), EN/RU/UK in
  one edit; the watch's protected-refusal line with it.
- **Port, not rewrite (handoff section 6a).** The record-opening code is carried from the FastMediaSorter Android donor
  with a port-ledger row; the watch adapter of S0022 is this tree's own and is reused, not copied a second time.

## Related

S0032 (end-to-end encryption, receiving and keys - this ticket is the phone half of its "keys distributed to the
account's own devices"; fold into S0032 on pickup if that ticket takes it), S0028 (account broadcast list,
protected-link reception), S0020 (receiver playback), S0022 (watch refusal phase), S0030 (words), S0034 (live runs).

## Dependency check - 2026-10-29

S0032 explicitly owns the phone and watch receive wiring, including opening the directory record's sealed box;
its status is In Progress. The phone's directory snapshot still passes `descriptorJson` directly into the item,
and the only main-source call to `E2eBroadcastSeals.withKeys` is in the watch adapter. The FastMediaSorter Android
donor has an `ExchangeSealedContentOpener` that opens own-account and grant boxes and carries credentials in the
opened descriptor. S0032 must deliver the receiving foundation before this ticket can be reconciled with that
implementation. Afterward, check the remaining S0042 requirements individually: use the record pair for play and
keep, route-specific refusal text, and the watch's protected-refusal wording. S0032 does not explicitly claim all
of those requirements; do not treat the overlap as proof they were delivered.

Documentation context: the maintained-document registry is not configured (S0051); `docs/README.md` indexes the
help sources and contract pointers. No product surface changes in this dependency check. The eventual user-facing
change requires the ship-together surface review and EN/RU/UK updates with the implementation.
