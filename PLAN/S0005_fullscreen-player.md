# S0005 - Fullscreen player for video, HLS, DASH and RTSP

**Status:** Draft
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
- Port ledger rows for every ported file.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0003 (core), S0004 (entry from the catalog).
- **Owner decisions:** none open for this phase.

## 4. Acceptance

- [ ] Handoff section 9 item 1, video half: an HLS channel opens in the fullscreen player.
- [ ] DASH and RTSP channels route as STREAM-BANK document 06 says.
