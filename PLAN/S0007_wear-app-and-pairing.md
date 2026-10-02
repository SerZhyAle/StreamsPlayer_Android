# S0007 - Wear app, Data Layer pairing and watch microphone broadcast

**Status:** Draft
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

- **Related tickets:** S0002, S0003, S0006 (broadcast core).
- **Owner decisions:** none open for this phase (4.1 and 4.2 answered in S0002).

## 4. Acceptance

- [ ] Handoff section 9 item 2: list visible, a pinned channel plays on the watch, the channel opens on the
      paired phone.
- [ ] Handoff section 9 item 6, watch half: a watch microphone broadcast plays on StreamsPlayer for Windows.
