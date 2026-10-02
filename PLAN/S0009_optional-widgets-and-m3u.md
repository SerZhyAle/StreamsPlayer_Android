# S0009 - Optional: home-screen widgets and m3u import

**Status:** Draft
**Phase:** 9 of 10 (handoff section 8) - optional
**Verification level:** device, per widget.

## 0. Source

`docs/BOOTSTRAP_HANDOFF.md` section 8 item 9, section 3 (owner wishes, done only if they port cheaply),
section 5 (`widget/Stream*`, `widget/BroadcastEntryWidgetProvider.kt`).

## 1. Goal

The channel tile widget, the radio tile and the broadcast entry widget, plus m3u playlist import if S0003 did
not already carry it (USER-PLAYLIST consumer) - each only if it ports cheaply.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0003, S0004.
- **Owner decisions:** none from handoff section 4; whether a widget "ports cheaply" is reported to the owner
  before it is dropped.

## 4. Acceptance

- [ ] Each carried widget places, configures and launches its target.
- [ ] m3u import, if carried here, conforms to USER-PLAYLIST and its registry row is verified.
