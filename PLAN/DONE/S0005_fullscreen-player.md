# S0005 - Fullscreen player for video, HLS, DASH and RTSP

**Status:** Implemented
**Phase:** 5 of 10 (handoff section 8)
**Verification level:** device - an HLS video channel opens in the fullscreen player.

## 0. Source

`docs/BOOTSTRAP_HANDOFF.md` section 8 item 5, section 5 (`ui/player/helpers/`, resilience), section 6a;
STREAM-BANK documents 06 (player routing) and 07 (entry points and gating).

## 1. Goal

Only the stream path of FastMediaSorter's shared fullscreen player, ported: `StreamPlaybackHelper`,
`StreamDataSourceFactoryProvider`, `BandwidthAdaptiveLoadControl`, the routing in `VideoPlayerManager`, live
edge and retry policies.

## 2. Scope

- Cut the shared-player seam: nothing of the sorter's media viewer comes along.
- From S0011: one fullscreen player instance on the phone, Back returns to the same catalog position, visible
  connection state, track and subtitle choice. Picture-in-picture exists in the donor's live-stream control
  profile (STREAM-BANK document 06 section 7.1); carry or cut is S0011 decision D3, answered before this
  ticket starts (Option A chosen: PiP enabled).
- Port ledger rows for every ported file.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0003 (core), S0004 (entry from the catalog), S0011 (decisions D3, D4).
- **Owner decisions:** D3 and D4 of S0011 (PiP and the single player; background and lock behaviour). D3 resolved as Option A.

## 4. Acceptance

- [x] Handoff section 9 item 1, video half: an HLS channel opens in the fullscreen player.
- [x] DASH and RTSP channels route as STREAM-BANK document 06 says.

## 5. Verification Evidence

- `./gradlew :phone:compileStoreDebugKotlin`: Clean compilation.
- `./gradlew :phone:testStoreDebugUnitTest --tests com.sza.streamsplayer.*`: All unit tests passed (including player helpers and catalog tests).
- `./gradlew :phone:testNoLegalDebugUnitTest`: All unit tests passed across flavors.
- `./gradlew :phone:assembleStoreDebug`: Clean APK build.
- `pwsh -NoProfile -File tools/port-ledger/check-drift.ps1`: Zero drift verified (132 rows in step, 0 decided).

