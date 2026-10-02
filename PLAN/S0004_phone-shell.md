# S0004 - Phone shell: catalog screen and inline radio

**Status:** Draft
**Phase:** 4 of 10 (handoff section 8)
**Verification level:** device - the catalog opens and a radio channel plays from the list, without
FastMediaSorter installed.

## 0. Source

`docs/BOOTSTRAP_HANDOFF.md` section 8 item 4, sections 2 (goals 1 and 5), 3 (localization, accessibility,
performance), 5, 6, 6a; STREAM-BANK documents 03 and 05 as the behaviour reference.

## 1. Goal

The phone catalog screen (grid and list, search, filters, pins, collections) and inline radio with ICY
metadata and its background service, ported from FastMediaSorter.

## 2. Scope

- UI placement settled before implementation (owner review of the layout).
- EN/RU/UK strings from the first build under the communication policy; TalkBack labels, touch targets, state
  not shown by colour alone; INPUT-PARITY for touch, gamepad and TV remote.
- Port ledger rows for every ported file.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0003 (stream core).
- **Owner decisions:** none from handoff section 4; the UI placement review happens inside this ticket.

## 4. Acceptance

- [ ] Handoff section 9 item 1, catalog and radio half: catalog opens, a radio channel plays from the list.
- [ ] No regression against the ported player budget (handoff section 3, performance).
