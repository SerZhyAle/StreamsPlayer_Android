# S0004 - Phone shell: catalog screen and inline radio

**Status:** BlockNeedUserTest
**Status note:** Probe literal: S0004: phone catalog shell opened
942 phone tests and four APKs pass; device recovery/full retry window, final EOF cleanup and noLegal smoke pass. First-screen icons and player budget verified. Manual TalkBack, physical gamepad/TV remote and owner layout review remain; source carry prepared in S0013.
**Phase:** 4 of 10 (handoff section 8)
**Verification level:** device - the catalog opens and a radio channel plays from the list;
the fresh independence check ran with FastMediaSorter disabled, so its installed components were unavailable.

## 0. Source

`docs/BOOTSTRAP_HANDOFF.md` section 8 item 4, sections 2 (goals 1 and 5), 3 (localization, accessibility,
performance), 5, 6, 6a; STREAM-BANK documents 03 and 05 as the behaviour reference.

## 1. Goal

The phone catalog screen (grid and list, search, filters, pins, collections) and inline radio with ICY
metadata and its background service, ported from FastMediaSorter.

## 2. Scope

- UI placement settled before implementation (owner review of the layout). The screen map and the donor split
  (ported behaviour classes, view layer new from the Windows scenarios) come from S0011; this ticket does not
  start before S0011 decisions D1 and D4 are answered. Until then "ported from FastMediaSorter" in section 1
  means the behaviour layer, not the screen layout.
- From S0011: a persistent bottom radio player with notification and media-session controls; a channel actions
  sheet; filters and sort in a bottom sheet with a marker that survives closing it; a warning before a channel
  address that carries credentials is shared or copied.
- EN/RU/UK strings from the first build under the communication policy; TalkBack labels, touch targets, state
  not shown by colour alone; INPUT-PARITY for touch, gamepad and TV remote.
- ICON-SET, ICON-RENDER and ICON-EXTERNAL from the first screen (pointer `ICONOGRAPHY.md`): every glyph mapped to
  a vocabulary id or declared product-private, the stream bank's favicons and their stand-ins as ICON-EXTERNAL
  says. The three registry rows move from `pending` when conformance rung 2 holds.
- Port ledger rows for every ported file.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0003 (stream core), S0011 (product model, decisions D1 and D4), S0012 (timer and
  recents surfaces).
- **Owner decisions:** none from handoff section 4; the UI placement review happens inside this ticket, and it is
  the moment to say whether the animated backdrop (`WAVE-PARTICLES`) is adopted - today it is not (see
  `docs/contracts/README.md`, reviewed and not bound).

## 4. Acceptance

- [x] Handoff section 9 item 1, catalog and radio half: catalog opens, a radio channel plays from the list.
- [ ] Owner layout review from section 2. Final release review artifacts: `temp/S0004/owner-light-catalog.png`, `owner-dark-catalog.png`, plus `finish-light-actions.png`; the wave backdrop remains unbound.
- [x] Persistent bottom radio player with media session, ICY metadata, and background playback service (`AudioPlaybackService`).
- [x] Filter & Sort bottom sheet with active-filter indicator and facets.
- [x] Channel actions bottom sheet with secure copy/share credential warning dialog (`StreamAddressSecurity`).
- [x] Add/edit manual channel dialogs with validation.
- [x] Tri-lingual localization (EN, RU, UK) conforming to communication policy.
- [ ] Live TalkBack and physical gamepad/TV-remote pass. Labels, targets, touch operation, synthesized gamepad keys and D-pad focus have automated device evidence; analog translation has donor unit coverage.
- [x] ICON-SET / ICON-RENDER / ICON-EXTERNAL first-screen conformance rung 2 and own registry promotion; mapping and fresh light/dark measurements are documented in `docs/PHONE_SHELL_ICON_MAP.md`.
- [x] Favicon atlas cache and slicing (`FaviconAtlasSlicer`).
- [x] Zero uncarried port ledger drift: 702 rows, 699 in step, three explicitly decided broadcast files outside S0004.
- [x] Ported player budget: unchanged buffering/retry executable code, ten-minute playback observations and real full-window recovery/terminal checks. No comparative donor CPU benchmark is claimed.

## 5. Implementation audit and corrections (2026-10-10)

S0011 D1 and D4 are answered: port the behaviour and provide the new catalog view with persistent live-edge radio. The broader S0011 decisions remain in their own ticket.

- Publish the combined catalog state to the screen; refresh collection membership and reset the complete filter, including query and media chips. Fix list/grid switching and give grid titles the tile width.
- Restore sheets through Fragment arguments/results instead of constructor callbacks. Scroll sheets in landscape; keep invalid add/edit dialogs open; persist explicit media kind and category.
- Detach the Activity from service playback without stopping radio, reattach the running source after recreation, and reject stale connection callbacks. Pause keeps a resumable bar; resume starts a fresh live connection, including system media-session commands.
- Port the donor service retry and ICY update behaviour. Forward HTTP response headers explicitly: Kotlin interface delegation did not forward the Java default method, so ICY framing headers were previously lost. Publish ICY artist/title through Media3's session and notification. Enable supported cleartext HTTP/LAN streams.
- Correct the funnel and radio fallback glyphs, record the donor file in the port ledger, and map shell glyphs to vocabulary ids. Remove the unreferenced playing drawable; use the mapped play/pause glyphs.
- Use the actual donor outline/filled pin pair so pin state differs by shape. Give search a themed surface, text, hint and icon tint; correct dark-theme contrast. Normalize authored EN/RU/UK strings to the communication policy.
- Port the donor analog-stick translator and its nine tests; wire catalog focus/scroll, Start/Ctrl+F search and Y/Menu/Shift+F10 context actions. Add explicit reverse focus links from More through Pin to the channel card.
- Clear the failed media item and stop playback before stopping the service on terminal failure: a bound controller otherwise keeps the exhausted transport alive. Cancel pending retries on pause. Keep successful-play recording in the service for service playback, so attaching/recreating the shell does not count another play.
- Restore the donor's 10-second delayed auto-stop after clean EOF, with its playing/BUFFERING/READY guard and cancellation on a new READY/BUFFERING state, error or destruction. Apply the same explicit item cleanup when it fires, so an ended radio does not leave a foreground session behind the bound catalog controller.
- Pin Robolectric's default test SDK to 34, matching the explicit UI tests, and bound test-worker heap/lifetime. This removes the native-font mixed-SDK collision and heap exhaustion encountered in the full suites.

### Deliberate donor deviation

The donor data-source factory defaults to `Icy-MetaData: 0`. Preserve that default and opt radio callers into `1`, as required by STREAM-BANK 06 sections 2.2 and 3.2 and this ticket's ICY acceptance. This changes neither the stream-bank payload schema nor the shared contract. Contract pointers, handoff and other player constants remain unchanged.

The terminal-service cleanup (retry exhaustion and delayed EOF stop) and duplicate-success repairs need source carry. The exact, unapplied donor patch is [source-recovery-followup.patch](S0004_phone-shell/source-recovery-followup.patch), carried with the source coordination work in S0013. FastMediaSorter was not edited. Radio buffer configuration and both retry policies retain donor executable parity; no timeouts, retry windows or buffering constants were changed.

## 6. Verification evidence (2026-10-10)

- Final unfiltered Gradle command: `./gradlew.bat :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest :phone:assembleStoreDebug :phone:assembleNoLegalDebug :phone:assembleStoreRelease :phone:assembleNoLegalRelease --init-script temp/S0004/test-memory.gradle --no-parallel --max-workers=2 --continue --console=plain`. EXIT 0, BUILD SUCCESSFUL in 7m 16s after the final acceptance marker and EOF handler: 470 store + 472 noLegal = 942 tests, zero failures/errors/skips. All four APKs, including both minified release variants, built with release lint gates. Exact locked wrapper, output and JUnit totals: `temp/S0004/final_verify.ps1`, `final-gradle.log`, `final-test-counts.json`. Heap/fork settings also live in `phone/build.gradle.kts`; the local init script places test scratch on C: after P: filled.
- Pixel_6 emulator, Android API 33, store-debug APK, no donor app: catalog import added 19,404 sources and loaded the favicon atlas. Manual fixture entry/invalid URL checks passed. Serialized runtime automation recorded 13 PASS, 0 FAIL: ICY bar/session metadata, readable grid, list toggle, background playback, sheet/radio recreation, pause retention, fresh resume connection, filter marker, complete clear and credential warning. Evidence: `temp/S0004/runtime.log`, `runtime-verdict.txt`, `verified-*.png` and XML snapshots. Synthetic loopback radio server stopped after verification. No claim of device coverage for noLegal.
- Fresh `pwsh -NoProfile -File tools/port-ledger/check-drift.ps1 -Json`: EXIT 0, 702 rows, 699 in step, three explicitly decided broadcast files, no uncarried files. The four additional ledger rows cover the input translator, intent, donor tests and outline pin.
- Final catalog validation: EXIT 0, 8 OK / 1 WARN / 0 FAIL (S0008 warning parked in S0015); release-queue validation: EXIT 0, 15 tickets, no drift. Source/reference search found no S0004 probes or stale DONE path. EN/RU/UK string-key parity and the new validation message were checked with XML parsing: EXIT 0.
- Earlier scoped tests (114 total) and earlier full suites are superseded by the final 942-test verdict. Earlier failed runs remain in scratch, including native-font collision, heap failures and disk exhaustion. Disk exhaustion damaged Gradle's binary test results; the next attempt failed before test execution while reading them. Those generated binary directories were moved to a named C: temporary archive, and the final fresh suites passed. Logs include `final-gradle-disk-failure.log`, `final-gradle-corrupt-results.log` and the earlier successful `final-gradle-before-marker.log`. No failures or skipped cases were hidden from the final run.
- Corrected surfaces: `python temp/S0004/finish_visual_check.py`, EXIT 0, 19 PASS. Fresh light/dark screenshots and XML verify changed glyph contrast 8.03-10.91:1, search text 8.88:1 light / 10.91:1 dark, distinct pin masks after removing colour, and 213 identical string keys in each authored locale. `docs/PHONE_SHELL_ICON_MAP.md` distinguishes this fresh evidence from the older broad screen sweep. Live TalkBack speech and a physical analog controller remain manual.
- Sustained fixture playback: 602 seconds, 20/20 samples playing; PSS 61,496-71,445 KiB, final five 70,241-70,449 KiB; sampled CPU 6-22.7% of one emulator core (median 9.45%). This run predates the final play-count/focus/string corrections and uses the same unchanged buffer/retry code; it is an observation, not a same-device comparative donor CPU benchmark. Evidence: `temp/S0004/finish-budget.csv`, `finish-sustained-and-graceful-fixture.log`. `python temp/S0004/budget_parity.py` proves executable parity of `RadioStreamBufferConfig`, `StreamServiceRetryPolicy` and `StreamInlineRetryPolicy`, EXIT 0.
- Inherited recovery limitation: a clean progressive HTTP EOF reaches ENDED without `onPlayerError` and does not auto-reconnect. The donor uses delayed auto-stop for ENDED; that missing lifecycle branch is now ported. The existing network-error retry contract was exercised separately with a silent socket/read timeout; no new EOF reconnection policy was invented. Any new reconnection behavior remains a source-first decision in S0013.
- Signed store release on the API 33 emulator, donor disabled, before the final marker/EOF handler: 19 PASS device postconditions covering Start search, D-pad row/reverse focus, Y/Menu sheet actions and B close, A play/pause/fresh resume, session ICY metadata, automatic recovery after read timeout, the entire five-minute retry window, hidden exhausted bar, no started/foreground exhausted service, replay after exhaustion and stopped playback/no foreground service after Close + HOME. Terminal observation completed 332 seconds after the second outage (the policy clock starts at the first error, after load/read retries). Logs: `temp/S0004/finish-timeout-retry.log`, `finish-device-verdict.txt`, `finish-retry-exhausted.png`. The original driver incorrectly required service destruction after HOME; HOME retains the Activity and warm controller. Its failed assertion is preserved; `finish_home_check.py` verified the corrected postcondition, EXIT 0. No product change was needed for that harness assumption. The later EOF handler cancels on errors/READY/BUFFERING and does not change either retry policy.
- Final signed store release EOF check: EXIT 0, five PASS. A finite HTTP/ICY fixture begins playing, then its clean EOF triggers delayed auto-stop; the bar disappears, foreground/start flags clear, queue and metadata empty while the catalog remains bound. Evidence: `temp/S0004/finish-eof.log`, `finish-eof-verdict.txt`, `finish-eof-stopped.png`. The initial driver mistakenly used legacy `active item id=-1` as an empty-queue predicate; the bridge retains id 0 when empty. That failed assertion is preserved in `finish-eof-legacy-id-assumption.log`; the complete corrected run passed using queue/metadata postconditions.
- Fresh contract metadata check: EXIT 0, 18 pointers, 13 own adoption rows and three required playlist vectors match. This does not replace device conformance evidence.
- Signed noLegal release smoke: installs over the same application id without clearing the database, catalog launches, Filter & Sort opens/closes, no AndroidRuntime fatal in the sampled recent log. EXIT 0, three PASS (`temp/S0004/finish-nolegal.log`, `finish-nolegal-verdict.txt`). The signed store release was restored and FastMediaSorter re-enabled after the independence checks.
- Final release owner-review snapshots: light/dark catalog loaded with the preserved bank, inspected visually, light mode restored (`temp/S0004/owner-review.log`, `owner-light-catalog.png`, `owner-dark-catalog.png`). These screenshots provide review material; owner approval remains unchecked.

## Last Audit

Verdict: **BlockNeedUserTest**. Implementation and available automated verification are complete; the live accessibility/controller pass and owner layout approval remain. Automated evidence does not constitute owner layout approval.

- PASS: catalog state/search/collections; list/grid layout; filter/clear behaviour; editable manual source fields and validation; action-sheet recreation/security gate; service lifecycle/live-edge controls; ICY request/decoding/session metadata; build variants and donor drift.
- PASS: first-screen icon mapping/registry promotion, corrected light/dark contrast and pin-state shape; full phone test suites; executable donor player-budget parity and sustained playback observations.
- MANUAL: live TalkBack, physical gamepad/TV-remote and analog operation; owner layout approval required by section 2. A donor CPU baseline was not collected.

The checked acceptance items have the evidence above; unchecked items prevent Verified status. The harness blocked `BlockNeedUserTest` without an acceptance probe, so `MainActivity.onCreate` retains the temporary entry marker `S0004: phone catalog shell opened`. It confirms entry for the debug/manual pass, not every action; remove it after manual acceptance and before Verified/closure. The journal's literal-probe contract names this exact message. Documentation refreshed: this specification, docs index, contract pointers and shell icon map. No shared contract text or handoff edits were needed. Existing missing spec paths for S0008/S0009/S0010/S0012 remain unrelated hygiene work.
