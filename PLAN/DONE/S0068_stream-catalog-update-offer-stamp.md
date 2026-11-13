# S0068 - Stream catalog update offer and installed stamp (FMS S4459) parked from S0065

**Ticket:** S0068
**Status:** BlockNeedUserTest
**Status note:** Implemented, not built: needs the release build, the phone unit suite and the six-step device walk of the Last Audit (fresh install, Close, later list, no network, the three policy positions)
**Complexity:** hard - confirmed at the spec pass 2026-11-08 and at the plan pass 2026-11-10 (seven phases, about twenty files): the port itself is small and known (four source classes with their tests, a new parameter on the import), but the screen it attaches to is not the source's screen, so the offer, its trigger and its focus routing are new wiring in `MainActivity` and `StreamsViewModel`; the source's refresh setting has no reader in this tree; and a network request the phone makes unasked changes the privacy page in its three locales and the Data safety source, in the same edit.
**Date:** 2026-11-08
**Verification level:** gate - the source's JVM tests for the offer rule, the publication client and the import, run at the release, plus a device walk: a fresh install, an installed list, a list published later, Close, Update, no network.
**Owner:** the StreamsPlayer Android session of this repository.

## Captured request and migration scope

Parked from S0065 (unattended queue pass pq2, 2026-11-08). FastMediaSorter added a stream catalog update offer after this tree was ported: the catalog import records the installed archive's publication stamp (ETag or Last-Modified of the response it merged) through StreamsSessionStore, and ResolveStreamCatalogOfferUseCase reads it so the Streams banner offers an update only for a different archive.

Symptom: the phone catalog import does not record an installed stamp and no offer exists in StreamsPlayer; the import file's drift row is DRIFT (source ce580d, port 879d70) with the stamp part decided and parked here (ledger decision row in docs/port-ledger.jsonl).

Evidence (read-only source, 2026-11-08): app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/ImportStreamCatalogUseCase.kt (lines 25, 50-52, 55-61, 178-191, 315-316 carry the stamp); data/repository/settings/StreamsSessionStore.kt (sha256 6d5f80...); domain/delivery/StreamCatalogPublication.kt (PublishedStreamCatalog.stampOf); data/delivery/StreamCatalogPublicationClient.kt; domain/usecase/streams/ResolveStreamCatalogOfferUseCase.kt with its test. None of these classes exists in this tree.

Why it is a separate ticket: a stream feature with a UI placement (the Streams banner) that the owner decides, not a drift fix. The writer alone would be dead weight (no reader), so writer and reader are carried together. STREAM-BANK does not name the stamp (the stream-catalog set has no ETag or Last-Modified text), so this is product behaviour carried under the full-port law (BOOTSTRAP_HANDOFF section 6a, item 1), not a contract amendment.

Owner decisions needed before the spec pass: where the offer shows in the Streams screen (banner, dialog or no offer), and whether the watch gets one (the watch's import does not carry the stamp in FastMediaSorter either).

## 1. Problem

The phone has one way to get or renew the channel list: the **Refresh catalog** entry in the overflow menu of the catalog screen. Nothing tells the user that a newer list exists, so a list published later reaches only the user who happens to press it. The same gap has a sharper form on a fresh install: the list is empty, the screen says "No channels found", and the action that fills it sits in the overflow menu. The watch does not have this gap on first use, because it downloads the catalog by itself when its list is empty.

FastMediaSorter closed the first gap with an offer resting on what is published: its import stores the archive's publication stamp, a cheap `HEAD` on open reads the stamp currently published, and a banner appears only for a stamp that is neither the installed one nor one whose offer the user closed. This tree has none of it, and the import's drift row stays decided against this ticket until the owner's answer turns it into a carry or into a permanent decision.

## 2. Goals, non-goals and constraints

Goals, all conditional on the answers in section 4:

- **G1 stamp.** A successful import records the publication stamp of the response it merged. A response with no usable header clears the stored stamp. A failed write costs one repeated offer at worst and never fails the import.
- **G2 offer rule.** The offer is decided by the source's rule, free of I/O and covered row by row: nothing for an unreachable mirror, the installed list or a closed offer; the published stamp otherwise; the file's own `Last-Modified` against the last import time for an install with an import and no stamp.
- **G3 offer on screen.** The form the owner picks (question 1) appears at most once per screen open, takes the same import path as the menu entry, and Close stores the stamp it was shown for, so a list published later is offered again.
- **G4 control.** Whatever the owner picks in question 3 decides whether the probe runs at all; no probe runs on the watch unless question 2 says so.
- **G5 surfaces.** Strings in en, ru and uk, the catalog help page and its `help-map.json` row, the privacy page, the Data safety source, README and CHANGELOG, in one edit with the code.
- **G6 tests.** The source's `ResolveStreamCatalogOfferUseCaseTest` and `StreamCatalogPublicationClientTest` come with the port, and the import's tests take the new parameter; they run at the release.

Non-goals: a change to how the import downloads, merges or prunes; the channel pictures offer that follows an import (S0050) and the control for them (S0064); the producer of the archive; the Windows product; any release.

Constraints that bind the work:

- `CLAUDE.md` section 2 (full port, never a rewrite): the classes are copied from the source and cleaned; each gets a port-ledger row; every deliberate delta is written here next to its reason. An uncarried source change stays a decision next to its ledger row, never silence.
- Canon invariant 14: the privacy page, the permission list and the Data safety source say the same thing. Section 3.3 is what this changes.
- Canon invariant 17: every surface and every authored locale in one edit, written as a shipped fact.
- Dead-weight rule: under answer C of question 1 every class of this port would be orphaned, so nothing is built before the answer. The writer alone has no reader, and the ticket's own capture says the two travel together.
- `CLAUDE.md` section 5a: nothing above 5 s runs between releases; the build, the unit suites and the device walk are RELEASE items.

## 3. Research findings (all closed)

### 3.1 What the source does (read-only, 2026-11-08)

- `PublishedStreamCatalog.stampOf(etag, lastModified)`: the ETag, else `lm:` plus `Last-Modified`, else null. The probe and the import share this one function, so a stamp written by one and compared by the other come from the same rule.
- `StreamCatalogPublicationClient`: `HEAD` on the very URL the import downloads, a 12 s call deadline of its own, no cache, and every failure (HTTP error, `IOException`, a call closed out of order) reads as "nothing to offer". Cancellation is rethrown.
- `ResolveStreamCatalogOfferUseCase`: `invoke()` honours a closed offer; `isUpdateAvailable()` does not, and serves the automatic policy (FastMediaSorter S4461).
- `ImportStreamCatalogUseCase`: takes the stamp from the same response whose bytes it merges, and records it after the collections step.
- `StreamsViewModel.onScreenOpened()`: once per view model, by policy. `MANUAL` does nothing; `ON_OPEN` runs the rule and raises the offer; `PERIODIC_WIFI` imports with no prompt when on Wi-Fi, the daily throttle has passed and the published list differs. `lastCatalogRefreshAt` is written after an import.
- `StreamsActivity`: the offer is a banner inflated on first use, with Update and Close, and D-pad focus routing through it (FastMediaSorter S2896). It was a Snackbar until FastMediaSorter S1823 measured that a Snackbar's timeout could cost a user the only path to a catalog.
- Setting: `streamsCatalogRefreshPolicy`, default `ON_OPEN`, a dropdown row "Updating the channel list" with "Only when I ask", "Suggest when I open" and "Automatically on Wi-Fi".
- The watch has no stamp and no offer in the source.

### 3.2 What this tree has (read 2026-11-08)

- None of `StreamsSessionStore`, `PublishedStreamCatalog`, `StreamCatalogPublicationSource`, `StreamCatalogPublicationClient`, `ResolveStreamCatalogOfferUseCase` exists. The import carries the retry and the SB-A7 refusal since S0065, and no stamp.
- `StreamsCatalogRefreshPolicy` was ported with S0003 and has no reader: no `AppSettings` field, no store key, no settings row (grep across `phone/src/main`).
- The catalog screen is this product's own `MainActivity`, not the source's `StreamsActivity`. There is no `onScreenOpened`. The only caller of the import is `onRefreshCatalog`, reached from the overflow item `menu_refresh_catalog` (`showAsAction="never"`). So a fresh phone install has no automatic path to a catalog, against the watch's automatic first download (`StreamsViewModel` in `wear`, recorded in S0038).
- `activity_main.xml` already has two banners above the list (`activeFiltersBanner`, `ownStatusBanner`), so a third has a place and a look to follow.
- `catalog_updated_summary` and the S0050 pictures offers already follow a successful import, so an accepted offer inherits them.
- No package has shipped (`PLAN/RELEASE_QUEUE_DONE.md` is empty), so no user has an import without a stamp today.
- The reference product is no help here: StreamsPlayer for Windows offers a built-in copy when the list is empty, and carries no `HEAD`, `ETag` or `Last-Modified` in `StreamCatalogService.cs` or in its application sources (grep), so it has no update offer.

### 3.3 What an offer changes on the privacy surfaces (read 2026-11-08)

`docs/PRIVACY_POLICY.md` section 3 says the phone downloads the catalog "when you choose **Refresh catalog** in the menu". The Data safety source records the catalog flow as "Not declared: user-initiated, no user data in the request". A `HEAD` at screen open is neither: it is a request to GitHub with no action from the user, on every open of the catalog screen, under the source's default policy. It carries the IP address and the usual request details and nothing about the user. The two texts change in the same edit, and the Data safety decision for that row is re-read against the form's own wording rather than carried over. This is why question 3 exists.

### 3.4 The mirror's answer, measured

`curl -sIL --max-time 12 <STREAM_CATALOG_URL>` twice, 2026-11-08, exit 0 both: final `HTTP/1.1 200 OK`, `ETag: "0x8DF35A053A14D57"` on both answers, `Last-Modified: Thu, 29 Oct 2026 09:38:02 GMT`, `Content-Length: 7134396`. The stamp the source relies on is present and stable through the redirect, as the source measured on 2026-10-26.

### 3.5 Contract boundary (closed)

None crossed. The stamp is local state read from HTTP headers of a file this product already downloads; no format, wire shape or constant that another product reads changes. The `STREAM-BANK` document set names no ETag or `Last-Modified` (grep; the only hit in the set is the word `GenreTags`), and section 2 item 3 of `CLAUDE.md` does not list catalog offers among the behaviours written there. Behaviour carried under the full-port law, no catalog amendment.

### 3.6 Resolved without asking

Each holds whichever way section 4 is answered, unless the answer removes the feature.

- **The rule.** `decide` is copied whole, including the branch for an install with an import and no stamp. Test builds of this product have such installs, and the branch is covered by the source's tests. Settled by the full-port law.
- **Probe, not download.** `HEAD` on `DeliveryAssets.STREAM_CATALOG_URL`, as measured in 3.4.
- **HTTP client (delta).** This tree has no `@Named("linkDownload")` client. The port takes the shared `OkHttpClient` the import uses and keeps the source's own 12 s call deadline. Same behaviour, one less binding.
- **Session store (delta).** `StreamsSessionStore` carries only the last refresh time and the two stamps, in a DataStore file of the source's name. The source's sort, filter, display mode and scroll keys have no reader in this screen (grep for `writeFilterState`, `last_sort`), and porting them would be dead weight.
- **Screen trigger (delta).** The source's `onScreenOpened` guard moves into this tree's `StreamsViewModel`: once per view model, so a rotation does not ask again. Called from `MainActivity`.
- **Update path.** The same `onRefreshCatalog` the menu runs, so the pictures offers follow it unchanged. A failed import after Update shows the existing failure message, and the offer returns on the next open because its stamp was neither installed nor closed.
- **Under the Mine filter.** The offer is not gated by the filter chip, as in the source: it is about the catalog, and the import does not touch the user's own rows.
- **Focus.** The source's D-pad routing through the banner is carried under `INPUT-PARITY`, adapted to this screen's view ids.
- **Tests that take the new parameter.** `ImportStreamCatalogUseCaseTest`, `FaviconAtlasStoreTest` and the two view model tests that construct the import or the view model.

## 4. Open questions (owner)

### Quiz decisions (2026-11-10)
- What form does the update offer take on the phone -> Option A (Source banner above the list with Update and Close buttons; non-blocking, visible path for fresh install).
- Does the watch get an offer -> Option A (No: watch keeps existing behavior without probe or banner, saving battery and screen space).
- What switches the automatic probe off -> Option A (Source setting whole on General page with 3 positions: "Only when I ask", "Suggest when I open" [default], "Automatically on Wi-Fi"; full port of enum and honest switch for privacy policy).

1. **What form does the update offer take on the phone?** `Status: Resolved (2026-11-10, quiz - Option A).`
   - **A. Recommended - the source's banner above the list**: "Update the channel list?" with Update and Close, stays until taken or closed. It is the port, it has a place next to the two banners already on this screen, and because the rule offers the first catalog too, it also gives a fresh install a visible way out of the empty list. The cost is the unasked request of section 3.3 and the surfaces it changes.
   - **B. A dialog at screen open.** Harder to miss and no room taken from the list. Not in the source (a deliberate divergence, written next to the ledger row), and it interrupts the first thing the user came to do on every newly published list.
   - **C. No offer.** Nothing is ported, no request is added and the privacy texts stay as they are. The ledger row stays decided for good, naming this answer, and the ticket closes. A fresh install keeps only the overflow entry; if that alone is worth fixing, it becomes its own ticket.
2. **Does the watch get an offer?** `Status: Resolved (2026-11-10, quiz - Option A).` Moot under 1C.
   - **A. Recommended - no.** The source's watch has none, the watch already fetches by itself on an empty list and on pull to refresh, and a probe would cost a small battery and a small screen for an offer the phone already makes. The watch's import row stays as it is.
   - **B. Yes.** New watch behaviour with no source twin: its own stamp store, a probe, a surface on the watch, a watch help row and watch lines in the privacy texts.
3. **What switches the automatic probe off?** `Status: Resolved (2026-11-10, quiz - Option A).` This question was not in the capture; the research raised it (sections 3.2, 3.3). Moot under 1C.
   - **A. Recommended - the source's setting whole**: one row on the General page, "Updating the channel list", with "Only when I ask", "Suggest when I open" (default) and "Automatically on Wi-Fi". It keeps the full port, gives the privacy page an honest off switch, and brings `StreamsCatalogRefreshPolicy` to life. The costs are a row on the page whose inventory the owner settled in S0049, and an automatic position that downloads about 7 MB with no tap.
   - **B. The same row with two positions**, "Only when I ask" and "Suggest when I open". No download ever happens without a tap, and the source's FastMediaSorter S4461 part stays uncarried as a written decision. The enum loses a value, a deliberate divergence next to its ledger row.
   - **C. No setting**: the offer is always on and Close is the only control. Smallest change, no settings row, and the enum is deleted as dead weight. The privacy page has to say that the request runs on every open with no way to stop it.

## 5. Scope map for the plan pass (not a plan)

Independent of questions 2 and 3, once question 1 is not C: the four source classes and their tests, the import's stamp with its parameter, the session store, the screen trigger, the offer on `MainActivity` with focus routing, en, ru and uk strings, the port-ledger rows for each file and the refresh of the import's row, `docs/PRIVACY_POLICY.md` section 3 and its translations, `docs/play/PLAY_DATA_SAFETY.md`, `docs/help/content/catalog.json` with its `help-map.json` row, README and CHANGELOG. Depends on question 2: everything on the `wear` module. Depends on question 3: `AppSettings`, the settings store and its row, or the removal of the enum.

Nothing is built yet: under answer C of question 1 every line would be dead weight, and the form of the offer decides most of the screen work.

## 6. Plan (2026-11-10, unattended pass pq2)

Answers 1A, 2A, 3A. The watch is untouched (2A).

### 6.1 Coverage inventory

| Line | Phase |
| --- | --- |
| G1 stamp | P1 (stampOf, session store), P2 (import) |
| G2 offer rule | P1 (`ResolveStreamCatalogOfferUseCase` whole, `decide` included) |
| G3 offer on screen, once per open, same import path, Close stores the stamp | P3 (view model), P4 (banner) |
| G4 control: the policy decides whether the probe runs; no watch probe | P3 (policy read), P5 (setting); watch out-of-scope: answer 2A |
| G5 surfaces | P6 |
| G6 tests | P1 (two ported tests), P2 (import tests take the parameter); run at the release |
| Full-port law, ledger rows | P1, P2, P5 (each ported file recorded or refreshed), P7 |
| Invariant 14 privacy / Data safety | P6 |
| Invariant 17 every surface and locale | P5 (strings en/ru/uk), P6 |
| Dead-weight: the enum gets its reader | P3, P5 |
| 5 s rule: build, suites, device walk at release | P7 (RELEASE items) |
| 3.6 deltas: shared `OkHttpClient`, reduced session store, trigger in this view model | P1, P3 |

### 6.2 Phases (produces -> consumes)

- **P1 port the domain and data classes.** Produces `PublishedStreamCatalog` + `StreamCatalogPublicationSource`
  (`domain/delivery/StreamCatalogPublication.kt`), `StreamCatalogPublicationClient` (`data/delivery/`), its binding in
  `core/di/DeliveryModule.kt`, `StreamsSessionStore` (`data/repository/settings/`, delta: refresh time and two stamps
  only), `ResolveStreamCatalogOfferUseCase`, the two ported tests. Consumes nothing new. Check: files exist, symbols
  grep.
- **P2 import records the stamp.** Consumes P1. `ImportStreamCatalogUseCase` carried to the source version
  (ce580d): `sessionStore` parameter, `stamp` on the payload, `recordInstalledStamp`. `ImportStreamCatalogUseCaseTest`
  and `FaviconAtlasStoreTest` take the parameter. Check: diff against the source is the package rename only.
- **P3 the policy and the offer in the view model.** Consumes P1, P2, and `AppSettings.streamsCatalogRefreshPolicy`
  produced here with its store key in `SettingsRepositoryImpl`. `StreamsViewModel` gains `onScreenOpened()`
  (once per view model), `onCatalogOfferAccepted()`, `onCatalogOfferDismissed()`, `catalogOffer` in the UI state
  (delta: state, not a one-shot event, so a rotation keeps the banner), `writeCatalogRefreshAt` after a completed
  import. New constructor parameters are nullable with defaults, appended, like the existing optional ones, so the
  positional test constructors are unchanged. Check: symbols grep.
- **P4 the banner.** Consumes P3. `view_streams_catalog_banner.xml` ported (ids kept), a `ViewStub` in
  `activity_main.xml` above `activeFiltersBanner`, `MainActivity` calls `onScreenOpened()`, renders the state,
  Update and Close, D-pad routing between the search field, the chips and the banner. Strings
  `streams_catalog_refresh_suggestion`, `streams_catalog_refresh_action` in en/ru/uk. Check: ids grep in layout and
  activity.
- **P5 the setting.** Consumes P3. `StreamsCatalogRefreshPolicy` refreshed to the source (doc comment only), a
  `SettingsDropdownRow` `rowCatalogRefresh` on General after the broadcasting switch, bound in
  `SettingsActivity.buildGeneralPage`; four strings en/ru/uk. Check: grep.
- **P6 surfaces.** `docs/PRIVACY_POLICY*.md` section 3 (en/ru/uk) with translation fingerprints recorded;
  `docs/play/PLAY_DATA_SAFETY.md` row; help `catalog.json` (element `catalog-offer`, appended) and `settings.json`
  (element `catalogRefresh`, appended) in three locales, pages regenerated, fingerprints recorded; README, CHANGELOG.
  The help map needs no new row: the banner is part of `MainActivity`, the row part of `SettingsActivity`. Check: the
  fast doc gates and `generate-help-pages.ps1 -Check`.
- **P7 ledger and audit.** `record-port.ps1` for every new file, `-Refresh` for the import and the enum;
  `StreamsViewModel`'s row stays DRIFT for its other uncarried changes, with this ticket's part named in the audit.

### 6.3 Self-review

Every inventory line maps to a phase; no phase consumes a later product (the setting's field is produced in P3, its
row in P5). Every step ends in a grep, a diff or a gate exit code.

### 6.4 What was built (2026-11-10)

- Ported whole (package rename only): `StreamCatalogPublication.kt`, `ResolveStreamCatalogOfferUseCase.kt`, both
  ported tests, and the import, the enum and `FaviconAtlasStoreTest` carried to the source's current version.
- Ported with a written delta: `StreamCatalogPublicationClient` takes the shared `OkHttpClient` (3.6);
  `StreamsSessionStore` keeps only the refresh time and the two stamps (3.6); the test's `Session` follows it; the
  banner layout uses this tree's text-button style, `ic_close`, `action_close`, a 48 dp close target and no
  `nextFocus` ids of the source's screen (its controls do not exist here; the banner sits in the vertical order
  between the chips and the list, so D-pad up and down follow the layout).
- Deltas in this tree's own files: the offer is UI state (`catalogOffer`), not a one-shot event, so a rotation keeps
  the banner; a completed import from the menu also clears a banner still on screen; `onScreenOpened` and the closed-
  offer write catch a store failure instead of crashing the screen (the source lets it propagate); the policy row is
  on General after the broadcasting switch.
- `StreamsViewModel`'s ledger row stays DRIFT: its other uncarried FastMediaSorter changes are not this ticket's,
  and the S4459 / S4461 part is carried here by hand into this tree's own catalog view model.

## Last Audit

**Date:** 2026-11-10 | **Mode:** static, pre-handoff | **Outcome:** BlockNeedUserTest
**Counts:** PASS 11 | WARN 1 | FAIL 0 | MANUAL 6 | UNCHECKABLE 0 | EXEMPT 1 | RELEASE 4

1. PASS - G1/G2 port: `diff <(sed 's/fastmediasorter/streamsplayer/g' <source import>) <port import>`, exit 0;
   expected identical | actual identical. Same for `FaviconAtlasStoreTest` (exit 0, identical).
2. PASS - ledger: `record-port.ps1` exit 0 for seven new rows (publication, client, offer use case, session store,
   banner layout, two tests) and `-Refresh` exit 0 for the import (879d70 -> ce580d), the enum (4e213e -> aa9fd1) and
   `FaviconAtlasStoreTest` (ffb488 -> 5fb02b); expected none of them in the `check-drift.ps1` DRIFT list | actual
   none (the run lists 140 uncarried rows of other files).
3. PASS - wiring grep: `stubCatalogBanner`, `btnCatalogBannerAction`, `btnCatalogBannerDismiss` in layout and
   `MainActivity.kt`; `rowCatalogRefresh` in `page_settings_general.xml` and `SettingsActivity.kt`;
   `StreamCatalogPublicationSource` bound in `DeliveryModule.kt`; expected each present | actual each present.
4. PASS - strings: six keys, each once in `values`, `values-ru`, `values-uk`; expected 1/1/1 | actual 1/1/1.
5. PASS - `pwsh -NoProfile -File tools/docs/assert-translation-freshness.ps1`, exit 0; expected PASS after the
   three-language edit was recorded | actual `PASS (blocks=224 locales=ru,uk)`; before `-Record` it named exactly
   the eight blocks of this edit.
6. PASS - `assert-doc-registry` exit 0 (`PASS (records=30 files=269 ignore-lines=5)`), `assert-doc-links` exit 0,
   `assert-doc-style` exit 0, `assert-termbase` exit 0.
7. PASS - `pwsh -NoProfile -File tools/help/generate-help-pages.ps1 -Check`, exit 0; expected pages equal their
   sources | actual `PASS (checked=37 pages=11 ..)`.
8. PASS - `pwsh -NoProfile -File tools/site/build-site.ps1 -Check`, exit 0 after the regeneration; expected the
   hosted privacy pages equal the sources | actual `OK site: 3 pages ..`.
9. PASS - invariant 14: privacy section 3 (en, ru, uk) and the Data safety table name the check, its off switch and
   the automatic download; the permission list is unchanged (no new permission: `INTERNET` already covers it).
10. PASS - `pwsh -NoProfile -File tools/release/check-syntax.ps1`, exit 0; no release script changed.
11. PASS - probe invariant: `rg 'S0068' phone/src` shows three `Timber.d("S0068: ..")` probes, each alone on its line,
    and the ticket enters BlockNeedUserTest.
12. WARN - `pwsh -NoProfile -File tools/help/assert-help-map.ps1 -CommittedBaseline tools/help/help-map-baseline.txt`,
    exit 1: `STALE-SHOT catalog` is this ticket's (the banner joined `activity_main.xml` and the marker list); the
    four other STALE-SHOT findings (broadcast, broadcast-settings, filter-and-sort, qr-scanner) were there before it.
    Without the flag the gate exits 2, `COULD NOT VERIFY`, until the baseline is committed.
13. EXEMPT - the watch: answer 2A, nothing on `wear` changed.
14. RELEASE - `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1`: not built, not tested until the release.
    It runs the phone build (Hilt wiring of the new nullable view-model parameters and the binding), the phone unit
    suite with `ResolveStreamCatalogOfferUseCaseTest`, `StreamCatalogPublicationClientTest`,
    `ImportStreamCatalogUseCaseTest`, `FaviconAtlasStoreTest` and the three view-model tests.
15. RELEASE - `pwsh -NoProfile -File tools/help/capture-help-screenshots.ps1 -Serial emulator-NNNN -Help catalog`
    and `-Help settings`, then `generate-help-pages.ps1`: the pictures of both pages, with the new markers.
16. RELEASE - `pwsh -NoProfile -File tools/port-ledger/check-drift.ps1` at the release: the `StreamsViewModel` row.
17. RELEASE - `tools/docs/assert-docs.ps1` (6.7 s) at the release.

MANUAL - the device walk (release build on a phone, logcat filtered on `S0068`):

- [ ] Fresh install, empty list: the bar **Update the channel list?** appears above the list; **Update** fills it,
      the pictures offers follow; reopening the screen shows no bar.
- [ ] Installed list, nothing newer published: no bar on open; `S0068: catalog refresh policy applied` in the log.
- [ ] **Close** on the bar: it hides, focus is not lost, and the same list is not offered on the next open.
- [ ] A list published later (or the stored stamp cleared): the bar comes back.
- [ ] No network: no bar, no error message, the screen works.
- [ ] Settings > General > **Updating the channel list**: **Only when I ask** shows no bar and sends no request (proxy
      or log); **Automatically on Wi-Fi** on Wi-Fi with an older list imports without a tap; the choice survives a
      restart; rotation keeps a shown bar; D-pad reaches **Update** and **Close** from the chips.

### Owner inputs (Approval gate)

- **Related tickets:** S0065 (parked this; its decision row in the port ledger names this ticket), S0003 (stream core port, the source of the import and of the unread enum), S0050 (the pictures offers that follow an import), S0049 (the settings screen and its destination inventory), S0064 (a parallel question about growing the General page), S0033 (the privacy page and the Data safety source), S0035 (in-app help and the help map), S0047 (the first release, which sets when any of this reaches a user).
