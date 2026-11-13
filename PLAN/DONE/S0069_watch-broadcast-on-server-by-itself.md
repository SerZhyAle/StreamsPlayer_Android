# S0069 - The watch announces its broadcast on the exchange server by itself (DEVICE-EXCHANGE 1.6 DT, DU) parked from S0021

**Ticket:** S0069
**Status:** BlockNeedUserTest
**Status note:** Development complete 2026-11-10 (watch: every broadcast announced from its start, no switch, control stream held while served, DU short line and network-only line), not built: needs the release sweep (wear suites, ExchangeTermsDictionaryTest) and a watch run with a member entry plus the S0022 battery note.
**Complexity:** medium - rewritten at the spec pass of 2026-11-10 and confirmed by the plan: the watch's exchange runtime, presence holder, broadcast service and screen, their tests, three string sets and the watch's help, privacy, portal and README text in three languages; no wire, contract or schema change.
**Date:** 2026-11-10
**Verification level:** static between releases; the wear unit suites at the release; a rung 4 observation on a watch with a member entry (the account's directory lists the broadcast with no step).
**Owner:** the StreamsPlayer Android session of this repository.

## Remaining testing

*As of 2026-11-10 (unattended queue pass). Development is complete; the release build and suites and a run on a watch remain.*

1. The wear store and noLegal suites with `WearExchangePresenceTest`, `StreamsViewModelOwnTest`, and the phone suite's
   `ExchangeTermsDictionaryTest` - not built, not tested until the release.
2. The rung 4 observation of item DT on a watch with a `member` entry, and the battery note of S0022 over a broadcast that
   now holds the control stream.
## Captured request and migration scope

Parked from S0021 (unattended queue pass, 2026-11-10). S0021 carried `DEVICE-EXCHANGE` 1.6 items DS, DT and DU on the
phone after the owner's quiz decision of 2026-11-10 ("follow 1.6 as written: drop the switch, assign the outgoing
entry automatically, announce every broadcast from its start, add the server-and-state line"). Item DT binds "the
phone and the watch" of this product (amendment of 2026-10-21, DT bullet "The rule binds every product that declares
`broadcaster`"); the watch side is S0022's code, which is outside S0021's scope ("this ticket changes nothing on the
phone's own path" for the watch, S0021 scope 9).

Symptom: the watch still waits for a per-broadcast switch before it announces. `WearExchangeRuntime` combines the
broadcast state with `mutablePublish` (set by `setPublishEnabled`, reset to `false` after every broadcast) and returns
without `startAndAwait` while it is false; the watch's Broadcast screen shows `exchange_watch_publish_notice` until it
is turned on.

Evidence (this tree, 2026-11-10): `wear/src/main/java/com/sza/streamsplayer/wear/data/exchange/WearExchangeRuntime.kt`
lines 42, 63-76 (`mutablePublish`, `next.enabled`); `wear/.../ui/broadcast/WearBroadcastScreen.kt` lines 150-163 (the
state line in its short form, item DU's watch clause); `docs/PRIVACY_POLICY*.md` section on the watch ("Publishing
through the server is off for each broadcast until you turn it on"), kept unchanged by S0021 because it is still
true of the watch.

Open for the spec pass: whether the watch's own presence rule (control held only from its visible broadcast and player
screens, `holdPresence`) stays as the watch's form of "keeps the control stream as 7.3 allows", and whether the owner's
2026-11-10 decision for the phone covers the watch as well (the quiz asked it for S0021 only).

## 1. Spec (spec pass 2026-11-10, unattended queue pass)

### Problem

`DEVICE-EXCHANGE` 1.6 (section 29) binds "StreamsPlayer_mob on the phone and the watch": every broadcast of a device with
an outgoing entry is announced on it from its start, with no switch (item DT); the device keeps the control stream while
the broadcast is served (item DT, item BM of 7.3); the broadcaster's control surface says where the broadcast went, the
watch in short form, and a device with no entry that may produce says the broadcast is reachable on this network only
(item DU). The phone does this since S0021 (2026-11-10). The watch still waits for a per-broadcast switch, holds the
control stream only while its screen is in front, and says nothing about "this network only".

### Goals

1. The watch's broadcast is announced on its own exchange entry from its start whenever that entry is connected and
   may produce (not a guest), with no switch and no step by the person (items DS, DT).
2. The control stream is held for as long as the broadcast is served, not only while a screen is in front (item DT
   "keeps the control stream as 7.3 allows", item BM).
3. The Broadcast screen carries one line about the server in short form (being announced, on the server, the failure
   with **Why won't it connect?**), and without an entry that may produce it says the broadcast is reachable on this
   network only, leading to **Connect watch** on the phone (item DU).
4. Every surface that describes the watch's switch or its screen-bound presence says the new rule, in EN, RU and UK.

### Constraints

- Shared-contract boundary: yes, `DEVICE-EXCHANGE` 1.6 items DS-DU, already in the catalog (amendment of 2026-10-21);
  no catalog amendment is needed and no wire changes (1.6 is "no wire change"). The registry row moves only after the
  rung 4 observation the amendment names, together with S0021's (a registry patch on top of S0057).
- Standing law section 2: the FastMediaSorter watch (`WearExchangeBroadcastAnnouncer`, `WearExchangePresence.isWanted
  = visible || broadcasting || castPlaying`) already announces every broadcast and holds presence while it is live;
  this change brings the behaviour in step. No new file is ported: the shared publisher of `:exchange-core` stays the
  carrier (S0022 extraction).
- Battery: the broadcast already runs a microphone foreground service with a wake lock; the control stream adds a
  keepalive of at least 10 s for the same span. Outside a broadcast nothing changes (screen-bound, S0022).
- No reach shrinks; no permission changes.

### Research - closed

- R1 (spec capture, open item 1): does the screen-bound presence rule stay during a broadcast? **No.** Item DT asks the
  broadcaster to keep the control stream "as 7.3 allows while the broadcast is served"; item BM allows "while a
  broadcast it started is being served"; the owner's own answer to ask H (proposal of 2026-10-10, second revision)
  reads "a phone holds its control stream while .. a published broadcast is on air"; the donor watch holds it the
  same way. Outside a broadcast the watch keeps S0022's screen-bound rule. Settled without asking.
- R2 (spec capture, open item 2): does the owner's 2026-11-10 decision for the phone cover the watch? **Yes, by the
  contract**: the decision was "follow 1.6 as written", and 1.6 item DT names "StreamsPlayer_mob on the phone and the
  watch"; CLAUDE.md section 2 point 3 makes the contract text the behaviour of both products. Settled without asking.
- R3: DS on the watch. The watch holds one entry; that entry is the outgoing one whenever it is connected and not a
  guest. Nothing to assign or migrate.
- R4: DU "short form" on the watch. The line gives the state only; the server's name and the details stay on the phone
  (item DA), as the capture reads lines 150-163.

### Owner inputs

- Watch publishing rule: follow `DEVICE-EXCHANGE` 1.6 as written on the watch too (the owner's quiz decision of 2026-11-10 for S0021, read with item DT naming the watch; R2).
- Watch presence during a broadcast: held while the broadcast is served (the owner's answer to ask H, 2026-10-10; R1).

### Related tickets

- S0021 (the phone side of items DS-DU; parked this ticket). S0022 (the watch's exchange device, the screen-bound presence this ticket narrows to outside a broadcast). S0038 (the Own list holding the connection only).

## 2. Plan

### Coverage inventory and topology

| Line | Phase / step | Static check |
| --- | --- | --- |
| Goal 1, DS/DT: no switch, announce while live and the entry is connected and not a guest | A1 `WearExchangeRuntime`: drop `mutablePublish`, `publishEnabled`, `setPublishEnabled`; gate on `isGuest` | `publishEnabled\|setPublishEnabled\|mutablePublish` grep to nothing in `wear/src` |
| Goal 2: control stream held while served | A1 `holdPresence = true`, the visibility input leaves the gate | read of `WearExchangeRuntime` |
| Dead weight: `WearExchangePresence.wanted` has no reader after A1 | A2 remove `wanted`; tests read `connection` | `presence.wanted` greps to nothing in `wear/` |
| Cellular server-only start read the switch | A3 `VoiceRecordingService` reads `WearExchangeRuntime.hasOutgoingEntry()` | grep |
| Goal 3, DU short form and network only | B1 `WearBroadcastScreen`, `WearBroadcastViewModel`: toggle removed; line for a producing entry; `exchange_watch_network_only` otherwise | read; string present in EN, RU, UK |
| Strings | B2 remove `exchange_watch_publish`, rewrite `exchange_watch_publish_notice`, add `exchange_watch_network_only` (EN, RU, UK) | XML parse: equal key sets in three locales |
| Terms test list | B3 `ExchangeTermsDictionaryTest` drops the removed key | grep |
| Goal 4: surfaces | C1 help source `watch.json` (EN, RU, UK) and regenerated pages; `docs/PRIVACY_POLICY*.md` section 8 and the site; `docs/PORTAL_SECTIONS_STREAMSPLAYER.md` `watch-exchange`; `README.md`; `CHANGELOG.md` | `generate-help-pages -Check`, `build-site -Check`, the five document gates |
| Registry row and the S0026 exceptions DS-DU | out-of-scope: after the rung 4 observation, with S0021's patch | - |

No phase consumes what a later one produces: A1 before A2 and A3; B1 consumes A1's API; C1 last.

Self-review: every inventory line maps to a step; the symbols consumed (`ExchangeAccountEntry.isGuest`,
`ExchangeAccountState.Connected`, `BroadcastExchangePublisher.Session.holdPresence`, `WearExchangeEntryStore.getAccountEntry`)
exist at the paths read on 2026-11-10.

## 3. What changed

- `wear/.../data/exchange/WearExchangeRuntime.kt`: the switch (`mutablePublish`, `publishEnabled`, `setPublishEnabled`) and
  its reset after each broadcast are gone; a live broadcast is announced whenever the watch's entry is connected and not a
  guest and the watch is on its own network; `holdPresence = true`, so the publisher holds the control stream for the
  broadcast's span; `hasOutgoingEntry()` (item DS); probe `S0069: watch broadcast announced on the outgoing entry without a switch`.
- `WearExchangePresence.kt`: `wanted` removed (its one reader left); `connection` stays the screen-bound holder outside a
  broadcast. `WearExchangePresenceTest` reads `connection`; `StreamsViewModelOwnTest` drops its `wanted` assertion.
- `VoiceRecordingService.openBroadcastSession`: the cellular server-only start reads `hasOutgoingEntry()`.
- `WearBroadcastScreen.kt`, `WearBroadcastViewModel.kt`: the toggle chip is gone; a producing entry gets the state line
  and **Why won't it connect?** as before; otherwise `exchange_watch_network_only`, plus the Connect watch hint when the
  watch is not connected.
- Strings EN/RU/UK (`exchange_watch.xml`): removed `exchange_watch_publish`; rewrote `exchange_watch_publish_notice`;
  added `exchange_watch_network_only`. `ExchangeTermsDictionaryTest` drops the removed key.
- Text: help source `watch.json` (`broadcast`, three languages, pages regenerated), `docs/PRIVACY_POLICY*.md` section 8
  (site rebuilt), `docs/PORTAL_SECTIONS_STREAMSPLAYER.md` `watch-exchange`, `README.md`, `CHANGELOG.md`; translations recorded.

## Last Audit

**2026-11-10, unattended queue pass, static (pre-handoff).** Not built, not tested until the release: this repository has
no measured fast Gradle form (CLAUDE.md section 5a).

Evidence run in this pass:

- `pwsh -NoProfile -File tools/help/generate-help-pages.ps1` - exit 0, `PASS (files=37 written=3 ..)`; `-Check` exit 0, `PASS (checked=37 pages=11 ..)`.
- `tools/site/build-site.ps1` exit 0; `-Check` exit 0, `OK site: 3 pages ..`.
- `assert-doc-registry` exit 0 `PASS (records=30 files=269 ..)`; `assert-doc-links` exit 0 `PASS (documents=42 pages=3 links=174)`;
  `assert-doc-style` exit 0; `assert-termbase` exit 0; `assert-translation-freshness` exit 1 with 6 findings, all this
  ticket's blocks (portal `watch-exchange`, help `watch` `elements.broadcast`, privacy `s08`, RU and UK), exit 0
  `PASS (blocks=224 ..)` after `-Record`.
- `tools/release/check-syntax.ps1` exit 0 `release-syntax: PASS`.
- `tools/help/assert-help-map.ps1 -CommittedBaseline tools/help/help-map-baseline.txt` exit 1, 5 `STALE-SHOT` (`broadcast`,
  `broadcast-settings` of S0021; `catalog`, `filter-and-sort`, `qr-scanner` of S0049) - the same five as before this
  ticket; the watch has no captured help surface.
- XML parse of the three `exchange_watch.xml`: 37 strings each, equal key sets; the relay term verbatim in
  `exchange_watch_publish_failed` and `exchange_watch_publish_notice` in EN, RU and UK.
- Grep: `publishEnabled`, `setPublishEnabled`, `mutablePublish`, `presence.wanted`, `exchange_watch_publish"` in
  `wear/src` and `phone/src` - no hit (exit 1). No long dash in the changed code, strings or help source.
- `spec_catalog/validate` exit 2 on one row not of this ticket: the journal names `PLAN/S0031_exchange-connection-diagnosis.md`, which is missing.

Contract coverage: goals 1-3 map to section 3's code lines, goal 4 to its text line; the registry row is out of scope
(section 2). Contract boundary: `DEVICE-EXCHANGE` 1.6 already holds items DS-DU; no wire change, no vector.

Verdict: development complete; the headline behaviour is written end to end in code and text but proven by no build or run.

- [ ] RELEASE: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` - both wear variants compile (the smart cast in
  `WearExchangeRuntime`, the removed ViewModel members), the wear suites including `WearExchangePresenceTest` and
  `StreamsViewModelOwnTest`, and the phone suite's `ExchangeTermsDictionaryTest`.
- [ ] MANUAL: a watch with one `member` entry starts a broadcast and leaves the Broadcast screen; the account's directory
  on the phone lists the broadcast with no step, and it stays listed with the watch display off. The screen shows
  connecting, then on the server. Logcat shows `S0069: watch broadcast announced on the outgoing entry without a switch`.
- [ ] MANUAL: a watch with no account shows "Reachable on this network only." and the Connect watch hint; signing out on the
  phone while the watch is on air ends the broadcast on the server and keeps it on the local network.
- [ ] MANUAL: the battery note of S0022 (FMS_A S4213 method) over a fixed broadcast time, now with the control stream held.
- [ ] After the rung 4 observation: the registry patch of S0021 also covers the watch side of items DS-DU.
