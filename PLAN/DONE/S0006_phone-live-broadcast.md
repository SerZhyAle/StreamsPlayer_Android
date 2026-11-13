# S0006 - Live broadcast from the phone: microphone and camera

**Status:** Implemented
**Phase:** 6 of 10 (handoff section 8)
**Verification level:** cross-product - the LIVE-BROADCAST handshake passes against StreamsPlayer for Windows
for each stream kind.

## 0. Source

`docs/BOOTSTRAP_HANDOFF.md` section 8 item 6, sections 2 (goal 6), 3, 5 (live broadcast packages), 6a, 7.

## 1. Goal

The FastMediaSorter phone broadcast ported in full: microphone and camera sources, the consent and permission
flows, the endpoint - making this product a LIVE-BROADCAST producer from its first release.

## 2. Scope

- Port the broadcast packages and only the capture code they import (camera, audio recording seams).
- Screen from S0011: one separate Broadcast screen - source choice (microphone, camera + microphone, camera
  only), preview before start, explicit Start/Stop, on-air state, reachable endpoint, descriptor and QR share.
  The donor already has a unified broadcast screen with preview, lens choice and QR; this is a port, not a new
  design. Define Back, screen lock, task dismissal and process death for an active broadcast.
- Verification dependency: the camera kinds can be verified against StreamsPlayer for Windows only if its
  distributed build carries SP-0159; the evidence request of S0011 section 2.4 settles that, otherwise the
  camera half is verified against another conforming listener and the gap is written here.
- Contract trigger: if a ported capture seam saves a file the user can see (voice recording, photo, video,
  frame), this product comes under `CAPTURE-OUTPUT` - its pointer and registry row are added in this ticket.
- Permissions declared only as used at runtime, each with a user-visible justification (canon invariant 13).
- Port ledger rows for every ported file.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0002 (modules), S0003 (core), S0011 (screen map, Windows build evidence).
- **Owner decisions:** none open for this phase.

## 4. Acceptance

- [ ] Handoff section 9 item 6, phone half: a microphone broadcast, then a camera broadcast, play on
      StreamsPlayer for Windows; the LIVE-BROADCAST section 6 handshake passes for each.
- [ ] This product's LIVE-BROADCAST registry row moves from `pending` to the verified version.
