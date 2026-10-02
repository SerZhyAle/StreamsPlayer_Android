# S0003 - Stream core: models, Room v1, STREAM-BANK import and merge, classification, resilience

**Status:** Draft
**Phase:** 3 of 10 (handoff section 8)
**Verification level:** unit - the ported unit tests pass; the STREAM-BANK reader runs against the live
published catalog.

## 0. Source

`docs/BOOTSTRAP_HANDOFF.md` section 8 item 3, sections 3 (storage), 5 (phone module packages), 6a, 7.

## 1. Goal

The stream core of FastMediaSorter ported in full, with its unit tests, and no UI: models and media kinds,
an own Room schema at version 1, catalog import and merge from STREAM-BANK, classification, user state
(pins, collections), resilience policies.

## 2. Scope

- Port, never rewrite (handoff section 6a item 1): copy each class, rename the package to
  `com.sza.streamsplayer`, cut sorter references, port its tests. Every ported file gets its port-ledger row.
- Cut the seams: the stream tables leave the app-wide database for an own schema v1 with no history; stream
  bindings leave the shared DI graph.
- STREAM-BANK consumer, MEDIA-CLASSIFICATION consumer; USER-PLAYLIST if the m3u import moves with the core
  (otherwise S0009).

## 3. Contract audit owed here (contract-sync step 5)

There is no consumer code before this phase, so the seven compatibility-law answers are owed by this ticket,
each with a file and a line: match by name; higher MINOR accepted; higher MAJOR refused cleanly; unknown enum
value degrades to the documented default; ingest preserves user-authored rows (merge by stable key, touch only
producer-origin rows); bounds-checked indices; defaults taken from the contract. Each `no` is its own ticket.
Then update this product's registry rows from `pending` to the verified version.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0002 (modules, port ledger and drift check must exist).
- **Owner decisions:** none open for this phase.

## 4. Acceptance

- [ ] Ported unit tests green; exit code cited.
- [ ] Port ledger covers every ported file.
- [ ] Seven compatibility-law answers recorded; registry rows updated.
