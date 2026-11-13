# S0064 - No way to remove or re-fetch the downloaded channel pictures

**Ticket:** S0064
**Status:** BlockNeedUserTest
**Status note:** Development complete on phone and watch (P1-P7), no compile command declared, not built, not tested until the release: needs the device walks of remove and re-fetch on the phone and on the watch (Manual lines of Last Audit) and the release sweep (RELEASE lines); 7 S0064 probe lines stay in code until the ticket leaves this status.
**Complexity:** medium - re-read at the plan pass 2026-11-10 and again at the P7 pass 2026-11-13: the phone side is as assessed at the spec pass 2026-11-08 (a ported uninstall path, two settings rows, a re-read of the main list on return, all in known code). The watch side was larger than assessed - the watch app has no settings page, so the control needed a new destination, route, help entry and strings together with the persisted opt-out that has no source twin - and it came out as planned once question 3 was answered (section 4): one route, one view model, one screen, a per-set opt-out store and three delivery calls, all in known code.
**Date:** 2026-11-08
**Verification level:** gate - JVM tests for the uninstall path, the row states and the watch's remove and download (run at the release), plus a device walk of remove and re-fetch on the phone and on the watch.
**Owner:** the StreamsPlayer Android session of this repository.

## Captured request and migration scope

Parked from S0050 (unattended pass, 2026-11-08). Symptom: the channel pictures of S0050 take about 25 MB on the phone and about 25 MB on the watch, and neither device has a way to remove them short of clearing the app's storage. The phone also has no place to fetch them later: after "Later" the offer returns only with the next catalog update.

Evidence: FastMediaSorter carries this as its Extensions Manager (`ui/delivery/ExtensionsManagerFragment.kt`, `ExtensionsManagerViewModel.kt`, and the size-label and uninstall parts of `DeliverableInventoryImpl`). S0050 ported the delivery client and cut those parts on purpose (`PLAN/S0050_stream-bank-artwork-delivery.md` section 5.3, item 6), because the ticket orders delivery and render, not a management screen.

Needed: decide the placement (a row on a Settings page is the obvious one), then port the screen's parts under the port ledger rule, with the help row and strings in three locales. Whether the watch gets the same control is a question for the owner.

## 1. Problem

The two channel-picture sets (previews and logos, published sizes 14,605,058 and 10,005,648 bytes) are installed on demand and then stay: a user who accepted the offer to see pictures cannot take the space back, and a user who answered "Later" cannot ask again until a catalog update happens to repeat the offer. Both devices behave this way. The only reclaim is clearing the whole app's storage, which also removes everything the user set up.

## 2. Goals, non-goals and constraints

Goals:

- **G1 phone control.** One place that lists the two sets, each with its state (not installed, downloading with a percent, installed, update ready, failed), the published size, and the actions that fit the state: download or update, and remove.
- **G2 remove.** Removing a set deletes its payload and its installed marker, asks first (the action is destructive and the confirmation cannot be skipped), and the main channel list stops drawing those pictures the next time the user is on it, without a restart.
- **G3 re-fetch.** Downloading from the control starts the same background job and progress notification as accepting the offer; a set removed here can be fetched again here.
- **G4 surfaces.** Strings in en, ru and uk, the help page and its `help-map.json` row, written in the same edit as the control.
- **G5 tests.** The uninstall path and the status transitions are covered by JVM tests, run at the release.

Non-goals: any change to how delivery downloads, verifies or promotes a payload; the cadence of the offer after a catalog update (S0050); the producer side of the artwork; the Windows product; the grid live-thumbnails document; any release.

Constraints that bind the work:

- `CLAUDE.md` section 2 (full port, never a rewrite): the ported parts are copied from the FastMediaSorter source and cleaned; every ported or newly written file has its port-ledger row; every deliberate delta from the source is written in this ticket next to its reason.
- `STREAM-BANK` rule 12 (an absent or unreadable manifest means "nothing new"): removing a set must not make the offer or the update state read differently when the manifest does not answer. Removal clears the stamp together with the payload, so a stamp never outlives its payload (the S1200 hazard).
- `SETTINGS-NAVIGATION` 2.0: section 3.2 (one setting, one editor), 4.3 (selecting a page starts no operation - the control acts only on an explicit tap), 5.2 (no file or network work on the UI thread; the size is data that arrives later and must not rebuild the page).
- `BUTTONS` 2.0 and `UI-SIZING` 1.1 (the 48 dp touch floor) for the row actions; `CONTEXT-HELP` for the help row.

## 3. Research findings (all closed)

### 3.1 What is in the tree

- Phone, domain: `DeliverableInventory` exposes `getExtensions()` and `download(item)` only. `ExtensionStatus` already has `Installed`, `NotInstalled`, `UpdateAvailable`, `Downloading(percent)` and `Failed`, and `getExtensions()` returns exactly the two artwork rows, each with a `statusFlow` that merges the persisted state with a live download. The row state the control needs already exists.
- Phone, data: `DeliverableCapabilityRepository` has no `uninstall`; `InstalledSetMarkerStore` has the payload directory (`filesDir/delivery/<SET>/`), the installed flag and the stamp, but no delete and no stamp clear. `DeliverableDownloadRunner` has `enqueue` and `progressOf`, and no cancel (the source runner has none either).
- The published size per set is `ArtworkManifestSource.sizeOf(set)`, already used by the offer dialog (`StreamAtlasPromptManager`). The size-label rows of the inventory were cut by S0050 delta 6.
- Who draws the pictures: `MainActivity` owns the two slicers and the coordinate maps as private state and reloads them through `reloadPreviewTiles()` and `reloadLogoTiles()`, which today run only from the offer's `onAtlasInstalled` callback. A control placed in `SettingsActivity` cannot reach them, so the main list needs its own trigger to re-read on return (its `onStart` is the seam); `StreamTilePackReader.invalidate()` already closes the open pack and drops the cache, and unlinking a file that is still open is harmless on Android.
- Watch: `WearArtworkDelivery.refresh()` fetches every set whose manifest stamp differs from the installed one, and an absent set always differs. The triggers are the watch's catalog import (first load of an empty list, pull to refresh). The watch has no offer and no screen that names these sets.

### 3.2 What the source has

- `ExtensionsManagerViewModel`: `download`, `uninstall`, `installAll`, `uninstallAll`; a retry replaces the previous collector.
- `DeliverableInventoryImpl.uninstall(item)`: repository uninstall, then the row's status flow is set to `NotInstalled`.
- `DeliverableCapabilityRepositoryImpl.uninstall(set)`: `deletePayload`, flag false, `clearStamp`. `InstalledSetMarkerStore` has `deletePayload` and `clearStamp`.
- The screen: a fragment overlay with a grouped list, status chip, size line, progress, and per-row Download and Delete buttons behind a destructive-styled confirmation dialog; strings `ext_*` in `strings.xml`.
- Source defect found while reading, for the plan and for the FastMediaSorter session: `uninstall` runs `deleteRecursively()` inside a coroutine started on the view model scope (main dispatcher), which is disk work on the UI thread. The port moves it to the IO dispatcher.

### 3.3 Contract boundary

None crossed. No format, wire shape, algorithm constant or behaviour another product reads changes: the control only removes and re-requests local copies through the delivery that S0050 built. The contracts above bind the screen, not a new boundary; no catalog amendment is needed.

### 3.4 Resolved without asking

- **Confirmation on remove.** A destructive action always confirms, and the dialog names the set and its size (canon invariant 9 and TESTING_AND_QA section 6; the source behaves the same).
- **Does the offer return after a remove?** Yes. The source's uninstall leaves the offer logic untouched, and the standing law of `CLAUDE.md` section 2 forbids a quiet divergence; a user who wants no offer answers "Later".
- **Does a phone remove reach the watch?** No. S0050 decision D1 (2026-11-08) made watch delivery independent of the phone, with nothing crossing the Data Layer; the same holds for removal.
- **Size shown.** The published size from the manifest, as in the offer and as the source shows an estimated size; no on-disk measurement, which would be disk work for a number the user cannot act on better.
- **Bulk buttons.** The source has Install all and Delete all for ten rows; here two rows with one tap each make them clutter, so they are cut (a deliberate delta, recorded here).
- **Remove while a job for the set runs.** The affordance is hidden while the row reads `Downloading`; for a job that outlives its screen, the plan adds `cancel(set)` to the runner (cancel the unique work by name) and removal cancels before it deletes. New code with no source twin, written under this ticket.

## 4. Open questions (owner)

### Quiz decisions (2026-11-10)
- Placement of the phone control -> Option A (Channel pictures group at the end of General; rail and destination inventory stay unchanged, no new glyph needed).
- Does the watch get a control, and what does Remove mean there -> Option A (Yes: Channel pictures row on the watch settings page with Remove/Download and a persisted do-not-fetch flag honoured by refresh).

### Quiz decisions (2026-11-13)
- Where the watch control lives (question 3) -> Option A: a "Channel pictures" row on the watch start screen, after Assign tile and before Help, opening one small screen with the two sets, each with its state, published size and Remove or Download (the recommendation, chosen by the owner's click; one new route, one `inherit` help-map row under the watch page, one watch help source entry, the start screen grows from five rows to six).
- Checked before asking, not a decision: the watch navigation graph in `wear/.../MainActivity.kt` holds HOME, STREAMS, AUDIO_PLAYER, VIDEO_PLAYER, BROADCAST, BROADCAST_QR and `tile_picker`; `WearRoutes.SETTINGS` is declared and has no destination, so the premise of question 3 holds.

1. **Placement of the phone control.** `Status: Resolved (2026-11-10, quiz - Option A).` Where does the control live in the settings screen (`SETTINGS-NAVIGATION` 2.0; today General holds theme, language and the broadcasting switch, Broadcast holds the broadcast rows, About is last; the owner settled that inventory in S0049 on 2026-11-08)?
   - **A. Recommended - a "Channel pictures" group at the end of General**, two rows (previews, logos). The rail and the destination inventory stay as they are, no new glyph is needed, and the Settings Screen Record changes by one paragraph; the cost is that General grows past theme and language.
   - **B. A new fourth destination "Channel pictures"** between Broadcast and About, the closest copy of the source's own screen. It needs a recognizable ICON-SET glyph (a missing meaning goes through the ICON-SET proposal procedure, which is the canon session's), a rail name that stays whole in every locale, and updates to the destination inventory and the UI checks.
   - **C. One row on General that opens a dialog** listing the two sets. Smallest page change; the state of the sets is hidden behind a tap and the dialog is a second place to keep consistent with the page.
2. **Does the watch get a control, and what does Remove mean there?** `Status: Resolved (2026-11-10, quiz - Option A).` The watch re-fetches any absent set on its next pull to refresh (section 3.1), so a bare Remove would be undone by the user's own next refresh.
   - **A. Recommended - yes: a "Channel pictures" row on the watch's settings page with Remove and Download, plus a persisted "do not fetch" flag** that Remove sets and Download clears, honoured by the refresh. New watch behaviour with no source twin, three-locale strings, a watch help row.
   - **B. Phone only in this ticket**; the watch control becomes its own ticket. The watch's ~25 MB stays removable only by clearing the watch app's storage until that ticket is built.
   - **C. No watch control at all**: the watch keeps fetching and holding the pictures as part of its catalog, and the help text says so.
3. **Where does the watch control live?** `Status: Resolved (2026-11-13, quiz - Option A).` Question 2 was answered as "a row on the watch's settings page", and that page does not exist: the watch app's navigation graph holds the start screen (Channels, Broadcast, Assign tile, Help rows), the channel list, the two players, the broadcast screens and the tile picker; `WearRoutes.SETTINGS` is a declared constant that no destination answers and nothing navigates to. The watch's behavior is settled by question 2 (Remove, Download, a persisted do-not-fetch flag the refresh honours); only the place is open.
   - **A. Recommended - a "Channel pictures" row on the watch start screen** (after Assign tile, before Help) that opens one small screen with the two sets, each with its state, its published size and Remove or Download. One new route, one new `inherit` row in the help map under the watch page, one entry in the watch help source. The start screen grows from five rows to six, and the round face keeps its existing list shape.
   - **B. A "Channel pictures" entry inside the channel list screen**, shown as the last row of its list. No change to the start screen, but the control is reachable only while browsing channels and sits inside a 1,247-line screen whose list is the channels themselves, so a row there is a different kind of row from its neighbours.
   - **C. Phone only in this ticket**; the watch control becomes its own ticket. Question 2 chose against this when the watch control looked like one more row; with a new destination in the price, the owner may want to reconsider.

## 5. Scope map for the plan pass (not a plan)

Independent of both questions: the phone uninstall path (`InstalledSetMarkerStore.deletePayload` and `clearStamp`, `DeliverableCapabilityRepository.uninstall`, `DeliverableInventory.uninstall`, runner `cancel`), the main list's re-read on return, its JVM tests, the port-ledger rows. Depends on question 1: the layout, the row view model, strings and the help row. Depends on question 2: everything on the wear module.

### Owner inputs (Approval gate)

- **Related tickets:** S0050 (the delivery this controls; its section 5.3 item 6 parked this ticket), S0049 (the settings screen and its destination inventory), S0035 (in-app help and the help map), S0036 (the unified-UI contracts the rows bind to).

## 6. Plan (written 2026-11-10, unattended queue pass)

Rung: complex - new files, a Hilt view model, a new compound view, three-locale surfaces, a second module. Between-release law (`CLAUDE.md` section 5a): no Gradle run; each phase ends in static checks, and the build and test rungs are RELEASE items.

### 6.1 Contract boundary

Section 3.3 stands: the control removes and re-requests local copies through the delivery S0050 built, and writes nothing another product reads. No catalog amendment precedes the code.

### 6.2 Coverage inventory

| Line | Phase | Note |
| --- | --- | --- |
| G1 phone control: state, size, fitting actions | P3 | `ChannelPictureRowState` maps `ExtensionStatus` to the row; size arrives later |
| G2 remove: confirm, delete payload and marker, list stops drawing | P1, P2, P3 | uninstall path, re-read on return, confirmation dialog naming the set and size |
| G3 re-fetch: same job and notification as the offer | P3 | `ChannelPicturesViewModel.download` drives `inventory.download`, the path the offer uses |
| G4 surfaces: strings en/ru/uk, help row | P3, P6 | help source `settings` gains the rows; no new surface, so no new map row |
| G5 tests | P4 | JVM; run at the release |
| Constraint: full port, ledger rows | P1, P5 | `ExtensionsManagerViewModel` is ported to `ChannelPicturesViewModel`; the ported files already edited keep their rows |
| Constraint: rule 12, stamp dies with payload | P1 | `uninstall` clears flag and stamp after the delete; a test names the order |
| Constraint: SETTINGS-NAVIGATION 4.3, 5.2 | P3 | no operation on page selection; no file or network work on the UI thread; size and state fill in after the page is shown |
| Constraint: BUTTONS, UI-SIZING | P3 | the shared button styles, 48 dp minimum |
| Decision 1 (placement A) | P3 | heading and two rows at the end of the General page; rail and destination inventory unchanged |
| Decision 2 (watch control, persisted flag) | P7 | the opt-out store, `remove`, `download`, the refresh honouring the record |
| Question 3 (where the watch control lives) | P7 | resolved 2026-11-13 (Option A): a row on the watch start screen, one route, one screen |

### 6.3 Deliberate deltas from the source, each with its reason

1. **No fragment, no grouped list, no bulk buttons.** The control is two rows of a settings page (decision 1); Install all and Delete all serve ten rows (section 3.4).
2. **`uninstall` moves its disk work to the IO dispatcher.** The source runs `deleteRecursively()` on the view model scope's main dispatcher (section 3.2). A finding for the FastMediaSorter session, carried in section 8.
3. **`cancel(set)` on the runner.** The source runner has none; removal while a job that outlived its screen runs must stop that job, or it promotes its payload over the empty slot (section 3.4).
4. **Size is the published size, read asynchronously.** No on-disk measurement (section 3.4).
5. **The main list re-reads on return in both directions.** The source's `onStart` seam reloads only a payload that appeared; here the same mismatch test also catches one that disappeared.
6. **A plain heading, not a collapsible group.** Decision 1 asks for a group at the end of General; the two rows are always shown and the page keeps its scroll anchors, so a heading carries the group without a disclosure state to remember (`COLLAPSIBLE-GROUP` 2.2 asks for a wrapper only where the page holds a single topic, and it does not here). Recorded as the structural choice.
7. **Remove stays on a failed row.** The status of a failed update and of a failed first download is the same `Failed`, and the failed update leaves the old copy on disk; hiding Remove there would strand the space for as long as the process lives. Removing from a row with nothing on disk changes nothing but the status.
8. **The three removal steps are not cancellable.** The uninstall runs inside the screen's view model scope, and a Remove tap followed by leaving Settings cancels that scope; a half-done uninstall would leave a stamp without its payload, the S1200 hazard rule 12 names. New behavior with no source twin.
9. **Known edge, not fixed.** Removing a set while a job for it that outlived its screen runs cancels the job; the offer dialog's collector, if still alive in the catalog screen, then maps the cancelled work to `Failed` and the row reads Failed until the next Download tap or Remove. The delivery is not changed here (non-goal).
10. **The watch control has no source twin at all (P7).** FastMediaSorter has no watch artwork delivery and no watch inventory, so nothing is ported and no ledger row is written for P7; `WearArtworkDelivery` (ledger row for `RealDeliverableSetDownloader`) gains `remove` and `download` and a refresh that skips removed sets, and `WearArtworkStore` gains `delete`. Each is a deliberate delta recorded here.
11. **A per-set record of removals, not one flag.** `WearArtworkOptOutStore` keeps the names of the removed sets in one `wear_settings` key (`ARTWORK_NOT_FETCHED`). Decision 2 words it as "a persisted do-not-fetch flag"; see the assumption in section 6.5.
12. **Remove records first and deletes second; the promotion re-reads the record under a lock.** A process that dies between the two leaves a copy the next Remove deletes, where the opposite order leaves a deleted copy that the next pull to refresh quietly brings back. A download in flight checks the record before its second file and again, under the same lock a removal deletes under, just before it promotes - so a removal made while a set downloads leaves nothing installed. `remove` is not cancellable (as delta 8 on the phone).
13. **The watch row has no "newer set ready" state and offers Remove only for a set it holds.** The watch's refresh replaces a set whose stamp changed by itself, so a set is held or not. Its delivery promotes all-or-nothing and keeps what it held, so a failed download leaves no copy to strand, unlike the phone's failed row (delta 7).
14. **The watch list needs no re-read hook.** `WearChannelArtwork` is created per `StreamsViewModel` and released with it, so the channel list opened after a Remove builds fresh readers over what is on disk; the phone's `onStart` seam (delta 5) has no watch twin because it has nothing to fix.

### Assumed (unattended, 2026-11-13)

Each was taken without asking because it is reversible inside this ticket and has a researched recommendation. The owner may overrule any of them.

- **A1 - one record per set, not one flag for both.** Taken: Remove on one row stops the refresh from fetching that set only. Weighed against: one global "do not fetch" flag, the shape decision 2 names. Reason: a watch that removed only the previews would otherwise stop receiving the logos, which Remove on one row does not say. Owner may overrule.
- **A2 - the watch's Download runs in the screen's scope.** Taken: leaving the screen stops it, the set stays un-removed so the next pull to refresh fetches it, and the row shows "Downloading.." without a percent. Weighed against: an application-scoped job with a progress notification like the phone's. Reason: S0050 gave the watch a plain suspend call tied to a screen, with no job runner or notification; adding both is a change to the delivery, which is a non-goal here. Owner may overrule.
- **A3 - the published size is read from the manifest when the screen comes to the front.** Taken: one GET with the client's 12 s deadline, off the main thread, a size line that appears when the manifest answers and no line when it does not. Weighed against: no size on the watch. Reason: G1 asks for the published size, and the data arriving later rebuilds nothing. Owner may overrule.

### 6.4 Phases (produces -> consumes order holds; every step ends in a static check)

- **P1 Phone uninstall path.** `InstalledSetMarkerStore.deletePayload` and `clearStamp`; `DeliverableCapabilityRepository.uninstall` and its implementation; `DeliverableDownloadRunner.cancel` and its implementation; `DeliverableInventory.uninstall` and its implementation. Check: each new member greps on a declaration line, interface and implementation agree, and no other class implements these three interfaces.
- **P2 Main list re-read.** `artworkIndexIsOutOfStep` in `ui/streams`; `MainActivity.onStart` uses it for both sets. Check: the function greps on a declaration line and both call sites use it.
- **P3 Settings control.** `ChannelPictureRowState`, `ChannelPicturesViewModel`, `SettingsArtworkRow` with its layout, the heading and two rows in `page_settings_general.xml`, the binding in `SettingsActivity.buildGeneralPage`, strings in en, ru and uk. Check: every `R.id` and `R.string` the code names is declared; each new string key is in all three locale files.
- **P4 Tests.** Row-state mapping, the reload rule, the repository's uninstall order, the inventory's uninstall (cancel, then delete, then status), the marker store's delete and stamp clear. Check: every symbol a test names greps on a declaration line.
- **P5 Port ledger.** `record-port.ps1` for `ChannelPicturesViewModel`. Check: exit 0 and the row present.
- **P6 Surfaces.** Help source `settings` in en, ru and uk, regenerated pages, README, `CHANGELOG.md`. Check: `generate-help-pages.ps1`, `assert-help-map.ps1` and the document gates, the tense grep.
- **P7 Watch.** `WearArtworkOptOutStore` and its `@Provides`, `WearArtworkStore.delete`, `WearArtworkDelivery.remove` and `download` and the refresh honouring the record, `WearChannelPictureRowState`, `WearChannelPicturesViewModel`, `WearChannelPicturesScreen`, `WearRoutes.CHANNEL_PICTURES` and its `composable` and start-screen row in the wear `MainActivity`, strings in en, ru and uk (`strings_artwork.xml`), the `pictures` entry of the `watch` help source and the `wear:WearRoutes.CHANNEL_PICTURES` row of the help map, tests. Check: every `R.string` the code names is declared once in each locale file; each new member greps on a declaration line; the route greps in `WearRoutes` and in one `composable(`; the help gates pass.

Pre-implementation blockers: none. Question 3 was answered 2026-11-13 (Option A) and P7 was built in the pass of that date.

## 7. Evidence (2026-11-10 and 2026-11-13, unattended passes; no Gradle run, by `CLAUDE.md` section 5a)

Not built, not tested until the release: no Kotlin in this ticket has been compiled or run, and none of the 61 new tests (19 on the phone, 42 on the watch) has run. `.sza-profile.json` declares no `commands.compileCheck`: no compile command.

**Phase results.** P1 to P7 are in the tree.

- **P1** `InstalledSetMarkerStore.clearStamp` and `deletePayload`; `DeliverableCapabilityRepository.uninstall` (the three steps in one non-cancellable block, the delete on the IO dispatcher); `DeliverableDownloadRunner.cancel`; `DeliverableInventory.uninstall` (cancel, then delete, then the row reads not installed). Each interface has exactly one implementation in the tree, so no implementer was left behind.
- **P2** `artworkIndexIsOutOfStep` and `MainActivity.onStart`: the list re-reads a set whenever its index and the disk disagree, in either direction.
- **P3** `ChannelPictureRowState`, `ChannelPicturesViewModel`, `SettingsArtworkRow` and its layout, the heading and two rows at the end of `page_settings_general.xml`, the binding and the confirmation dialog in `SettingsActivity`. The page is drawn whole with the rows in a checking state that offers no button; the status and the published size fill in when they arrive; selecting the page starts nothing.
- **P4** 19 JVM tests in four classes: `ChannelPictureRowStateTest` (9), `ArtworkReloadRuleTest` (3), `DeliverableUninstallTest` (4), `InstalledSetMarkerStoreUninstallTest` (3, real files and a real DataStore).
- **P5** One ledger row: `ExtensionsManagerViewModel` to `ChannelPicturesViewModel`, source SHA-256 `2118ab0e3fe6abb18195010ccca97bb1d80d75c0dca875835b553d051e3f7fd7`. The ported files edited here already have their rows; the delivery edits are deliberate deltas recorded in section 6.3.
- **P6** Strings, help source and pages, README and `CHANGELOG.md`, section 9.
- **P7** (2026-11-13) `WearArtworkOptOutStore` (the removed sets, by name, in one `wear_settings` key) wired by `WearArtworkModule`; `WearArtworkStore.delete`; `WearArtworkDelivery.remove` (record first, delete second, non-cancellable), `download` (clears the record, fetches now) and a refresh that leaves removed sets out and does not ask the mirror when every set is removed, with the record re-read before the second file and again under the promotion lock; `WearChannelPictureRowState` and `WearChannelPicturesState`; `WearChannelPicturesViewModel`; `WearChannelPicturesScreen`; `WearRoutes.CHANNEL_PICTURES`, its `composable` and the start-screen row after Choose tile channel and before Help; 16 strings in en, ru and uk (`strings_artwork.xml`); the `pictures` entry and the renumbered steps of the `watch` help source, and the `wear:WearRoutes.CHANNEL_PICTURES` help-map row (an `inherit` under the watch page, anchor `pictures`); README and `CHANGELOG.md`.
- **P7 tests** 42 JVM tests, none run: `WearChannelPictureRowStateTest` (13), `WearArtworkOptOutStoreTest` (6, an in-memory `DataStore`), `WearArtworkDeliveryTest` (12 new beside its 14 earlier ones, which only gained the new constructor argument; MockWebServer and Robolectric, including a removal that arrives while the sidecar is still downloading), `WearChannelPicturesViewModelTest` (11, mockk doubles).
- **Probes** (status-bound, for `BlockNeedUserTest`): seven `Timber.d("S0064: ..")` lines, each alone on its line - phone: download tapped, remove confirmed (`ChannelPicturesViewModel`), the catalog's two re-reads on return (`MainActivity.onStart`); watch: download tapped, remove confirmed, and the refresh leaving removed sets out (`WearArtworkDelivery`). They go when the ticket leaves that status.

**Watch behavior, as built.** The start screen has a **Channel pictures** row after Choose tile channel and before Help. It opens one screen with a title and two blocks, **Channel previews** and **Station logos**, each with its status (not downloaded, downloading, downloaded, download failed), the published size once the manifest answers, and the button that fits: **Download** or **Try again** for a set the watch does not hold, **Remove** for one it holds. Opening the screen reads the disk and the manifest and starts nothing. **Remove** asks first, says the watch will not fetch the set again until it is downloaded here, and names what it frees; confirming records the removal, then deletes the payload and its stamp. The channel list's pull to refresh skips a removed set, and when both are removed it does not ask the mirror. **Download** clears the removal and fetches the set now; a failed download reads **Try again**. A phone Remove and a watch Remove do not reach each other (section 3.4).

**Phone behavior, as built.** Settings > General ends with a **Channel pictures** heading and two rows. Each shows its title, its state (not downloaded, downloading with a percent bar, downloaded, a newer set ready, failed), the published size, and the buttons that fit: Download or Update or Try again, and Remove. Download is the offer's own background job and progress notification. Remove asks first, names the set and what it frees, cancels a running job, deletes the payload, then clears the installed flag and the stamp; the catalog screen re-reads on its next start and stops drawing those pictures.

## 8. Carried outside this repository (not applied from here)

**To the FastMediaSorter session - one finding in its source, read while porting (section 3.2):** `app_v2/.../data/delivery/DeliverableCapabilityRepositoryImpl.kt:47` (`uninstall`) calls `markerStore.deletePayload`, which runs `deleteRecursively()` inside a coroutine started on the view model scope (`ExtensionsManagerViewModel.uninstall`, main dispatcher): disk work on the UI thread. The port here moves the delete to the IO dispatcher and keeps the three steps non-cancellable.

## 9. Surfaces (through `feature-to-site`; the manifest is `docs/DOCS_SURFACES.md`)

| Surface | Source or render target | Locales | Changed / N/A + reason |
| --- | --- | --- | --- |
| Phone app strings | source | en, ru, uk | changed: `strings_artwork.xml`, 16 keys in each |
| Wear OS app strings | source | en, ru, uk | changed: `strings_artwork.xml` (new file), 16 keys in each |
| Watch face strings | source | en, ru, uk | N/A: the watch face shows no channel |
| Help source `settings` | source | en, ru, uk | changed: two elements and a group in each locale; the translations were written with the English and recorded with `-Record` |
| Help pages | render, `generate-help-pages.ps1` | en, ru, uk | regenerated, PASS |
| Help source `watch` | source | en, ru, uk | changed: the `pictures` element, a step for it with Help renumbered (8), and a clause in `channels`; the translations were written with the English and recorded with `-Record -Doc help/watch` |
| Help screenshot of settings | render, needs a device | en, ru, uk | N/A: no picture set exists for `settings`, so nothing is stale |
| Help map | source | neutral | changed: one `inherit` row for the new watch route (anchor `pictures`); the phone rows sit on the mapped `SettingsActivity` page and need none |
| README | source | en | changed: the channel pictures sentence names the watch screen |
| Ledger | `CHANGELOG.md` | en | changed: the phone entry stands; one new entry under Added for the watch |
| Privacy policy, Play Data safety | source | en, ru, uk | N/A: removing a local copy and re-requesting the published files changes nothing the app stores or sends |
| Permissions table | render | en, ru, uk | N/A: no permission added |
| Site landing, help on the site | render | en, ru, uk | N/A: not in the tree yet (`site/` holds the privacy pages only) |
| Play listing, portal sections, hub | source, other repositories | - | N/A: what the product is has not changed |

## Last Audit

**Date:** 2026-11-13 | **Mode:** static | **Outcome:** BlockNeedUserTest
**Counts:** PASS 13 | WARN 0 | FAIL 0 | MANUAL 4 | UNCHECKABLE 0 | EXEMPT 3 | RELEASE 4

Not built, not tested until the release: no Kotlin in this ticket has been compiled or run, and a clean read of the code proves nothing about the compile. `.sza-profile.json` declares no `commands.compileCheck`: no compile command, none run. Development is complete on the phone and the watch; what remains is the device walk and the release rungs. Same shape as S0049.

### PASS (each command run in this pass unless it names the earlier one; expected | actual)

1. New members on a declaration line: grep `fun (remove|download|delete|optedOut|isOptedOut|setOptedOut|reload)`, `const val CHANNEL_PICTURES`, `ARTWORK_NOT_FETCHED =`, `WearChannelPicturesScreen(` in `wear/src/main` | one declaration each (the delivery's private `download(url, dest)` is a different overload); `WearArtworkDelivery(` is constructed in exactly one place besides Hilt, `WearArtworkDeliveryTest`, which gained the `optOut` argument.
2. String parity: the 16 `wear_pictures_*` and `sp_channel_pictures` keys the code names | each exactly once in `values`, `values-ru` and `values-uk` (16 declared in each file).
3. `generate-help-pages.ps1` | `PASS (files=37 written=6 pages=11 locales=en,ru,uk date=2026-11-13)`, exit 0, 2.4 s; `-Check` exit 0.
4. `assert-translation-freshness.ps1` | exit 1 with 6 findings (`channels` and `steps` STALE, `pictures` NEW-BLOCK, in ru and uk) before recording; `-Record -Doc help/watch` `PASS (recorded=22 blocks=225)`; the plain run `PASS (blocks=225 locales=ru,uk)`.
5. `assert-doc-registry.ps1`, `assert-doc-links.ps1`, `assert-doc-style.ps1`, `assert-termbase.ps1`, run again after the README and `CHANGELOG.md` edits | each exit 0: records=33 files=280; documents=47 pages=3 links=195; documents=47 sources=13; terms=12 forbidden=8 blocks=48.
6. `assert-help-map.ps1 -CommittedBaseline tools/help/help-map-baseline.txt` and `list-help-surfaces.ps1 -Module wear` | exit 1 with the same 5 `STALE-SHOT` (broadcast, broadcast-settings, catalog, filter-and-sort, qr-scanner) as before this pass, none for `watch` and none for the new route; the enumerator lists `wear:WearRoutes.CHANNEL_PICTURES` and the map has its row.
7. Route wiring: `CHANNEL_PICTURES` in `WearRoutes`, `composable(WearRoutes.CHANNEL_PICTURES)` and the `sp_channel_pictures` row in the wear `MainActivity` | 1, 1 and 1.
8. Tense and promise words over the new feature lines (wear strings in three locales, the `pictures` lines of `watch.json`, the `CHANGELOG.md` entry, the README sentence) | 0 hits.
9. Typographic long dashes in the 12 new or touched wear Kotlin files | 0 hits.
10. Probe invariant: seven `Timber.d("S0064: ..")` lines, each alone on its line (phone: `ChannelPicturesViewModel` 2, `MainActivity.onStart` 2; watch: `WearArtworkDelivery` 3) | present, and the ticket is entering the needs-manual-test status; they go when it leaves it.
11. Rule 12 and the S1200 hazard, by reading: `WearArtworkStore.delete` removes the payload directory that holds the stamp, `installedStamp` answers null without a pack, `remove` records first and deletes second, and the promotion re-reads the record under the lock `remove` deletes under.
12. DI, by reading: `WearArtworkOptOutStore` is provided by `WearArtworkModule` from `WearSettingsDataStore.store`; `WearArtworkDelivery` is the only class that takes it; the new `@HiltViewModel` takes `WearArtworkDelivery`, `WearArtworkStore` and `ArtworkManifestClient`, each a `@Singleton` with an `@Inject` constructor.
13. Symbols of every new test greps on a declaration line (`WEAR_PICTURE_SETS`, `publishedMegabytes`, `WearChannelPicturesState`, `WearChannelPictureRowState.Checking` and `of`, the view model's constructor order, `WearArtworkOptOutStore`'s three members, `delivery.remove` and `download`) | all declared, the view model constructor is `(delivery, store, manifest)` as the test passes it.

### WARN

None. The earlier WARN (the watch control, phase P7) is closed: question 3 was answered 2026-11-13 and the control is built.

### EXEMPT

1. Grid live thumbnails, the producer side of the artwork, the cadence of the offer: out of scope, section 2.
2. Bulk Install all and Delete all of the source: cut on purpose, section 3.4 and delta 1.
3. Help screenshots of the settings page and of the watch page: no picture set exists for either, so nothing is stale.

### RELEASE (not run between releases; the commands the release runs)

1. Compile, DI graph (`ChannelPicturesViewModel` and `WearChannelPicturesViewModel` are new `@HiltViewModel`s, `WearArtworkModule` is a new module), resources and the 61 new tests: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1`. If the DataStore cases of `InstalledSetMarkerStoreUninstallTest` hang, look first at its scope (it builds a real DataStore on an IO scope it cancels in `tearDown`); if `a removal made while the set downloads leaves nothing installed` in `WearArtworkDeliveryTest` hangs, look at the three requests it waits for (manifest, pack, sidecar) and at the 2 s sidecar delay.
2. `pwsh -NoProfile -File tools/port-ledger/check-drift.ps1` at the start of the release: the `ChannelPicturesViewModel` row must not report drift; P7 added no ledger row, because nothing in it is ported.
3. `pwsh -NoProfile -File tools/help/assert-help-map.ps1`: exit 2 until its baseline is in HEAD, whichever ticket runs it.
4. `pwsh -NoProfile -File tools/docs/assert-docs.ps1` (the aggregator; the gates above were run one by one).

### Action items

1. Owner: the device walks under Manual / on-target, phone and watch. Then a re-audit turns the ticket Verified and removes the seven probe lines.
2. RELEASE items above, in that order.

### Manual / on-target

- [ ] Phone, a device with both sets installed: Settings > General > Channel pictures shows both rows as Downloaded with their sizes; **Remove** on one asks, names the set and the megabytes, and **Cancel** changes nothing; confirming removes it, the row reads Not downloaded with **Download**, and going back to the catalog shows the list and the grid without those pictures, with no restart (probe `S0064: catalog re-reads`); **Download** shows the progress notification and the bar, and the pictures return in the list and the grid. Not covered by anything run so far.
- [ ] Phone, a newer set published: the row reads **A newer set is ready** with **Update** and **Remove**; Update replaces the copy. Remove during a running download, and leaving Settings right after confirming Remove, leave the row consistent. Not covered by anything run so far.
- [ ] Watch, both sets installed: the start screen shows **Channel pictures** between **Choose tile channel** and **Help**; its screen lists both sets as Downloaded with their sizes once the manifest answers; **Remove** asks, says the watch will not fetch the set again, and **Cancel** changes nothing; confirming leaves the row Not downloaded with **Download**; the channel list shows no pictures from that set, and pull to refresh does not bring it back (probe `S0064: watch refresh leaves out the removed sets`). Not covered by anything run so far.
- [ ] Watch: **Download** shows Downloading.. and then Downloaded, and the list shows the pictures again; with the connection off it reads **Try again**; leaving the screen right after confirming Remove still removes the set, and leaving it in the middle of a Download leaves the set un-removed so the next pull to refresh fetches it. Not covered by anything run so far.