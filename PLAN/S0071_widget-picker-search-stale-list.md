# S0071 - widget-picker-search-stale-list

**Ticket:** S0071
**Status:** Implemented
**Status note:** Implemented, no compile command (commands.compileCheck absent in .sza-profile.json), not built, not tested until the release: pre-release-sweep.ps1 (phone suite with RowDiffBudgetTest) and a timed picker search on a 19,550-row catalog (no rig scene for the picker yet)
**Complexity:** light - one call site and a test reusing the S0070 helper
**Date:** 2026-11-13

## Captured request and migration scope

Parked from S0070 (catalog search shows a stale list). The widget channel picker has the same defect class as the catalog screen: StreamLaunchWidgetConfigActivity.filterChannels() hands every keystroke's filtered list of up to 19,550 StreamSourceEntity rows to a ListAdapter (ConfigChannelAdapter) through submitList, so each keystroke queues a DiffUtil run whose cost grows with the square of the rows that differ and cannot be cancelled. Expected: a search typed in the picker over the full catalog reaches the list at once; actual (by the same mechanism measured for the catalog in S0070, not yet measured here): the old rows stay on screen while the diffs queue.

Evidence: S0070 section 3 (JVM probe: DiffUtil 6,000 rows 43 ms, 19,550 rows 532 ms, roughly quadratic; kept in PLAN/S0070_catalog-search-stale-list/probe). The fix pattern is S0070's RowDiffBudget in phone/src/main/java/com/sza/streamsplayer/ui/streams/RowDiffBudget.kt (a change touching more than 2,000 rows replaces the list: submitList(null) then submitList(rows)); ConfigChannelAdapter keys on StreamSourceEntity.id. Scope: one call site plus a test; reuse the S0070 helper, do not copy it.

## Problem

The widget channel picker hands every keystroke's filtered list of up to 19,550 rows to `ConfigChannelAdapter` through `submitList`. Each keystroke queues a DiffUtil run that cannot be cancelled, so a search over the full catalog lags behind the typing and the rows of earlier keystrokes stay on screen. S0070 fixed the catalog screen with `RowDiffBudget`; the picker has the same mechanism. Measured on the catalog screen only (S0070); not measured on the picker.

## Approach

- `filterChannels()` checks `RowDiffBudget.withinBudget(adapter.currentList, filtered)`, keyed by `id` as the adapter's `DiffCallback` keys its rows. Over the budget it calls `submitList(null)` before `submitList(filtered)`. The helper is reused, not copied.
- `RowDiffBudgetTest` gets one case over a 19,550-row list of `StreamSourceEntity` keyed by id: a 16-row search replaces the list, a one-row change stays within budget.
- `CHANGELOG.md` gets one line under Fixed, after S0070's line.

Files touched: `phone/src/main/java/com/sza/streamsplayer/widget/StreamLaunchWidgetConfigActivity.kt`, `phone/src/test/java/com/sza/streamsplayer/ui/streams/RowDiffBudgetTest.kt`, `CHANGELOG.md`, and this ticket.

Rung: primitive spec. Criteria, all true: three existing files change (the activity, the existing test file, the ledger); no new file; no new public type; no schema, DI wiring, screen or route; no deferred decision; a delta of about 30 lines. Contract boundary: none (a list update strategy inside one screen; no format, wire shape or constant another product reads). The activity has no row in `docs/port-ledger.jsonl`, so no port row changes.

## Done criteria

1. `filterChannels` calls `RowDiffBudget.withinBudget` and `submitList(null)` before `submitList(filtered)`. Check: grep of the activity.
2. `RowDiffBudgetTest` declares the id-keyed case. Check: grep of the test file.
3. The CHANGELOG Fixed line is present. Check: grep of `CHANGELOG.md`.
4. No `Timber.d("S0071` probe in code, since the ticket is not in the needs-manual-test status. Check: grep of the source roots.
5. `assert-doc-registry` and `assert-doc-style` PASS. Check: the two gates, exit 0.
6. RELEASE: build, phone suite and a picker search on a 19,550-row catalog (see Last Audit).

### Assumed (unattended, 2026-11-13)

- **The budget check sits in `filterChannels`, the one call site, not in a new `submitChannels` method on `ConfigChannelAdapter`.** Alternative weighed: a method mirroring `StreamChannelAdapter.submitRows`, which keeps the rule in one place for future callers. The ticket scopes one call site. Owner may overrule.
- **The new test case goes into the existing `RowDiffBudgetTest.kt`, not into a new Robolectric test of the activity.** Alternative weighed: an activity test needs Hilt, the widget manager and Room, and cannot run between releases; the call site is therefore proven by static grep until the release run. Owner may overrule.
- **A replaced picker list shows from the top**, as S0070 assumed for the catalog: a search is wanted from the top. Alternative weighed: keep the scroll anchor. Owner may overrule.
- **The CHANGELOG line is written with the code** (Stage 6; S0070 precedent). Alternative weighed: leave the ledger line to the release. Owner may overrule.

## Last Audit

**Date:** 2026-11-13 | **Mode:** static | **Outcome:** Verified
**Counts:** PASS 7 | WARN 0 | FAIL 0 | MANUAL 0 | UNCHECKABLE 0 | EXEMPT 1 | RELEASE 3

Verified is the static verdict only; the status stays `Implemented` until the RELEASE items run.

### Action items
1. Release build compiles the change; the repository declares no compile-only command (`commands.compileCheck` absent in `.sza-profile.json`) - RELEASE: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1`.
2. Phone unit suite with the new `RowDiffBudgetTest` case - RELEASE: the same sweep (Gradle phone suite; measured 73 s filtered and 132 s full on 2026-10-25).
3. A picker search over the 19,550-row catalog reaches the list at once - RELEASE: manual run on an emulator; no rig scene covers the widget picker (`tools/audit/auditrig/phase_b.py` covers the catalog screen only), so the check is a timed run by hand until a scene exists.

### Manual / on-target
- None open. The picker check above is a RELEASE item, not a manual one.

