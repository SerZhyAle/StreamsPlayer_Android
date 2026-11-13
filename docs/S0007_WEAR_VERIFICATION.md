# S0007 implementation and verification

The Wear app is an independent StreamsPlayer installation. It uses the phone's
`com.sza.streamsplayer` application ID and the same signing convention. All active
Data Layer paths use `/streamsplayer/`; the two modules advertise their own
StreamsPlayer companion capabilities. No FastMediaSorter installation is needed.

## Implementation

- The donor channel catalog, projections, watch/phone pins, tile assignment and
  launch target handling are connected to the new Wear navigation surface.
- Audio and video playback retain the donor stream preparation, Media3 data sources,
  network holds, ICY metadata, stall timeout and display hold policies. Audio can
  hand its session to the foreground playback service and take it back on return.
  The home screen exposes a running background session. Audio volume and the
  background setting are available in the player.
- The donor listener handles stream transfers, foreground open confirmation,
  deferred notification entry and playback commands. Watch-authored pin deltas are
  retained while disconnected and sent again on launch/capability changes. The
  phone publishes its complete pin set, including an empty set.
- Open on phone carries the URL, display name and optional media kind. A visible
  phone opens the channel; a background phone offers a notification. The watch
  distinguishes shown, notified, notification refusal, invalid target and timeout.
  The optional kind preserves pathless video URLs and remains compatible with
  requests that omit it. The phone resolves existing catalog entries before
  constructing a transient playback entry.
- Microphone broadcast remains owner-started. The service holds a Wi-Fi lease,
  streams mono AAC ADTS at 44,100 Hz / 64,000 bit/s, publishes the LIVE-BROADCAST
  descriptor, and exposes its QR code. Stop cancels an in-progress start and closes
  the server, recorder, network lease and wake lock. A killed service does not
  automatically reopen the microphone.

Sorter repositories, network files, cameras, voice note delivery and unrelated
application navigation were removed from the stream player/listener/recording
seams. Shared neutral donor models and preference storage remain private to Wear;
they expose no extra product flows. Ports and donor tests are recorded by source
hash in the port ledger. Three obsolete `strings_fms.xml` ledger destinations were
collapsed into the already registered `strings.xml` destinations through the
ledger library, after checking identical source/hash and the new files' presence.

## Manifest and permissions

The store release merged Wear manifest was inspected. It requires watch hardware,
declares standalone operation, and exports the launcher, GMS listener and the tile
provider. The tile requires `BIND_TILE_PROVIDER`. Capture is not exported; the
playback service exposes the Media3 session interface. Its foreground type is
`mediaPlayback`; capture uses `microphone`.

Internet and network state support stream fetching and Wi-Fi selection. Network
change and wake lock permissions support a session-owned transport and capture
while the display sleeps. Foreground service permissions support the two declared
service types. Microphone permission is requested from the broadcast screen after
the same-Wi-Fi audience explanation. Notification permission enables the ongoing
stop action and deferred opens; denial does not itself authorize microphone access.
Cleartext HTTP is needed for radio sources and the LAN broadcast endpoint. No
camera, storage or location permission is introduced on Wear.

Both APK families include runtime third-party notice assets. Regeneration is
documented in `tools/third-party-notices/README.md`.

## Automated checks (2026-10-10)

The following checks passed. The final Wear packaging and test rerun after adding
the background setting readout and volume controls also passed.

```powershell
./gradlew.bat :wear:assembleStoreDebug :wear:assembleNoLegalDebug :phone:assembleStoreDebug :phone:assembleNoLegalDebug :wear:testStoreDebugUnitTest :wear:testNoLegalDebugUnitTest :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest :wear:assembleStoreRelease :phone:assembleStoreRelease --console=plain
```

Wear: 233 tests per debug variant, no failures or skips. Phone: 409 store tests and
411 noLegal tests, no failures, one existing ignored test in each variant. Tests
cover pins and offline deltas, catalog merging/projections/search, transfers,
background/stall/display policies, network lease cleanup, the bounded HTTP server,
descriptor serialization/identity, and the optional open-on-phone media kind.
Release builds run R8 and vital lint. Without a configured release key they produce
unsigned APKs; they are not distribution-ready signed packages.

All 71 shared Data Layer path/event constants match between modules. Both debug
APKs have signing certificate SHA-256
`9cc45cfe8ce58eb84b9e2f59b592a9db9ebafa4f02aaaacca97f164ff3a27d42`.
The port drift check passes with 665 rows in step.

## Emulator evidence

`Wear_OS_Small_Round`, API 37, 384 x 384 pixels at density 320 (192 dp):

- Installed and cold-launched the final store debug app in 1,095 ms; no
  AndroidRuntime launch crash.
- Walked the home, channel list, audio controls, permission prompts, broadcast and
  QR screens. Lists scroll; the principal buttons remain accessible.
- Downloaded the real catalog (19,404 channels), played a radio station, pinned it,
  and observed the pin action change to Unpin stream.
- Open on phone without a paired phone displayed the explicit unavailable result.
- Enabled background audio, pressed Home, observed the foreground playback
  service, and returned to the player.
- Broadcast refused absent Wi-Fi, then entered On air after connecting AndroidWifi.
  Its QR image decoded into a valid descriptor. The advertised Wi-Fi endpoint
  returned HTTP/1.0 200, `audio/aac`, and more than 40 KB of AAC ADTS. ADTS headers
  confirm 44,100 Hz mono across 214 complete frames (4.969 seconds). This capture
  used a client on the emulated Wi-Fi address;
  it does not prove Windows playback, physical LAN reachability or audible delay.
- Stop returned the screen to its start state and removed the microphone service.

Scratch logs, screenshots, the decoded descriptor and AAC capture live under
`temp/S0007/` (not committed).

The catalog and release queue validators pass. Catalog validation also reports
pre-existing missing-spec hygiene warnings for S0002 through S0006; these are
outside this implementation.

Debug log probes for device verification are `S0007: Wear entry opened`,
`S0007: Open-on-phone result=%s`, `S0007: Watch microphone broadcast on air`,
and `S0007: Watch microphone broadcast stopped`. They record no channel URL or
microphone content. Both modules use Timber debug trees in debug builds.

## Remaining acceptance on devices

Install phone and Wear APKs of this product using the same distribution/signing
family on a paired phone/watch. Confirm catalog visibility, pinned playback,
watch-to-phone opening, notification refusal/deferred opening, and two-way pin
updates after disconnect/reconnect. Verify the tile starts its assigned channel,
background audio survives a wrist drop and returns without overlapping players,
and a stall releases playback/display/network holds.

On a shared physical Wi-Fi network, scan the watch broadcast descriptor in
StreamsPlayer for Windows. Confirm audible microphone audio, usable delay,
reconnection and bounded multiple listeners, and that stopping on the watch or
from its notification ends playback and releases capture. These are the two
unchecked acceptance items in S0007; emulator and JVM evidence do not replace them.
