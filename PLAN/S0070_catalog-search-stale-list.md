# S0070 - catalog-search-stale-list

**Ticket:** S0070
**Status:** Implemented
**Status note:** Implemented, no compile command declared (commands.compileCheck absent), not built, not tested until the release: needs pre-release-sweep.ps1 (phone suite with RowDiffBudgetTest and StreamChannelAdapterSubmitRowsTest) and the rig phase-b SC-06/SC-20 re-run on the 19,550-row catalog
**Complexity:** medium - confirmed at the spec and plan pass 2026-11-13: the cause is the adapter's DiffUtil, not the projection, and the fix is one pure helper, one adapter method and one call site with two test files; no schema, DI, route or contract moves, but the root cause needed a measurement and the proof is a rig re-run on a 19,550-row catalog
**Date:** 2026-11-13

## 0. Captured request and migration scope

Finding S0025-F-0004 (P1, rank L) of the S0025 phase B audit session, 2026-11-13, run temp/S0025/b-20261113-2.

Symptom: on the phone catalog with the real channel list (19,550 channels after the fixture import), typing a
search query leaves the full unfiltered list on screen for about a minute before the matching rows appear:
67,625 ms in the session run, 65,344 ms in the aborted run b-20261113-1, about 50 s in the exploration pass (query
"SP Audit", 15 matches). While the list is stale, a query with no match shows "No channels found" on top of the old
rows (3 stale rows beside the empty message, reproduced twice). The main thread stays free during the wait (main-Looper
ping p50 0 ms, max 5 ms), so the delay is off the main thread - the row list handed to the adapter is the suspect
(StreamsViewModel filtering 19,550 sources, MainActivity submitList of the full row list and its background diff).
Narrowing an already-narrow list is fast: 4,360 ms on the 119,550-channel catalog for a 1-row result from a 15-row one.

Budget: B-2 (hypothesis) p95 <= 1,000 ms; measured 67,625 ms, lower bound 67,025 ms.
Reproduction: py -3 tools/audit/rig.py phase-b --serial emulator-5556 --apk phone/build/outputs/apk/store/debug/phone-store-debug.apk --run-id <new-id>
Evidence: temp/S0025/b-20261113-2/SC-06-search-latency.json, SC-20-events.json, verdicts.json.
Routing: decision 14.5 (2026-11-08) - a P1 becomes a ticket at once, in the current release package by priority.

## 1. Problem

Typing in the catalog search with the full channel list loaded leaves the unfiltered list on screen for about a minute, and a query that matches nothing shows "No channels found" over the old rows. The person cannot tell whether the search worked, and tapping a row that is about to disappear plays the wrong channel.

## 2. Goals, non-goals and constraints

- **G1.** A search typed over the whole catalog, and clearing it, reach the list in the same call that receives the new rows. Budget B-2: p95 <= 1,000 ms from the query to the first list whose rows all match.
- **G2.** A query with no match shows no stale row beside the empty message.
- **G3.** A small change (one row changed, inserted or removed, a short search step) keeps the in-place diff and its animation.
- **G4.** The fix adds nothing to the screen's text, controls or settings.

Non-goals: the projection in `StreamsViewModel` (section 3.3); the widget channel picker (parked as S0071); the rig itself; any release.

Constraints: `CLAUDE.md` section 5a - nothing above 5 s runs between releases, so the build, the suites and the rig scene are RELEASE items; section 2 - the playback and broadcast behaviour is untouched. `StreamChannelAdapter.kt` and the phone `MainActivity.kt` have no row in `docs/port-ledger.jsonl` (grep, 2026-11-13), so no ported file is edited; the ported `StreamsViewModel` is not touched.

## 3. Research findings (all closed)

### 3.1 Where the minute goes (read 2026-11-13)

- The state reaches the screen quickly: SC-20 records the empty message at 1,984 ms while stale rows are still on screen, so `StreamsViewModel` has delivered the final state and the list is what lags.
- `MainActivity.observeViewModel` hands `state.rows` to `StreamChannelAdapter`, a `ListAdapter`, which diffs old into new with DiffUtil on a background executor. A diff is not cancellable and a newer submit only discards a finished result, so one diff per typed letter runs to the end.
- DiffUtil's Myers search loops over the number of differing rows `D` quadratically even when it calls the item callback rarely. Narrowing 19,550 rows to a few, or widening back, makes `D` about 19,500. Narrowing an already narrow list (the 4,360 ms case of the finding) has a small `D`, which is why that case is fast.
- The row key `"channel:${source.id}"` is a getter that builds a string on each call, so every comparison of the diff allocated two strings.

### 3.2 Measured, JVM probe (2026-11-13, sources and output in `PLAN/S0070_catalog-search-stale-list/probe/`, host JVM, recyclerview 1.3.2 `DiffUtil` itself)

`DiffProbe`, exit 0 (the row key built per comparison as in the app): 6,000 rows to 15 scattered 43 ms; 19,550 rows to 15 scattered 532 ms, to 60% scattered 565 ms, back from 15 to 19,550 505 ms; one row moved to the top 1 ms; to an empty list 0 ms. The cost is roughly quadratic (a list 3.3 times longer cost 10 to 12 times more). That is the host's speed; the audit ran an emulator, and one diff per typed letter queues behind the last, which is the minute. The emulator ratio was not measured; the rig re-run (release item) is the proof on target.
`BudgetProbe` showed why a cap on item-callback calls is not a usable guard: 19,550 rows to the last 16 took 432 ms with only 292,143 callback calls, because the search loop runs without calling back. The guard therefore counts rows that differ, not calls. The audit's own numbers are kept in `s0025-extract/` of the same folder.

### 3.3 What the projection costs (read, not changed)

`StreamsViewModel` filters and sorts in a `combine` collected on `viewModelScope`, so on the main thread; it holds `parsedDescriptors`, a plain `HashMap` that `playLive` also touches, so moving the projection to another dispatcher would be a race, not a tuning. The audit measured the main looper free during the wait (ping p50 0 ms, max 5 ms) and the state arrives in about 2 s, so the projection is not the minute. Left as it is.

### 3.4 Contract boundary (closed)

None crossed: a list update strategy inside one screen; no format, wire shape or constant another product reads or reproduces.

### 3.5 Owner inputs (Approval gate)

None. Every choice below is reversible inside the ticket.

### Assumed (unattended, 2026-11-13)

- **Replace the list when more than 2,000 rows differ; diff otherwise.** Alternative weighed: an own keyed O(N+M) diff (longest increasing subsequence over the new rows' old positions) that keeps the scroll position and animation for large changes, which means replacing `ListAdapter` with an adapter owning its list and a concurrency scheme of its own - larger, and not provable between releases. Owner may overrule.
- **The threshold is 2,000 differing rows, found by trimming the unchanged head and tail, with a size-change shortcut.** Alternatives weighed: a cap on item-callback calls (refused after the measurement of 3.2), and a probe run off the main thread with a generous budget (a suspend submit, a re-plan loop and a race with committed diffs for a gain only in mid-size changes). Owner may overrule.
- **A replaced list shows from the top.** For a search that is the wanted view. The same applies to a change that moves rows across more than the budget: unpinning a channel that falls deep into the alphabet, or a catalog refresh that changes rows scattered through 19,550 (both leave the list at the top). Alternative: keep the scroll anchor by restoring the first visible key after the replacement. Owner may overrule.
- **The widget picker is parked, not fixed here** (S0071): another screen and adapter, same cause, not measured.

## 4. Plan

Coverage inventory: G1, G2 -> phases 2 and 3; G3 -> phase 1 (budget keeps small changes in the diff) and the tests of phase 4; G4 -> files of phase 5 list no resource; research items 3.1-3.4 closed; the three Assumed entries -> phases 1 and 2.
Produces/consumes: phase 1 produces `RowDiffBudget.withinBudget`; phase 2 consumes it and produces `StreamChannelAdapter.submitRows`; phase 3 consumes that; phase 4 consumes all three; phase 5 consumes nothing. No phase consumes a later product.

1. `phone/src/main/java/com/sza/streamsplayer/ui/streams/RowDiffBudget.kt` (new): pure `withinBudget(old, new, sameItem)`. Check: the file declares `fun <T> withinBudget`.
2. `StreamChannelAdapter.kt`: `areItemsTheSame` compares channel ids directly; new `submitRows` clears with `submitList(null)` when over budget. Check: `fun submitRows` declared, `RowDiffBudget.withinBudget` called in it.
3. `MainActivity.kt`: the one `submitList(state.rows)` becomes `submitRows(state.rows)`. Check: grep finds no `channelAdapter.submitList(`.
4. Tests (new): `RowDiffBudgetTest` (JVM) and `StreamChannelAdapterSubmitRowsTest` (Robolectric). Check: both files declare their classes.
5. `CHANGELOG.md`: one line under Fixed.

Files touched: the five above, `PLAN/S0070_catalog-search-stale-list.md` and its evidence folder `PLAN/S0070_catalog-search-stale-list/`.

### Release items

- RELEASE: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` - release builds and the Gradle phone suite, which now holds `RowDiffBudgetTest` and `StreamChannelAdapterSubmitRowsTest`. Not built, not tested until the release; the repository declares no compile-only command (`commands.compileCheck` absent in `.sza-profile.json`).
- RELEASE: the rig scene on an emulator with the 19,550-row catalog: `py -3 tools/audit/rig.py phase-b --serial emulator-5556 --apk phone/build/outputs/apk/store/debug/phone-store-debug.apk --run-id <new-id>`. Passing: SC-06 sample <= 1,000 ms, SC-20 `rows_beside_empty_message` 0, `clear_filter_ms` and `search_big_ms` not above the 2026-11-13 values (4,078 and 4,360 ms). On a pass, re-audit and move to Verified; on a miss, the likeliest remainder is the projection of section 3.3.

## Last Audit

**Date:** 2026-11-13 | **Mode:** static | **Outcome:** Verified
**Counts:** PASS 7 | WARN 0 | FAIL 0 | MANUAL 0 | UNCHECKABLE 0 | EXEMPT 2 | RELEASE 3

### Action items
1. Release build and phone unit suites - `pre-release-sweep.ps1` - not run between releases (RELEASE).
2. Rig scene SC-06 and SC-20 on the 19,550-row catalog - `tools/audit/rig.py phase-b` - not run between releases (RELEASE).
3. Release build compiles the change; the repository declares no compile-only command (RELEASE).

### Manual / on-target
- None open. The rig re-run above is a machine check; a pass moves the ticket to Verified.

