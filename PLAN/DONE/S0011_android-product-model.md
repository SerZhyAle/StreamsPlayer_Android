# S0011 - Android product model: screens and behaviour adapted from the Windows product

**Status:** Verified
**Phase:** gate before phases 4-7 (handoff section 8) - not a build phase
**Verification level:** owner review - the screen map and the owner decisions of section 3.3 are answered in
writing; nothing is built here.

## 0. Source

- Research note `temp/research/windows-product-baseline.md` (read-only investigation of 2026-10-02 against
  `P:\WINDOWS\Streams_Player`; a proposal, not an approved screen specification; source reads only - no app
  launched, no test run, no new playback observed).
- `docs/BOOTSTRAP_HANDOFF.md` section 2 goal 5 (look and behaviour continue StreamsPlayer for Windows),
  sections 3, 5, 6, 6a, 8; `CLAUDE.md` section 2 (standing law: full port, never a rewrite).
- STREAM-BANK 2.2 documents 05, 06, 07 (behaviour reference; document 05 itself says it describes the donor
  UI as-is and binds only what it marks `[CONTRACT]`), item D of the merge rules (retirement keeps the id);
  LIVE-BROADCAST 0.13 sections 2.1-2.5, 6.

## 1. Goal

Turn the Windows product model into an Android screen map that phases 4-7 build against, and settle - once, in
writing - where that map follows a ported FastMediaSorter class and where it follows the Windows scenario, so
that no later ticket re-opens the question. No code, no contract change.

## 2. Scope

### 2.1 Screen map (proposal from the note; the owner approves or amends it)

| Windows scenario | Android surface | Ticket that builds it |
| --- | --- | --- |
| Find a channel | landing catalog: search, Video / Radio / Mine chips, list or grid; advanced filters and sort in a bottom sheet; an active-filter marker that survives closing the sheet | S0004 |
| Return to own channels | pins section above the catalog, named collections, recently played; pins and collections stay independent | S0004 (pins, collections), S0012 (recents) |
| Add and pass on a channel | channel actions sheet, manual add, M3U import with preview before apply, system share; catalog refresh keeps user rows | S0004, S0009 (M3U) |
| Listen while using the app | persistent bottom player: title, track, stop/resume; expanded screen with sleep timer and previous/next; resume returns to the live edge | S0004 (player, resume), S0012 (timer) |
| Watch video or RTSP | fullscreen player, touch controls, track and subtitle choice, visible connection state; Back returns to the same catalog position | S0005 |
| Recover from an error | bounded reconnects, then the explanation with Retry, Keep, Copy report, Hide (catalog row) or Delete (own row) | S0004, S0005 |
| Configure | preferences screen separate from the operations (import, refresh, clear, diagnostics) | S0004 |
| Broadcast | separate Broadcast screen: source choice (microphone, camera + microphone, camera only), preview, explicit Start/Stop, on-air state, endpoint, descriptor and QR share | S0006 |
| Watch | short pin and channel list, large play controls, open on phone, microphone broadcast | S0007 |
| Outside the app | notification and system media session controls | S0004 |

**Approval note (2026-10-10):** The Android screen map proposed above was approved as written by the owner.

### 2.2 Reconciliation of the note with the ported donor (read 2026-10-02)

These are findings of this ticket, each to be written into the dependent tickets as scope or as an owner input.

1. **Two donors, two layers.** The behaviour (view model, use cases, resilience, routing, broadcast) is ported
   from FastMediaSorter and is not rewritten (law, section 2 item 1). The Windows model decides the screen
   structure and the scenarios. Where a Windows scenario needs a layout the ported FastMediaSorter screen does
   not have (bottom-sheet filters, library surface, bottom radio panel), the view layer is new and sits on the
   ported behaviour classes. This split is owner decision D1 below; S0004 currently reads as a screen port.
2. **Document 05 is no obstacle to the layout.** It describes the donor UI as-is and binds only `[CONTRACT]`
   items; the facets, the AND semantics, RTSP folded into Video and the pinned-only filter are the behaviour
   to keep. A change to a `[CONTRACT]` behaviour is a catalog amendment first (law, section 2 item 3).
3. **Picture-in-picture already exists in the donor.** Document 06 section 7.1 keeps PiP for a live video
   stream and says its manager has no stream gating. The note treats PiP as a scope proposal; the real
   question is whether S0005 carries the PiP manager with "only the stream path" or cuts it (D3).
4. **Sleep timer is not in the donor for streams.** Document 06 section 7.1 hides it for a live stream. A radio
   sleep timer is Windows-only behaviour, new Android code, outside the first-release list of the handoff -
   routed to S0012, not to S0004.
5. **Stop and resume.** Document 05 section 15 gives the inline player a Stop button only; Windows keeps a
   resumable station and resumes at the live edge. This is a behaviour delta against a donor document; whether
   the section is `[CONTRACT]` is checked first, then it is amended in the catalog or taken as a documented
   Android exception (D4).
6. **Background and lock.** The donor already defines an answer for leaving the screen with audio playing
   (`backgroundAudioExitBehavior`: ask, always stop, always continue, document 05 section 15). The note lists
   background and lock behaviour as open; the question is narrower: keep the donor policy or replace it with
   the Windows "no background surprises" reading (D4).
7. **Windows-only catalog features.** Minimum-bitrate filter (the `bitrate` column exists in the bank; no donor
   filter uses it), "Mine" as a facet value (the donor has `sourceOrigin` but no such filter), recently
   played, hidden-channel management, listening history, received-stream recording, XMLTV programme guide.
   Each is a new feature, not a port. They belong to S0012 and wait for the owner's scope answer (D2).
8. **Retirement semantics.** The note warns that the Windows README promises more than its code. The
   contract is the authority: STREAM-BANK item D keeps the channel id on retirement and merge touches
   `CATALOG`-origin rows only. The donor deletes catalog rows on prune (`deleteCatalogByUrls`) and offers a
   separate "identity survives prune" feature. The S0003 contract audit answers which holds on Android; this
   ticket only records that "retire, keep pin/collection/history" is the target.
9. **Broadcast kinds and the acceptance of S0006.** S0006 requires the handshake against StreamsPlayer for
   Windows "for each stream kind". The contract registry records the Windows consumer as accepting both camera
   kinds (SP-0159) but the section 6 handshake against a live phone camera broadcast through the Windows
   consumer was not re-run, and end-to-end delay for the video kinds is a standing deviation. Whether the
   distributed Windows build contains SP-0159 decides whether S0006 can be verified against it at all - see
   section 2.4.
10. **Sharing a channel.** The donor shares the bare URL as text (document 05 section 11). The note asks for a
    credential-risk warning before copying an address that carries credentials; that is a new Android
    behaviour for S0004 and is added to its scope.

### 2.3 Not in this ticket

Implementation of any screen; changes to a contract, to the handoff, or to the Windows or FastMediaSorter
repositories; numeric buffer and retry values (Windows constants are platform evidence and are not copied;
the Android values stay those of the ported policies unless a catalog amendment says otherwise).

### 2.4 Evidence owed from the Windows donor (relayed by the owner; this repository does not message that session)

Request, to be passed to the Windows StreamsPlayer session as written:

> Confirm which features of the current source tree are in the currently distributed Windows build, in
> particular SP-0159 (video broadcast modes) and the two camera kinds of LIVE-BROADCAST. Reconcile the README
> and the release queue with the Implemented status of SP-0159. State which physical-phone and watch handshake
> checks are still open. Supply current screenshots of the list with radio playing, the channel actions menu
> and the video player error dialog, each with the build number. State which interaction changes were approved
> after those screenshots, and the known differences from Android in recovery, live latency, previous/next
> navigation and channel retirement. No code changes and no contract decisions are requested.

**Resolution (2026-10-10):** The evidence request was evaluated with the owner. Physical Windows listener/handshake is unavailable in this Android workspace; emulator tests and mock/unit tests serve as current verification evidence. Physical cross-product handshake is deferred to end-to-end device acceptance (consistent with S0006 and S0007). The gap is hereby formally named and recorded.

### 3.3 Owner inputs (Approval gate)

Asked once each, in question-with-options form, when this ticket moves to approval; each blocks the ticket named.

- **D1 - Donor split (blocks S0004).** **Answered (2026-10-10):** Option (a) chosen by owner — behaviour classes ported from FastMediaSorter, view layer designed new from the Windows scenarios.
- **D2 - Windows-only features (blocks S0012).** **Answered (2026-10-10):** Wave 1 (v1.0): "Mine" filter (already in S0004), Sleep timer for radio, Recently played list; Wave 2 (post-v1.0): Listening history, Hidden-channel management, Minimum-bitrate filter, Received-stream recording; Dropped: XMLTV programme guide.
- **D3 - PiP and the single video player (blocks S0005).** **Answered (2026-10-10):** Option (a) chosen by owner — Support Picture-in-Picture (PiP) ported from the donor, using a single fullscreen player instance on the phone.
- **D4 - Background, lock, resume (blocks S0004, S0005).** **Answered (2026-10-10):** Adopt Windows resume-at-live-edge and persistent resumable mini-player with media session controls.
- **Related tickets:** S0003 (core), S0004, S0005, S0006, S0007, S0009, S0012.

## 4. Acceptance

- [x] The screen map of 2.1 is approved or amended by the owner, in writing, in this file (approved as written 2026-10-10).
- [x] D1-D4 answered; each dependent ticket carries the answer in its own scope (D1 in S0004; D2 in S0012; D3 in S0005; D4 in S0004, S0005).
- [x] The evidence request of 2.4 passed to the Windows session by the owner, and the reply recorded or the gap named (gap named 2026-10-10: physical handshake unavailable in workspace, deferred to device acceptance).
- [x] Any catalog amendment the answers require is sent to the canon session as an exact diff before the dependent ticket starts (none required: donor policies and StreamBank/LiveBroadcast contracts accommodate the decisions).

## 5. Verification Evidence (2026-10-10)

- Owner review completed in writing: Android screen map (Section 2.1) approved as proposed.
- Owner decision D1 resolved: ported FastMediaSorter behaviour classes, view layer designed new from Windows scenarios; recorded in S0004 scope.
- Owner decision D2 resolved: Wave 1 (v1.0): "Mine" filter, Sleep timer, Recently played; Wave 2 (post-v1.0): Listening history, Hidden channels, Min-bitrate filter, Stream recording; Dropped: XMLTV guide; recorded in S0012 scope.
- Owner decision D3 resolved: Option (a) PiP supported from donor using single fullscreen player instance; recorded in S0005 scope.
- Owner decision D4 resolved: Windows resume-at-live-edge and persistent resumable mini-player with media session controls adopted; recorded in S0004/S0005 scope.
- Windows donor evidence 2.4 evaluated: physical Windows handshake unavailable in this workspace; gap named and recorded.
- No contract amendment required: STREAM-BANK and LIVE-BROADCAST contracts accommodate all approved decisions without schema or behavioral contract changes.

## Last Audit

**Date:** 2026-10-10 | **Mode:** owner-review | **Outcome:** Verified
**Counts:** PASS 4 | WARN 0 | FAIL 0 | MANUAL 0 | UNCHECKABLE 0 | EXEMPT 0

### Verdict

Verified. S0011 is an owner-review gate ticket (no code built). All owner inputs (D1-D4) are resolved in writing, the screen map is approved by the owner, evidence gap 2.4 is named and recorded, and no contract amendments are required. Downstream ticket scopes reflect these decisions.
