# S0052 - INPUT-PARITY and Wear input gaps: video player keys, media session, activity-screen keys, Wear rotary volume

**Ticket:** S0052
**Status:** BlockExternal
**Status note:** Required phone device observations are missing: video headset and system media keys, Esc and Alt+Left fallback, and catalog paging and Home/End; no device was attached.
**Date:** 2026-10-27
**Phase:** input parity on phone and Wear - independent of the build phases of handoff section 8
**Verification level:** gate - each applicable clause held by a JVM test where one is possible and by a recorded keyboard-only or gamepad walk where it is not.
**Complexity:** hard - assessed at the spec pass: input behavior spans phone and Wear, playback lifecycle and multiple screens; required device observations remain open.
**Owner:** the StreamsPlayer Android session of this repository.

## Captured request and migration scope

`INPUT-PARITY` 0.4 sections 2-6 and the viewer keyboard profile bind the actions this product offers. The S0037 audit (`PLAN/S0037_contract-sync-20261017/audit.md`, section 5) read the phone and Wear modules and found the gaps below; clauses that do not bind a live-stream player (chord, file and image actions, seek, select) are recorded not applicable there.

## Spec pass - 2026-10-29

This is a complex change across phone and Wear and a shared behavior boundary. The current catalog is
`INPUT-PARITY` 0.5: its 2026-10-29 amendment clarifies that viewer rows apply only to functions offered,
gives the viewer keyboard profile precedence for viewers and adds the F1 help action. The existing catalog
text is the authority; no contract amendment or conformance-row advance is claimed by this pass.

Required research, open before the implementation plan can select the affected behaviors:

- [ ] On a connected phone, observe video playback with headset and system media keys; determine whether a
  video media session is needed or a reasoned exception is warranted (gap 2).
- [ ] On the affected activity screens, observe Esc and Alt+Left system fallback, and on the catalog observe
  PageUp, PageDown, Home and End behavior; record results before selecting handlers or exceptions (gaps 4-5).

The 2026-10-29 `adb devices -l` returned no attached devices. The S0037 reading is historical evidence, not a
device observation. Focus visibility and physical input checks remain acceptance evidence, not results of this
spec pass. Resume from Draft after the required observations are recorded; reassess all applicable 0.5 clauses
and the current source before approving the spec.

## 1. Gaps

1. Video player (`FullscreenPlayerActivity.dispatchKeyEvent` handles F1 only): `play-pause` on Space / K; `next` / `previous` on PageDown / PageUp, X / Y, CHANNEL_UP / DOWN, MEDIA_NEXT / PREVIOUS; `A` opens the audio-track dialog.
2. Video playback has no MediaSession: headset and system media keys do nothing for video. Add one, or record an exception with the reason (confirm on a device first).
3. The player's error dialog is not cancellable (`FullscreenPlayerActivity.kt:446-464`): Esc and Back do nothing. Make it cancellable with a safe default, or record why not.
4. Esc and Alt+Left on activity screens other than dialogs (player, Broadcast Control, Exchange, QR scanner, Help, widget configuration): add a shared handler, or record the system fallback seen on a device.
5. Catalog (about 19 thousand rows): PageUp / PageDown / Home / End (`StreamCatalogInputManager.handleKeyEvent`); gamepad stick navigation and key-repeat acceleration exist for the catalog only (`GamepadNavigationTranslator.kt`, `MainActivity.kt:176-179`): share it or record why not.
6. Broadcast Control dim overlay does not take focus (`BroadcastBlankScreenManager.show`), so Esc may not reach it.
7. Widget configuration search field: Ctrl+F / Start.
8. Focus visibility (rule 2) measured on a device for the card, the player buttons and the exchange rows.
9. Wear: `rotaryActionSteps` is declared and never called; the video player has no volume control. Wire the rotary volume or file recommendation R4 (the contract has no rotary column) with the evidence.
10. `F1` on the Exchange screens and the diagnosis dialog: the recorded debt of S0027 / S0031, unchanged.

## 2. Acceptance

- [ ] Every applicable clause is held by a test or a recorded walk, or carries a dated exception with reason, impact and review condition.
- [ ] Physical-device items are named as unverified until done; no JVM test is read as device acceptance.
- [ ] The `INPUT-PARITY` row advances only to what ran; no release, tag, upload or deployment is implied.

## Last Audit

Task created only. No product code, build or device check ran for this ticket.
