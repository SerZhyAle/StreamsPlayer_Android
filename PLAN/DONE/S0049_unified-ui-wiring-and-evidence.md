# S0049 - Unified UI: wire built components into screens and measure

**Ticket:** S0049
**Status:** BlockNeedUserTest
**Status note:** Development complete, not built: needs the release sweep, the help captures that clear 5 STALE-SHOT, and the device evidence of docs/design/UI_CONTRACT_CHECKS.md; see the RELEASE and MANUAL lines of Last Audit.
**Date:** 2026-10-27
**Phase:** unified UI contracts - remaining implementation and the measured proof S0036 did not produce; independent of the build phases of handoff section 8
**Verification level:** gate - the clause-to-surface table of section 4 held by rendered captures, geometry/contrast/accessibility checks and timed page switches, per the contracts' own conformance sections.
**Complexity:** hard - assessed at the spec pass and confirmed at the plan pass of 2026-11-08: a new settings screen with
three destinations, live theme and language, the broadcast settings moved off Broadcast Control, eight layouts migrated, and
contract evidence that only a release build on a device can produce.
**Owner:** the StreamsPlayer Android session of this repository. Owner decisions already taken for this product (S0036, 2026-10-26) are not reopened: no compact mode, 48 dp touch floor, the Wear OS permanent variant.

## Captured request and migration scope

The owner's order of 2026-10-17 (canon S0007) made `UI-SIZING`, `BUTTONS`, `BOOLEAN-OPTION`, `COLLAPSIBLE-GROUP` and `SETTINGS-NAVIGATION` mandatory without per-product opt-in. S0036 built the shared components and closed on 2026-10-26 with component-level unit tests. The S0037 audit of 2026-10-27 (`PLAN/S0037_contract-sync-20261017/audit.md`, section "Unified UI") read the tree against the contracts and found that the components exist but most of the product does not use them yet, and that the measured evidence the contracts require was not produced. This ticket carries that remainder to the registry date of the unified-UI migration entry, 2026-12-21, so that the entry has an open successor.

## 1. What the audit found (2026-10-27, tree as it stands)

1. `SettingsNavigationRail`, `SettingsDestination`, `SettingsAboutDestinationView` and `DiagnosticReportExporter` (phone, `ui/settings/`) are referenced only by their own unit tests; no activity, fragment or layout instantiates them. The product therefore has no reachable settings page with a rail and no About page (`SETTINGS-NAVIGATION` 2.0 sections 2 and 3).
2. `CollapsibleSectionsManager` / `CollapsibleSectionHeader` are referenced by no screen (`COLLAPSIBLE-GROUP` 1.1).
3. `CommandFlowLayout` is used by 5 of 33 layouts (`activity_broadcast_control`, `activity_exchange`, `dialog_exchange_diagnosis`, `sheet_sleep_timer`, `view_settings_about`); 14 other layouts still hold plain `MaterialButton` / `Button` / `ImageButton` rows (`activity_main`, `activity_qr_scanner`, `custom_stream_player_controls`, `dialog_import_broadcast`, `item_*`, `sheet_recently_played`, `sheet_stream_filter`, `view_settings_*`).
4. Boolean options: `SettingsToggleRow` is used by `fragment_settings_broadcast` (portrait and landscape). Three `MaterialCheckBox` in `activity_exchange` and one `CheckBox` in `sheet_stream_filter` do not use the shared component (`BOOLEAN-OPTION` 2.0).
5. No rendered capture, contrast measurement, TalkBack/keyboard walk or page-switch timing exists for any of the five contracts: the uncommitted scratch folder of S0036 holds four inventory scripts and a build log only. Unit tests cover geometry and state logic, not the contracts' section 5/6 evidence.

## 2. Contract baseline

Read the catalog's current text at the time of work and record the version implemented: `UI-SIZING` 1.1, `BUTTONS` 2.0, `BOOLEAN-OPTION` 2.0, `COLLAPSIBLE-GROUP` 1.1, `SETTINGS-NAVIGATION` 2.0, `ICON-RENDER` 0.24 (covered components), `CONTEXT-HELP` 0.1 (surface help stays reachable outside About). Cite by id and section; the catalog is located only through `CLAUDE.md`.

## 3. Work

1. **A reachable settings surface.** Decide with the owner (owner-decision item, asked once, in question-with-options form) what the product's first settings page holds (theme, language, the most essential settings) and whether the broadcast settings panel becomes a destination of it. Wire the rail, the About destination (description, running version from build metadata, license and privacy pages, help, official site, support/contact, user-initiated diagnostic export, download/update information, repository page, resource index, related products from one canonical inventory) and the group headers into it. Missing resources are implementation work, not invented links.
2. **Groups** where a screen has distinct topics (Broadcast Control, the exchange screen); a single-topic page needs no wrapper.
3. **Buttons**: migrate the 14 remaining layouts; answers in dialogs stay visible; equal row heights; semantic icons for substantive commands.
4. **Boolean options**: the four remaining checkboxes, with the help/toggle split where an explanation exists.
5. **Wear OS**: keep the recorded permanent variant; add the per-surface applicability record for the watch face (no configuration surface: say so in the profile).
6. **Evidence** (the contracts' sections 5/6): captures at smallest / ordinary / largest size, longest locale strings, light / dark / high contrast, RTL, narrow / wide; measured contrast; keyboard and TalkBack walks; negative cases (scroll versus tap, help versus toggle, repeated activation, focus return, large-text reflow, stale asynchronous results); page-switch timings with raw samples and maximum (warm p95 below 150 ms, first visit below 500 ms) on named configurations. Record device, build, locale, scale. The S0040 capture rig is the intended source.
7. Documents in the same edit: `docs/design/UI_SIZE_PROFILE.md`, `docs/help/content/` and `help-map.json` rows for any phone screen added or changed (`CLAUDE.md` section 5), the three authored locales.

### Owner decisions required before the settings phase

`SETTINGS-NAVIGATION` 2.0 section 3.1 already requires theme and language on the first page; it does not choose this product's essential settings. The current `AppSettings` model contains Wear companion, broadcasting, clock dimming and fullscreen system-UI choices, and broadcast settings currently appear on Broadcast Control. These facts do not decide product placement.

1. Which additional settings belong on the first page alongside theme and language?
   - Recommended: Wear companion and broadcasting; the remaining choices stay with their relevant tasks. This emphasizes the product's main device and broadcast capabilities.
   - All four existing choices: also place clock dimming and fullscreen system-UI there. This favors a single overview at the cost of a denser first page.
   - Theme and language only: keep the existing choices on task pages. This gives the shortest first page, but requires an owner decision on how the contract's essential-settings requirement is met.
2. Should broadcast settings also be a destination in the settings surface?
   - Recommended: add a Broadcast destination and retain access from Broadcast Control, sharing the same settings. This makes the options discoverable in both contexts.
   - Keep broadcast settings only in Broadcast Control. This preserves their current task context but leaves them outside the settings rail.
   - Move broadcast settings into the settings surface only. This centralizes configuration but removes the current direct control-screen access.

Both decisions were answered on 2026-11-08 (quiz block below).

### Quiz decisions (2026-11-08)

Tree facts read before asking (2026-11-08): only `enableBroadcasting` has an editor (`rowEnableBroadcasting` of the broadcast
settings panel); `enableWearCompanion` and `dimClockOverlayEnabled` are persisted and read but have no editor;
`hideSystemUiInFullscreen` is not persisted at all (`SettingsRepositoryImpl` neither reads nor writes it, the value is always
the default `true`); `enableWearCompanion` has no effect today (`supportsWearCompanion = false` in `MediaCapabilitiesModule`,
and the Data Layer path answers `isWearCompanionEnabled() = true` regardless); the theme follows the system and no theme
selector exists; the language mechanism is `LocaleHelper` / `UiLanguageCatalog` and no language picker was found by name.

- Decision 1, the first settings page beyond theme and language -> the broadcasting switch only: the page holds theme,
  language and `enableBroadcasting` (the ticket's earlier recommendation, Wear companion and broadcasting, is not taken: it
  would put a switch with no effect on the page, which `SETTINGS-NAVIGATION` 2.0 section 3 item 4 and the "no nonfunctional
  placeholders" rule forbid unless the capability flag is changed, which is outside this ticket)
- Decision 2, broadcast settings as a destination -> a Broadcast destination in the rail; Broadcast Control keeps a button
  that opens that destination and holds no embedded copy of the rows (one editor and two entry points, `SETTINGS-NAVIGATION`
  2.0 section 3 item 2)
- Settings that have no editor (`enableWearCompanion`, `dimClockOverlayEnabled`, `hideSystemUiInFullscreen`) -> they stay
  internal values with no editor and no new work here; the ticket's audit records them as not covered by section 3 item 2 of
  the contract, naming that the dim clock overlay cannot be switched on by a user today (an editor, or retiring the fields,
  is a separate ticket the owner may order)

**Consequences for the work** (written for the implementation pass; the quiz writes no code)
- Rail order: the first page (theme, language, broadcasting), then the Broadcast destination, then About as the final
  top-level page. Theme (system / light / dark, live, high contrast respected) and language (endonyms, no restart) are
  implementation work of section 3 item 1: the selectors do not exist yet or were not found.
- `enableBroadcasting` keeps one editor, on the first page. The Broadcast destination holds the remaining rows; how those rows
  present themselves while the switch is off is for the plan pass to settle inside the contract (one setting, one editor).
- The audit of this ticket carries the three editor-less settings as a named gap, never as conformance.

### Owner inputs (Approval gate)

- **Related tickets:** S0036 (built the shared components), S0037 (the audit that found the remainder), S0040 (the capture rig for the evidence), S0035 (help sources and `help-map.json` rows for any phone screen added or changed).
- **First settings page:** theme, language and the broadcasting switch (`enableBroadcasting`) - owner, 2026-11-08 (quiz, decision 1).
- **Broadcast destination:** a Broadcast destination in the rail, opened from Broadcast Control by a button, no embedded copy of the rows - owner, 2026-11-08 (quiz, decision 2).
- **Settings without an editor:** `enableWearCompanion`, `dimClockOverlayEnabled` and `hideSystemUiInFullscreen` stay internal values with no new work here and are named as a gap in the audit - owner, 2026-11-08 (quiz).

## 3a. Plan (2026-11-08)

Rung: complex - new files, a new screen and route, new DI use and more than three files changed. Contract boundary: none in
the wire sense - no format, stored value or algorithm another product reads changes; the change conforms the phone to the
five presentation contracts, and the catalog's adoption rows are not advanced by this pass (section 5, "a plan is not
conformance"). Stored preferences keep their keys and defaults; two new local keys (theme mode, last settings page) are
product-private. Research items: none open - the tree facts of the quiz block settle them.

### Design decisions settled inside the contracts (no owner question)

- **D1 Surface.** One settings screen with the shared rail and three top-level destinations in a fixed order: General,
  Broadcast, About (`SETTINGS-NAVIGATION` 2.0 section 3.1-3.3). No child level. Glyph treatment: monochrome for every
  destination (section 2.3 permits it; the rail tints selected and normal glyphs from theme roles).
- **D2 Narrow layout.** At a screen width of 600 dp and more the rail stays beside the page. Below 600 dp the rail is a
  dedicated navigation view: the page list fills the screen, a destination opens its page in the same window, the toolbar's
  Up returns to the list with the selection kept (section 2.6). Recorded in `docs/design/UI_SIZE_PROFILE.md`.
- **D3 Entry points.** The catalog toolbar overflow gets Settings (`app.settings`); Broadcast Control gets a Broadcast
  settings button (child navigation, trailing chevron) that opens the Broadcast destination. A screen opened at a named
  destination returns to its caller on Back.
- **D4 General page.** Theme (system / light / dark) applied live through the AppCompat night mode and kept in a synchronous
  preference read at process start; language (system default plus the endonym of every tag of `locales_config.xml`) applied
  without restart through `LocaleManager` on API 33+ and `AppCompatDelegate.setApplicationLocales` below; the broadcasting
  switch moves here from the broadcast layout, so it has one editor. High contrast is the platform's and is not overridden.
- **D5 Broadcast page.** The broadcast settings layout without the switch. While broadcasting is off its rows stay hidden as
  today and the page shows one explanation line with an Open General button. The publish and Drive choices, remembered per
  family (audio, camera) since S0021/S0029, become two explicit rows each, so no editor depends on a hidden mode.
- **D6 About.** The existing About view plus: download and updates (the variant's own page, from `Distribution`), the two
  related products as external links from the `SITE-FAMILY-MAP` canonical URLs, the external-navigation marker
  (`nav.open-external`, `ic_open_in_browse`) on every web link; one inventory, the view's companion object.
- **D7 Page state.** Page views are built on first visit and kept for the screen's life (scroll kept across switches); the
  last destination is remembered across openings and restored after layout; text rows commit when the screen pauses; a
  page-visit timing hook (activation to first drawn frame, trace section plus debug log, no ticket id) feeds section 5.4.
- **D8 Groups.** The exchange screen gets independent collapsible groups for its topics (Google Drive, exchange server
  account, broadcast list), first-use state expanded, state persisted. Broadcast Control shows one coherent set per state
  once the settings card leaves it: no wrapper (`COLLAPSIBLE-GROUP` 1.1 section 2.2), recorded in the profile.
- **D9 Buttons.** Rows of two or more commands move into `CommandFlowLayout`; full-width commands become content-width.
  Icon-only transport, corner-help and list-row controls stay icon-only (`BUTTONS` 2.0 section 2.4, familiar repeated
  commands in constrained areas) and are listed in the profile.
- **D10 Boolean options.** The three exchange checkboxes and the pinned-only filter become `SettingsToggleRow`; existing
  explanations become the row's explanation line.

### Coverage and topology

| Item | Phase | Produces | Consumes |
| --- | --- | --- | --- |
| Work 1, D1-D7, Acceptance 2 | P1 settings surface | `SettingsActivity`, its layouts, theme store, strings EN/RU/UK, manifest row, toolbar entry, Broadcast Control button | S0036 rail, About view, toggle and dropdown rows, broadcast panel manager |
| Work 2, D8 | P2 groups | exchange screen groups, group strings | S0036 collapsible header and manager |
| Work 3, D9 | P3 buttons | eight layouts migrated | `CommandFlowLayout` |
| Work 4, D10 | P4 boolean options | four rows migrated, callers adapted | `SettingsToggleRow` |
| Work 5 | P5 Wear record | profile section | - |
| Work 7, CLAUDE.md section 5 | P6 documents | profile, help sources, help map rows, generated pages, pointer | P1-P5 |
| Work 6, Acceptance 1, 3, 4 | P7 evidence | clause-to-check table, RELEASE and MANUAL items | P1-P6, a release build, a device |

No phase consumes what a later one produces. Acceptance 5 is held by the skill boundary: nothing here tags or uploads.

### Steps and static checks

- P1.1 Theme store and startup apply - `ThemeModeStore` exists; `StreamsPlayerApplication` calls it before any activity.
- P1.2 Settings layouts - `activity_settings.xml`, `page_settings_general.xml`, `page_settings_broadcast.xml` parse as XML.
- P1.3 `SettingsActivity` - declared in the manifest; destinations general, broadcast, about in that order.
- P1.4 Broadcast panel - `rowEnableBroadcasting` absent from `fragment_settings_broadcast.xml`; the panel binds four
  publish rows; Broadcast Control holds no `include` of the panel and has `btnBroadcastSettings`.
- P1.5 Entry from the catalog - `menu_settings` in `main_menu.xml` and handled in `MainActivity`.
- P1.6 About completion - download, related-product and external markers present in `view_settings_about.xml`.
- P1.7 Strings - every new key present in `values`, `values-ru`, `values-uk`.
- P2-P4 - each named layout parses; no `MaterialCheckBox` / `CheckBox` left in the two layouts; callers grep clean.
- P6 - `tools/help/assert-help-map.ps1` exits 0 with `PASS`.
- P7 - the evidence items are RELEASE / MANUAL lines of the audit; none is claimed.

Self-review (2026-11-08): every work item and acceptance line maps to a phase; every consumed symbol was read in the tree
this pass (`SettingsNavigationRail`, `SettingsAboutDestinationView`, `BroadcastSettingsPanelManager`, `CommandFlowLayout`,
`CollapsibleSectionsManager`, `SettingsToggleRow`, `SettingsDropdownRow`, `LocaleHelper`, `UiLanguageCatalog`,
`Distribution`, `HelpOpenManager`, `HelpSurfaceIds`). No pre-implementation blocker is open.

## 3b. Progress and results (2026-11-08, unattended queue passes)

Not built, not tested until the release (`CLAUDE.md` section 5a): nothing here has been compiled or run. Phases P1-P6 landed
in the first pass of the day, which ended before it wrote this down; the second pass re-read the tree, re-ran every predicate
and wrote the record. Results are in `## Last Audit`.

**Found and fixed by the second pass** (a code read against the contracts):

- `SettingsActivity` opened the page list on every opening below 600 dp, although `SETTINGS-NAVIGATION` 2.0 section 4.3, the
  help page and the changelog all say the screen reopens on the page left last. A later opening now lands on that page at any
  width; only the very first opening below the breakpoint starts at the list, and Up from the page returns to it. Recorded in
  the profile, section 5.
- The clause table had no row for `BUTTONS` 4.7 and 6, `BOOLEAN-OPTION` 6, `COLLAPSIBLE-GROUP` 6 or `UI-SIZING` 3 and 5, which
  Acceptance 1 counts as unmapped. Rows added.

**Done beyond the plan**, each because a clause needs it and each inside this ticket's surfaces:

- The rail row grew with its caption and lost `singleLine` and `ellipsize` (`SETTINGS-NAVIGATION` 2.0 section 2.2), says its
  role in the interface language instead of a literal, and keeps a visible focus ring when selected.
- Link semantics moved out of the About view into one `exposeAsLink` and now also cover the web links of the exchange screen
  and the viewer guide of Broadcast Control (`BUTTONS` 2.0 section 2.5).
- The viewport of each page is kept by anchor row and offset (`SETTINGS-NAVIGATION` 2.0 section 4.3), a third local key beside
  the two the plan named.
- `<profileable android:shell="true"/>` in the manifest: section 5.4 wants release-build timings, and a release build emits no
  trace sections without it. It declares no permission and has no user effect. The owner's to remove if unwanted; the timings
  then need another source.
- A publish or Drive row names its family after a colon, so **Publish through your exchange server** and **Announce via Google
  Drive** still find the rows wherever the privacy policy and the portal name them.
- The first frame of the screen is held until the first page is ready, for at most 400 ms (section 5.1).

**Facts the spec assumed and the tree does not hold:**

- S0040 is a report to the portfolio and holds no capture rig. The sources that exist are
  `tools/help/capture-help-screenshots.ps1` and the S0025 rig in `tools/audit/`; the procedures in
  `docs/design/UI_CONTRACT_CHECKS.md` use `adb` directly and need neither.
- The plan's "eight layouts" are `activity_exchange`, `activity_main`, `activity_qr_scanner`, `dialog_import_broadcast`,
  `item_exchange_broadcast`, `sheet_stream_filter`, `view_settings_about` and the landscape `activity_broadcast_control`. The
  other layouts of the audit's list keep icon-only or single commands; each is recorded in the profile, section 7.

## 4. Deadlines

The dates are those the owner approved on 2026-10-26 for S0036 and recorded in the registry's unified-UI migration entry; this ticket does not move them: settings surface with rail and About 2026-11-30, exchange and broadcast screens 2026-12-07, Wear decision and web pages 2026-12-14, captures, checks, timings and adoption rows 2026-12-21.

## 5. Acceptance

- [x] Every clause of the five contracts that binds a phone or Wear surface is mapped to a screen and a reproducible check; none rests on a component unit test alone. Held by `docs/design/UI_CONTRACT_CHECKS.md`.
- [ ] The settings surface, About set and group headers are reachable from the running app. Wired and read statically; MANUAL 1-5 of the audit prove it on a running build.
- [ ] Captures, geometry, contrast, accessibility and timing evidence are recorded with device/build/locale/scale; missing items are named as unverified. All of it is unverified and named so in the clause table; MANUAL 6-7 of the audit produce it.
- [x] The own registry rows advance only to what was measured; a plan is not conformance. Nothing was measured, so this ticket wrote no registry patch.
- [x] No release, tag, upload or deployment is implied.

## Last Audit

**Date:** 2026-11-08 17:50 | **Mode:** pre-handoff, static | **Outcome:** BlockNeedUserTest
**Counts:** PASS 13 | WARN 0 | FAIL 0 | MANUAL 7 | UNCHECKABLE 0 | EXEMPT 1 | RELEASE 3

Not built, not tested until the release: the headline is wired end to end and read statically, and nothing has run. Every
command below ran in this pass, each measured under 5 s; the scratch checks are direct calls, their numbers kept here.

1. PASS - the plan's static predicates P1.1-P7, 36 in all (grep and XML parse of the named files, including 510 layout nodes for
   commands at full width or by weight, and raw checkboxes), exit 0, 0.354 s; expected all hold | actual `plan-predicates: PASS`.
2. PASS - the last-page fix: `reopening` in `SettingsActivity.onCreate`; expected a later opening opens the page below 600 dp |
   actual `savedInstanceState == null && lastPage != null` joins the condition that opens a page. Behaviour is MANUAL 2.
3. PASS - every `binding.<name>` of the 23 Kotlin files using a view binding resolves to an id of its layouts, exit 0, 0.378 s;
   expected 0 unresolved | actual `scan-bindings: PASS files=23 names=247`.
4. PASS - every `@type/name` and `R.type.name` of `phone/src` resolves and every XML parses, exit 0, 0.565 s; expected PASS |
   actual `check-res: PASS (refs=755 xml=139)`.
5. PASS - `R.id` in the unit test sources, and custom `app:` attributes in the layouts; expected nothing unresolved | actual
   `refs=58 unresolved=0`; 21 attributes used, the 4 undeclared are Media3 `PlayerView`'s own.
6. PASS - the screen's consumed symbols read at their declarations this pass (rail, dropdown and toggle rows, panel manager,
   `HelpOpenManager`, `HelpKeys`, `UiLanguageCatalog`, `Distribution`, `collectOnLifecycle`, `exposeAsLink`); the About cast holds
   (`view_settings_about` has a `ScrollView` root); the rail does not call back on a programmatic select; a group body has no
   other visibility writer; no P0 or P1 left.
7. PASS - clause coverage: the numbered clauses of the binding sections of the five contracts, read from the catalog this pass,
   each have a row of `docs/design/UI_CONTRACT_CHECKS.md`; expected none unmapped | actual none, after the rows of section 3b.
8. PASS - contract versions in the catalog; expected `UI-SIZING` 1.1, `BUTTONS` 2.0, `BOOLEAN-OPTION` 2.0, `COLLAPSIBLE-GROUP` 1.1,
   `SETTINGS-NAVIGATION` 2.0 | actual the same. `tools/contracts/check-sync.ps1` exit 1, 0.397 s, 14 findings: the known red
   of S0051's audit, none about these rows; `PASS: 32 pointer versions match domain headers`.
9. PASS - `tools/help/generate-help-pages.ps1 -Check`, exit 0, 2.53 s; expected PASS | actual `generate-help-pages: PASS (checked=37
   pages=11 locales=en,ru,uk date=2026-10-29)`.
10. PASS - the document gates, each exit 0: `assert-doc-registry` 1.197 s `records=30 files=268`, `assert-doc-links` 0.877 s
    `documents=42 pages=3 links=174`, `assert-doc-style` 0.681 s, `assert-translation-freshness` 0.584 s `blocks=221`,
    `assert-termbase` 0.527 s.
11. PASS - `tools/site/build-site.ps1 -Check`, exit 0, 0.335 s; `tools/release/check-syntax.ps1`, exit 0, 0.234 s.
12. PASS - probe invariant: four `Timber.d("S0049: ..")` lines, each alone on its line, for this status; the gate reads them on
    this transition.
13. PASS - Acceptance 4 and 5: no registry patch in this ticket; nothing tagged, uploaded or deployed. The pending S0057 row of
    `COLLAPSIBLE-GROUP` still says no screen uses the component, which this ticket made untrue; it is corrected with the measured
    evidence (MANUAL 6), not before.

**EXEMPT**

1. `enableWearCompanion`, `dimClockOverlayEnabled` and `hideSystemUiInFullscreen` have no editor (owner, 2026-11-08). A named gap
   against `SETTINGS-NAVIGATION` 2.0 section 3.2, not conformance: the dim clock overlay cannot be switched on by a user today.

**RELEASE**

1. Nothing has been compiled, so every Kotlin and resource edit of this ticket first meets the compiler here:
   `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` (release builds, the Gradle unit suites, among them
   `SettingsDestinationsTest` and `SettingsAboutDestinationViewTest`).
2. Known red, this ticket's own: `tools/help/assert-help-map.ps1 -CommittedBaseline tools/help/help-map-baseline.txt` exit 1,
   0.757 s, `assert-help-map: FAIL (5 finding(s))`, `STALE-SHOT` for `broadcast`, `broadcast-settings`, `catalog`,
   `filter-and-sort` and `qr-scanner`, whose layouts this ticket changed; plan step P6 is not met until this clears. A picture
   needs a build on an emulator, so it stays red rather than being accepted in the baseline. The sweep's gate refuses the release
   until `pwsh -NoProfile -File tools/help/capture-help-screenshots.ps1 -Serial emulator-NNNN`, then
   `tools/help/generate-help-pages.ps1`, then the gate. The same capture writes the first pictures of the new `settings` page,
   which has none yet.
3. `pwsh -NoProfile -File tools/help/capture-help-screenshots.ps1 -F1Proof`: F1 and **?** on the settings pages reach the right
   help page.

### Manual / on-target

Probes: filter logcat for `S0049:`. Each line below passes when the behaviour holds and the named probe appears.

- [ ] 1. Catalog menu > **Settings** opens the screen (`settings page activated`). General: theme system / light / dark changes live
      (`theme row chosen`), language changes without a restart with endonyms listed (`language row chosen`), the broadcasting
      switch holds across reopening.
- [ ] 2. Below 600 dp: the very first opening shows the list; a page opens in the same window; Up returns to the list with the
      selection kept; a later opening lands on the page left last, at the place left. At 600 dp and more the rail stays beside
      the page. Rotation and a language change keep page and place.
- [ ] 3. Broadcast Control > **Broadcast settings** opens the Broadcast page, Back returns to Broadcast Control. With broadcasting
      off the rows are hidden and **Open General** leads to the switch; a typed port is the one the next broadcast uses.
- [ ] 4. About: every link opens its page, the privacy link in the app language (this also closes the MANUAL of S0033), the
      diagnostic report reaches the share sheet, and opening About makes no network request.
- [ ] 5. Exchange server: the three groups fold and unfold by the whole header (`exchange groups registered`), their state survives
      a process restart, and a delivered connection string expands the account group with the form filled.
- [ ] 6. Every procedure of `docs/design/UI_CONTRACT_CHECKS.md` on a release build of `store` on a named device (`CAP`, `GEO`, `CON`,
      `A11Y`, `NEG`, `TIME`, `GROW`), results recorded in that file with device, build, locale, scale and date: warm p95 below 150 ms
      and first visit below 500 ms with raw samples and maximum. Then the own registry rows advance to what was measured,
      through a registry patch for the canon session.
- [ ] 7. The rows marked Wear in the same file, on a round Wear device.
