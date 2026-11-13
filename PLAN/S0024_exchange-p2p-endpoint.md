# S0024 - P2P: a direct path between a broadcaster and a listener, negotiated through the exchange

**Status:** BlockExternal
**Status note:** Missing: a DEVICE-EXCHANGE amendment opening the P2P transport. The catalog (DEVICE-EXCHANGE 1.11, checked 2026-10-29) still reserves P2P in 7.7 and section 12; the contract owner rejected PROPOSAL-2026-10-10-p2p-endpoint-and-signalling on 2026-10-14 and no product may build a private P2P. Clears when the proposal is refiled, the contract owner (FastMediaSorter Android, with FMS_W for the server signalling) adopts it, and the owner schedules this ticket. Nothing started.
**Phase:** device exchange, later optional revision - independent of the build phases of handoff section 8
**Verification level:** to be written with the amendment; at least two networks behind NAT, one real listener.

## 0. Source

- Owner, 2026-10-10 (this session): relay or tunnel server of the exchange **and also P2P**.
- `DEVICE-EXCHANGE` 7.7 ("`P2P` stays reserved for a later optional revision"), section 12 ("P2P and STUN as a
  load-relieving optimization - reserved, out of v1"), 1.2 (an own server; third-party relay and TURN rejected
  for privacy), 13 (out of scope).
- `LIVE-BROADCAST` descriptor transport `P2P` (reserved), `12_relay_p2p_exchange_protocol.md` (superseded by
  sections 7.5-7.8 of the exchange; its P2P shapes are history, not text in force).
- Facts in this tree, 2026-10-10: nothing implements or plays `P2P`; the descriptor tests carry a `P2P` URL as a
  shape fixture only.

## 1. Goal

When the amendment exists: a listener on another network reaches the broadcaster directly, with the relay and
the tunnel as the fallback, so that the exchange server carries only the introduction and a long broadcast
costs the server nothing.

## 2. Scope

Held until the amendment names it. What the amendment must settle before any line is written (the proposal's
findings): the descriptor's `P2P` endpoint shape and its place in the order; the signalling messages and who
carries them (the control streams of the two devices through the server); how a device learns its public
address without a third-party service, given section 1.2; what transport P2P uses (the contract's media is TCP,
HTTP and RTSP); what a listener learns about the broadcaster (its public address) and the opt-in that says so.

The shape this ticket will then take: a candidate gatherer and an offer/answer exchange on the control stream,
a direct connection attempt with a short deadline, the endpoint order of 7.7 unchanged otherwise (a failed
direct attempt falls through to `RELAY`, then `TUNNEL`), and a player that never waits on P2P longer than the
deadline.

Not in scope: anything before the amendment; STUN or TURN from a third party; any change to the receiver's
rules for LAN, relay and tunnel.

## 3. Constraints and dependencies

### 3.1 Constraints

- The contract changes before the code (`RULES.md` 6); the amendment is the catalog owner's.
- Privacy: P2P exposes the broadcaster's public address to every listener, which relay and tunnel do not; it
  ships off, per broadcast, with a sentence that says so (invariants 11 and 14).
- Handoff section 6a: ported from FastMediaSorter if its phone builds P2P first.

### 3.2 Related tickets

S0016, S0020 (endpoint order; skips `P2P` until then), S0021 (the publisher), S0022 (the watch), S0013.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0016, S0020, S0021, S0022, S0013.
- **The amendment:** who writes it - the contract owner (FastMediaSorter Android) with FMS_W, whose server
  must carry the signalling. This product files the proposal and waits.
- **Public address discovery:** a third-party STUN service (rejected in spirit by section 1.2), or the
  exchange server reflecting the address it sees (recommended in the proposal).

## 4. Definition of done

Written with the amendment. Until then: the proposal is filed, the registry row says what waits, and this
ticket is not started.

## S4203 decisions 2026-10-14

S4203 is the release-46 device-exchange milestone of FastMediaSorter Android (FMS_A). The owner's answer of
2026-10-14 to its questionnaire is final:

- **P2P without a server and without port forwarding is NOT in release 46.** Port forwarding through a router
  already works. FMS_A S4212, folding the pending proposals, closes or defers
  `PROPOSAL-2026-10-10-p2p-endpoint-and-signalling.md` with a reason.
- **Kept unscheduled.** The ticket stays in the `--` bucket of `PLAN/RELEASE_QUEUE.md`, last, after the release-46
  exchange block; nothing is started and no amendment is asked for now. The status note was rewritten through
  `spec_catalog/update` to say so.
