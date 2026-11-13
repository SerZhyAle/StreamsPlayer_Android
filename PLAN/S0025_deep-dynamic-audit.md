# S0025 - Deep dynamic audit: five properties measured from outside the process

**Status:** BlockQuestions
**Status note:** Phase B session ran 2026-11-13 (b-20261113-2, rollback proved, 29 cells measured, P1 filed as S0070). Q: how are the phase B P2/P3 findings F-0005..F-0012 handled at the phase exit? Q: adjust, keep or drop budget B-4a? Q: which rig session runs next - phase C, phase D or none before the release? Options in section Questions (v1, 2026-11-13). No compile command (no compiled source edited).
2026-10-25: the tooling lives in `tools/audit/` with every documented exit code forced by fixtures
(coverage 12 capabilities, 110 cells; rig 87 fixture tests, 11 live checks), and a full session -
instrument self-tests, backup manifest, control run, the SC-01, SC-06, SC-22 scenes, a proven
rollback - is recorded in `temp/S0025/a-20261025-1/`. Baseline: B-1 p50 1,475 ms, B-3 159 ms,
B-5a/B-7b within their hypothesis budgets; B-2 and B-5b over, plus a deadline-miss observation
with no B-row - three findings, all routed to the owner (14.1, 14.2, 14.3).
2026-11-08: the owner answered section 14; the queue pass of the same day built what the answers add (section 14,
"Queue pass 2026-11-08"): B-1, B-3, B-5a, B-7b promoted in `tools/audit/budgets.json`, the findings ledger moved to
`docs/audit/findings.jsonl`, the debug-only main-Looper ping, the 100,000-entry M3U generator, the report-only phase A
smoke of the pre-release sweep, `tools/audit/` aligned to `CHECK-VERDICT` 0.14. Phases B-E not started: their runs
wait for the owner's question 14.8.
2026-11-13: the phase B session the owner named ran (section 14, "Queue pass 2026-11-13"): loopback stream fixtures
(ffmpeg 9.0.2 and mediamtx 1.21.3 in the gitignored tool cache), the `rig.py phase-b` driver, one full session
`b-20261113-2` with a proven rollback; 29 phase B cells answered `measured`, SC-21 P3/P4 still open. Nine findings
F-0004..F-0012: one P1 (catalog search shows the stale list for 67.6 s on the real catalog) filed at once as S0070,
six P2, two P3 - the P2/P3 review of the phase exit and the next session are the owner's (section "Questions (v1,
2026-11-13)").
**Complexity:** hard - assessed at the queue pass 2026-11-08: a measurement subsystem across the host rig, a debug
app instrument and the release sweep; the remaining phases need new loopback fixtures (HLS, RTSP, fault injection),
live device sessions of minutes to overnight and a manual pass by the owner.
**Phase:** quality, cross-cutting, with its own risk-ordered phases (section 11); interleavable with
feature tickets
**Verification level:** live measurement from outside the app process and automated scripts with fixed
exit codes (ranks T and L); property 5 (understanding) adds manual evidence (rank V where a run
records it).

## Remaining development

*As of 2026-11-13. Phase A is complete; phase B ran on 2026-11-13 (`b-20261113-2`, section 14, "Queue pass
2026-11-13"); the rig drives phase A (`auditrig/phase_a.py`) and phase B (`auditrig/phase_b.py`).*

1. **Phase B exit** (decision 14.5): the owner reviews the P2 and P3 findings F-0005..F-0012 and the B-4a band
   (section "Questions (v1, 2026-11-13)"). Two phase B cells stay open: SC-21 P3 and P4 (no sampler ran during the
   fault scene) - the next phase B re-run adds a sampler window to `scene_failures`; the DASH half of SC-08 is invalid
   in `b-20261113-2` (fixture defect, fixed the same day) and is re-run with it.
2. **Phases C and D**: one rig session per phase when the owner names it (decision 14.8.1). C - broadcast and exchange
   (SC-13, SC-14, SC-16, SC-17, SC-18); D - lifecycle and leaks (SC-01 P2-P4, SC-02 to SC-05, SC-11, SC-12, SC-22, the
   overnight runs). Each needs its scene driver written against the live device, run, and its matrix cells answered.
3. **Phase E** (the owner): the TalkBack walk, the RU and UK pass, an undo path per switch, gamepad and TV remote.
4. **Budgets still open.** B-2 now has phase B numbers (F-0004, S0070); B-4a is refuted by F-0011 unless the owner keeps
   it; B-5b waits for the phase D overnight runs; B-6 and B-10 have their first numbers (B-6 within, B-10 over: F-0006);
   B-8 within on the storm (673 KB/min device-wide); B-9 waits for phase C. The baseline report with the promoted or
   refuted budget table is part of the definition of done.
5. **First live use of the release pieces** at the next release: the sweep's smoke step with `-AuditSerial`. The ping
   ran live for the first time on 2026-11-13 (`rig.py ping`, 10 of 10 pongs, p50 0 ms).

## 0. Source

- Owner, 2026-10-11 (this session): a deep dynamic audit and testing specification over five
  properties - responsiveness, wrapped errors, no system blocking, leaks, understandability - as the
  dynamic complement to the static audits, with numbers and reproductions, not impressions.
- Canon `TESTING_AND_QA`: the evidence ladder, the four answers (pass, found-a-defect, could not
  verify, not applicable), instrument validation (an impossible reading means the instrument is
  broken, not that the subject failed), threshold decay (every number ships with its date and the one
  command that regenerates it), budgets timed on inputs of the promised size, no retry-to-green.
- `CHECK-VERDICT` 0.12 (catalog, `automated-checks/`): the exit-code vocabulary this spec borrows -
  `0` pass, `1` inspected and defective, `2` could not verify and never a pass; one machine-readable
  verdict line, last on stdout; a documented code must be reachable; fixtures force every documented
  code. Adopted as a consumer by the owner's decision of 2026-10-25 (S0035), now at 0.14
  (`docs/contracts/AUTOMATED-CHECKS.md`); decision 14.7 records it, and `tools/audit/` was aligned to 0.14 on
  2026-11-08.
- Precedents in this tree: S0004 sustained-playback sampling (`temp/S0004/finish-budget.csv`: PSS
  61,496-71,445 KiB, CPU median 9.45% of one core, 602 s), S0007 emulator evidence (cold launch
  1,095 ms on API 37; the 19,404-channel catalog), the host-side Python drivers under `temp/S0004/`,
  and the `Sxxxx:` Timber probe convention (`.sza-profile.json`, probes).
- The capability list the coverage self-test anchors on: CLAUDE.md section 1. The product's languages
  are EN/RU/UK (handoff section 3; `phone/src/main/res/values-*`, `wear/src/main/res/values-*`).

## 1. Problem

The static machinery of this repository - per-ticket audits, contract conformance, port drift, catalog
validation - proves that the code agrees with the written decisions. None of it answers how the product
behaves in use:

- how long a person and the main thread actually wait at each action;
- what a failure at a boundary looks like to the user, the log and the state;
- whether the app makes the device and other apps wait for it;
- what grows while nobody looks;
- whether a person understands what a switch does before and after, and can undo it.

This ticket specifies the dynamic complement: a catalog of scenes (what a user actually does) crossed
with the five properties, every cell answered by a number and a reproduction, or an explicit n/a with
a reason, or open with the phase that owns it. Findings are measured consequences, never impressions.

## 2. What this audit does NOT do

1. No behaviour change from this ticket. A finding moves onward (section 10); the audit measures,
   it never fixes in passing.
2. **Documented decisions are not findings.** The register this audit consumes as ground truth and
   never re-litigates:
   - the shared contract texts in the catalog: `STREAM-BANK` (buffering, live edge, routing),
     `LIVE-BROADCAST`, `DEVICE-EXCHANGE`, `USER-PLAYLIST`, `MEDIA-CLASSIFICATION`,
     `PACKAGE-VERSIONING`, `INPUT-PARITY`, `ICONOGRAPHY` - their consequences are the contract's
     to own;
   - owner decisions recorded in CLAUDE.md section 3 and the handoff section 3 (application ids,
     signing, variants, minSdk reference, EN/RU/UK, the donor performance budget carried over);
   - handoff section 6a: full port, ledger, drift check - the audit does not judge the port law,
     only measures what the ported behaviour does;
   - deliberate deviations with a written record (S0004's ICY opt-in deviation, S0013's dated
     exceptions, the decided tourist rows of the port ledger).
   A finding exists only as a measured consequence of a decision (a number crossing a budget, a scene
   failing) or as behaviour no written decision covers. Where a decision causes the number, the
   finding names the decision's owner - the ticket or contract that holds it - and the catalog
   amendment path of handoff 6a item 3 applies.
3. No re-running of static judgments: string parity across locales, ledger drift, pointer sync,
   package versioning are existing checks; their green is a precondition for a run, not this audit's
   subject.
4. No telemetry and no external endpoints: every measurement is local - emulator or device, loopback
   fixtures, local file reads.
5. No rewrite of ported behaviour to make a number look better; that path is the contract amendment
   first (handoff 6a item 3).
6. Not a replacement for device acceptance (S0004, S0007) or the `LIVE-BROADCAST` section 6
   handshake: those remain their tickets' gates; this audit reuses their scenes and hands them numbers.
7. Not a `DIAGNOSTIC-REPORT` export: nothing here produces the user-facing diagnostic bundle, so the
   conditional pointer (`docs/contracts/DIAGNOSTIC-REPORT.md`) stays unowed.
8. No production user data on the rig: fixtures only; the owner's real exchange connection string
   never enters a run.

## 3. The five properties on this platform

The owner's five properties, with this platform's instruments. The Windows instruments named in the
request have no direct counterpart here; the mapping below is the rig's authority, and where no
counterpart exists the matrix cell says n/a with that reason.

| Property | What it asks | Instrument on this platform |
| --- | --- | --- |
| P1 Responsiveness | how long the user and the main thread wait | injected-input-to-visible-change latency; frame-clock stall window (`dumpsys gfxinfo framestats`); probe-line latency; optional debug-only main-Looper ping (14.2) - the counterpart of a WM_NULL ping |
| P2 Wrapped errors | a boundary failure is caught where it is understood, mapped to a known outcome, logged without secrets, told to the user in human localized words, and the state stays consistent | fault injection per scene (SC-21): one clear message, no raw class names, a redacted log line, and the scene succeeds on retry without a restart |
| P3 No system blocking | the app makes no other program and no OS service wait | wake-lock posture (`dumpsys power`), audio-focus events, the bounded listener count of the broadcast HTTP server, broadcast and clipboard use, Wi-Fi lease lifetime, Doze cooperation, foreground-service types |
| P4 Leaks | nothing grows without a user-visible reason | the sampler (section 7): PSS, Java heap, Native heap, fd count, thread count, alarm count, codec and session instances, cache and store file sizes, log volume - as growth slopes with a confidence band against a control run |
| P5 Understandable and accessible | a person knows what a switch does before and after and can undo it; EN/RU/UK; assistive technology works | TalkBack walk, string audits in the three locales, an undo path per switch, input parity (touch, gamepad, TV remote per `INPUT-PARITY`) |

Platform n/a's declared once, so no matrix cell repeats them: GDI/USER handles and the Windows
registry have no counterpart - file descriptors, binder references and DataStore files are the
analogues actually sampled; COM has none - codec, player and service instances are sampled instead;
RDP has none - SC-05 covers the nearest lifecycle analogues (lock, split screen, standby buckets,
update, process death).

## 4. Scene catalog

A scene is what a user does, not what a class does. Lifecycle covers sleep and lock and their
analogue set; RDP as such does not exist on this platform (section 3).

| Id | Scene | The user's action | Where the risk lives |
| --- | --- | --- | --- |
| SC-01 | First start | fresh install, permissions asked, catalog import, first frame | import size, permission refusals |
| SC-02 | Warm start | return to a running player, task switch back | reattach, duplicate players |
| SC-03 | Exit | back out, swipe away, task removal | service, notification, session teardown |
| SC-04 | Sleep and lock | screen off, Doze, standby bucket, with and without playback | wake locks, live edge on return |
| SC-05 | Switch and upgrade | lock/unlock, split screen, install-over update, process death and restore | state restore, no data loss |
| SC-06 | Catalog work | search, filters, list/grid, pins, collections, manual channel add and edit | list cost at catalog size |
| SC-07 | Radio from the list | play, pause, switch, ICY metadata, notification controls | service hand-off, retry window |
| SC-08 | Fullscreen video | HLS/DASH open, live edge, rotation, close | player lifecycle, surface |
| SC-09 | RTSP playback | open and play an RTSP channel | interleaved TCP, stalls |
| SC-10 | M3U import | import a playlist file | big input, malformed file |
| SC-11 | Widgets and tile | channel tile widget, radio QS tile, broadcast entry widget | background start limits |
| SC-12 | Settings switches | every toggle: read the wording, flip, observe, revert | wording, undo, side effects |
| SC-13 | Phone broadcast | start (microphone, then camera), on air, stop; permission refusal | capture, Wi-Fi lease, server cap |
| SC-14 | Watch broadcast and flows | watch microphone on air, stop; list, pins, open-on-phone | Wear lifecycle, Data Layer |
| SC-15 | Watch face | apply the face, ambient and interactive, complications | none in product code (see 3a) |
| SC-16 | Exchange join | quick join, connection string, QR scan, directory list | TLS, trust decisions |
| SC-17 | Receiver playback | play a foreign broadcast over LAN, RELAY, TUNNEL | proxy, pinning, live edge |
| SC-18 | Broadcaster publish | publish through the exchange (S0021 surfaces) | records, listener caps |
| SC-19 | Storms | network flap, channel-switch storm, rapid pin taps, broadcast start/stop storm, slow-server live-edge fight | retry policy under load |
| SC-20 | Big inputs | full real catalog, synthetic oversized M3U, long and unicode names, oversized descriptor | memory, list latency |
| SC-21 | Failures | dead host, slow host, wrong port, self-signed certificate, HTTP error codes, no network, denied permission, full storage, server rejection | every property-2 boundary |
| SC-22 | Idle | hours stopped; hours playing | the leak window |

## 5. The matrix

Cell states: `measured` - a number or outcome, with its date, run folder and one regenerating
command; `n/a (face)` - the platform n/a 3a below; `open` - with the owning phase from section 11.
At filing the matrix was open by design. The first `measured` cells are the phase A baseline of
2026-10-25 (run `a-20261025-1`); the phase B session of 2026-11-13 (run `b-20261113-2`) answered 29 more; every other
cell is still `open` with its owning phase. Prior dated
observations exist only in the provenance column of section 8; they are promoted into cells only
when re-measured under the rig.

Cell grammar, as `tools/audit/coverage_check.ps1` reads it: `open (A..E)` - the owning phase;
`n/a (<tag>)` - a tag declared in the gate's keyword table (today `face`); `measured (YYYY-MM-DD; <run-id>)` -
the date and the run folder `temp/S0025/<run-id>/`, whose `environment.json` holds the regenerating command.

| Scene | P1 | P2 | P3 | P4 | P5 |
| --- | --- | --- | --- | --- | --- |
| SC-01 | measured (2026-10-25; a-20261025-1) | open (D) | open (D) | open (D) | open (E) |
| SC-02 | open (D) | open (D) | open (D) | open (D) | open (E) |
| SC-03 | open (D) | open (D) | open (D) | open (D) | open (E) |
| SC-04 | open (D) | open (D) | open (D) | open (D) | open (E) |
| SC-05 | open (D) | open (D) | open (D) | open (D) | open (E) |
| SC-06 | measured (2026-10-25; a-20261025-1) | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | open (E) |
| SC-07 | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | open (E) |
| SC-08 | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | open (E) |
| SC-09 | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | open (E) |
| SC-10 | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | open (E) |
| SC-11 | open (D) | open (D) | open (D) | open (D) | open (E) |
| SC-12 | open (D) | open (D) | open (D) | open (D) | open (E) |
| SC-13 | open (C) | open (C) | open (C) | open (C) | open (E) |
| SC-14 | open (C) | open (C) | open (C) | open (C) | open (E) |
| SC-15 | n/a (face) | n/a (face) | n/a (face) | n/a (face) | open (E) |
| SC-16 | open (C) | open (C) | open (C) | open (C) | open (E) |
| SC-17 | open (C) | open (C) | open (C) | open (C) | open (E) |
| SC-18 | open (C) | open (C) | open (C) | open (C) | open (E) |
| SC-19 | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | open (E) |
| SC-20 | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | open (E) |
| SC-21 | measured (2026-11-13; b-20261113-2) | measured (2026-11-13; b-20261113-2) | open (B) | open (B) | open (E) |
| SC-22 | open (D) | open (D) | open (D) | open (D) | open (E) |
3a: SC-15 is the Watch Face Format module - a declarative, resource-only package in which no product
code executes (handoff section 5, S0008). It renders on the system's renderer, holds no locks,
services or capture, and allocates no runtime state of this product; P1-P4 therefore have no
measurable subject there. P5 stays open: the face's readability and branding are user-facing.

## 6. Coverage script and self-test

- `tools/audit/coverage_check.ps1` (Code.Scripts lock domain per `.sza-profile.json`) reads two live
  inputs: the matrix of section 5 in this spec, and CLAUDE.md section 1.
- It asserts: every capability noun of CLAUDE.md section 1 - phone app, Wear OS app, watch face,
  internet radio, live video (HLS/DASH), RTSP, phone microphone broadcast, phone camera broadcast,
  watch microphone broadcast - maps to at least one scene through a keyword table kept beside the
  script and reviewed in every diff; every scene row carries a cell for all five properties; every
  cell is a legal state; every `measured` cell carries a date and a command pointer; every `open`
  cell names a phase. The mapping table may add surfaces beyond CLAUDE.md (widgets, M3U, exchange)
  and may never drop one.
- Exit codes and the verdict line in the `CHECK-VERDICT` shape: `0` all covered, last line
  `coverage: PASS (<n> capabilities, <m> cells)`; `1` a gap, last line `coverage: FAIL (<gap>)`;
  `2` an input unreadable - CLAUDE.md or the matrix missing or unparsable - last line
  `coverage: COULD NOT VERIFY (<what>)`. Exit `2` is never a pass and never collapses into `0`.
- Self-test: fixture inputs force each documented code - a capability with no scene (`1`), a
  truncated CLAUDE.md (`2`), the real pair (`0`). The fixtures run before any coverage verdict is
  trusted; a gate that has not run since the last change is itself unverified.

## 7. Measurement rig, from outside the process

1. **Host-side only.** The rig runs on the host against adb: PowerShell and Python under
   `tools/audit/` (committed, Code.Scripts lock), run folders under `temp/S0025/<run-id>/`
   (gitignored). App-side additions are limited to the existing `Sxxxx:` debug probes; any new
   in-app probe is decision 14.2 and debug-build-only.
2. **Instruments.**
   - Frame clock: `adb shell dumpsys gfxinfo com.sza.streamsplayer framestats` sampled across a
     scripted action; reports missed frame deadlines and the longest main-thread stall in the window.
   - Injected-input latency: `adb shell input tap|keyevent` timestamped on the host; the wait ends
     at the first UI-tree change the driver observes (a UI Automator wait, millisecond clock). This
     is the "user wait" number; the frame clock is the "main thread wait" number.
   - Ping (14.2, allowed 2026-11-08): a debug-only receiver that touches the main Looper and logs
     `SpAuditPing: pong run=<nonce> seq=<n> queue_ms=<ms> uptime_ms=<uptime>` - the direct
     counterpart of a WM_NULL ping, without one line of product code in release builds
     (`rig.py ping`). The line carries a stable tag, not the `S0025:` probe prefix: the
     instrument is permanent, and a ticket-id line exists only while its ticket waits for a
     manual test (resolved in section 14, "Queue pass 2026-11-08").
   - Sampler, every 5 s during a scene: `dumpsys meminfo` (TOTAL PSS, Java heap, Native heap),
     `/proc/<pid>/status` Threads, `/proc/<pid>/fd` count, `dumpsys alarm` count for the package,
     `dumpsys media.codec` codec instances, `dumpsys power` wake locks held by the package,
     `dumpsys activity services` foreground state, cache and DataStore file sizes over adb (root on
     the emulator image), `logcat -d` byte volume. One CSV per scene, the `finish-budget.csv` shape.
3. **Growth slope with a confidence band.** After a scene-specific warm-up and before teardown, fit
   a line over the stable window; report the slope per hour and a 95% band. A leak finding requires
   the band's lower bound above the control run's band and above zero.
4. **Control run without the app.** The identical rig and duration against the idle emulator with
   the app force-stopped, and a second with the app installed but never started: this records probe
   self-noise and the system's own churn (launcher, SystemUI). No cell may cite a number on a metric
   the control run moved beyond its own band.
5. **Invalid-run rule.** A run exits `2` (`COULD NOT VERIFY`) and may produce neither a finding nor
   a pass when: the blank-run band overlaps the measured band on that metric; the instrument's own
   jitter exceeds 30% of the effect; the sampler lost more than 10% of its samples; a scene step
   failed mid-way and the state was not restored. The invalid run stays in its run folder with the
   reason - a discarded run is reported, never deleted.
6. **Instrument self-test first.** Every rig script has a fixture mode against a known-value
   synthetic subject (a loopback server of known size, a process of known thread count), run before
   every session; an impossible reading stops the session with `2` before any scene runs.
7. **Isolated disposable profile.** One dedicated emulator image family for the audit. Before a
   session: a fresh snapshot (`sp-audit-clean`), plus `adb pull` of both application data
   directories and `settings list global|system|secure` dumps. The session refuses to start (exit
   `2`) without a backup manifest it has verified complete. Rollback runs in `finally`: restore the
   snapshot, compare the package list and settings digests, and verify the recovery manifest - the
   folder listing of everything the rig wrote (packages installed, settings keys touched, files
   pushed, accounts added) - against empty. A rollback that cannot prove itself fails the session
   with `2` and names what did not restore.
8. **Every script speaks `CHECK-VERDICT`.** `0` scene within budget, `1` defect, `2` could not
   verify; one verdict line, last on stdout; every documented code forced by a fixture.

## 8. Budgets - hypotheses until the phase A baseline

Every row is a **HYPOTHESIS**. Phase A measures the baseline on the canonical rig; the owner
promotes, adjusts or drops each row (14.1). A promoted number carries its date and its one
regenerating command from then on (threshold decay).

**Promoted 2026-11-08** (decision 14.1, on the phase A baseline of 2026-10-25, run `a-20261025-1`; regenerated by
`py -3 tools/audit/rig.py phase-a --serial emulator-5556 --apk phone/build/outputs/apk/store/debug/phone-store-debug.apk --run-id <new-id>`;
the data is `tools/audit/budgets.json`): B-1 (p50 1,475 ms against 2,000), B-3 (159 ms against 700), B-5a (idle fd
slope band [+0, +0]/h against +2/h), B-7b (idle store slope band [+0, +0] KiB/h against 0). No promoted row is a
release gate. Every other row below is still a hypothesis.

| # | Metric | Proposed budget | Provenance of the guess |
| --- | --- | --- | --- |
| B-1 | Cold start to first catalog frame | <= 2,000 ms p50 | the wear app measured 1,095 ms once on API 37 (S0007); the phone never measured |
| B-2 | Injected tap to visible change, catalog actions | <= 1,000 ms p95 | none - pure hypothesis |
| B-3 | Longest main-thread stall in a scripted scene window | <= 700 ms | none - pure hypothesis |
| B-4 | Sustained playback PSS | band 60-75 MiB, slope <= +5 MiB/h over at least 4 h | S0004: 61,496-71,445 KiB over 602 s, no band, no control run |
| B-5 | fd and thread slopes | fd <= +2/h idle, <= +6/h playing; threads stable within +/- 3 after warm-up | none - pure hypothesis |
| B-6 | Codec and session instances | return to baseline within 10 s of stop | none - pure hypothesis |
| B-7 | Cache and store files | <= +50 MiB per 8 h session; no growth while idle-stopped | none - pure hypothesis |
| B-8 | Log volume | <= 2 MiB/min in debug builds over a storm scene, redaction verified per section 12 | S0016's redaction tests exist; volume never measured |
| B-9 | Broadcast start to on air | <= 5 s on LAN; stop releases the wake lock and Wi-Fi lease within 2 s | S0007 observed the flow, no timing |
| B-10 | Error surfacing | a terminal boundary failure shows its localized message within 1 s, and a retry of the scene succeeds without restart | S0004's recovery checks observed the behaviour, no timing |

## 9. Severity and proof ranks

- **P0**: data loss; a crash or ANR loop; a secret in a log; a system-wide hold (the device kept
  awake, storage or network exhausted while idle); a cell beyond budget with no workaround.
- **P1**: a property violated with user-visible harm; a wrapped-error breach that leaves
  inconsistent state; a workaround exists but costs the user.
- **P2**: degraded experience; a budget crossed by a small margin; a leak slope present but tiny.
- **P3**: polish - wording, timing margin, cosmetics.

Proof ranks: **R** reasoned - a code reading that names the line; **V** observed once - one run's
log, screenshot or recording; **T** automated - a script exits `1` reproducing it; **L** live
measured - numbers, a control run, one regenerating command.

Filing rule: a **P0 or P1** finding may be filed only with rank **T or L**; a P2 needs at least V;
a P3 may stand on R. No matrix cell closes as `measured` for a scene where a P0/P1 was raised, on
R or V alone.

## 10. Findings

- One finding: scene, property, the number or outcome, rank, budget crossed, reproduction command,
  run folder. The ledger lives at `docs/audit/findings.jsonl` (decision 14.5, 2026-11-08; the three
  phase A findings of `temp/S0025/findings.jsonl` are carried across when it is created). P0 and P1
  findings become tickets at once, P2 and P3 are reviewed by the owner at each phase exit.
- The section 2 register applies verbatim: where the measured consequence belongs to a documented
  decision, the finding names the owning ticket or contract and moves there; this ticket records
  the number and stops.
- Findings never become silent fixes, and the audit never re-runs until green: a run after a fix is
  a new run, cited next to the old one.

## 11. Phases by risk

Ordered by blast radius and novelty, each phase gated by its own exit review:

1. **A - Rig and baseline.** Instruments self-tested, control runs recorded, the baseline measured
   on the current build, budgets promoted or refuted, the coverage script green on the real pair.
   Gate: every instrument has a fixture forcing each of its exit codes.
2. **B - Media core under stress.** SC-07, SC-08, SC-09, SC-19, SC-20, SC-21 against P1-P4: the
   oldest ported code and the widest user surface.
3. **C - Broadcast and exchange.** SC-13, SC-14, SC-16, SC-17, SC-18 against P1-P4: the newest
   code, capture plus network plus trust.
4. **D - Lifecycle and leaks.** SC-01 through SC-05, SC-11, SC-22 against P3 and P4: the long
   overnight runs.
5. **E - Understanding and accessibility.** P5 across every scene: the TalkBack walk, the
   three-locale string pass, an undo path per switch, gamepad and TV remote per `INPUT-PARITY`,
   carried out by a person in each locale the owner appoints.

A phase may interleave with feature tickets; a P0/P1 raised in any phase is filed at once and never
waits for its phase's gate. No cell stays `open` past phase E without an owner decision (14.6).

**Cell ownership (amendment of 2026-10-23, from the implementation).** The phase lists above leave cells
without an owner - the coverage gate requires every `open` cell to name one - so the matrix assigns them as
follows, and the owner may re-assign any of them (14.6):
- P5 of every scene: phase E, as listed.
- SC-01 P1 and SC-06 P1: phase A - they carry the baseline numbers B-1 (cold start) and B-2/B-3 (catalog
  actions), which phase A measures.
- SC-06 P2-P4 and SC-10 P1-P4: phase B with SC-20, whose big catalog and oversized M3U they share.
- SC-01 P2-P4, SC-02 to SC-05, SC-11, SC-12 and SC-22 against P1-P4: phase D. Phase D's own text names P3 and P4;
  P1 and P2 of the lifecycle scenes follow the same runs rather than getting a phase of their own.
- SC-13, SC-14, SC-16, SC-17, SC-18: phase C, as listed.

## 12. Artifact privacy

- Run folders live under `temp/S0025/` (gitignored); what enters the repository is this spec, later
  the matrix update, the journal row and - by decision 14.5 of 2026-11-08 - the findings ledger
  `docs/audit/findings.jsonl` (numbers and references only, no private data) - nothing else.
- Redaction follows the product's own logging rules: broadcast identifiers as 8 hex of their
  SHA-256, endpoint URLs never logged whole, exchange connection strings and manual channel names
  never logged at all; the rig's own logs obey the same rule, and B-8 verifies it.
- Screenshots may show the public catalog; private manual channels, exchange strings and any real
  credential are redacted before a folder leaves the machine. Broadcast audio captures are deleted
  once the verdict citing them is written.
- Every artifact carries its date and the one command that regenerates it. Folders are kept until
  this ticket reaches `Verified`; after that only the baseline report, the findings ledger and the
  latest run folder per phase are kept (decision 14.6, 2026-11-08), and the invalid runs (exit `2`)
  of the deleted folders are listed with their reasons in the baseline report first.

## 13. Definition of done

- `tools/audit/` holds the coverage script and the rig, each with fixtures forcing every documented
  exit code; the coverage script exits `0` on the real CLAUDE.md and matrix pair.
- One full rig session documented end to end: instrument self-tests, the backup manifest, a control
  run, scenes, slopes with bands, a proven rollback, and a run folder with dates and commands.
- The matrix of section 5 updated in place: at least phases A through D answered `measured` or
  `n/a` with reasons; every `open` cell names its owning phase.
- A baseline report with the promoted or refuted budget table, and the owner's answers to section 14
  recorded in the journal.
- Any findings filed per section 10 with their rank; the journal updated through the harness only.

## 14. Owner inputs (Approval gate)

- **Related tickets:** S0004, S0005, S0006, S0007, S0008, S0009, S0013, S0016, S0017, S0018, S0019,
  S0020, S0021, S0022, S0023, S0024 - their surfaces are the scenes; S0013's source carry may move
  measured numbers.
- **Owner decisions:** all 8 items answered (items 1-7 on 2026-11-08, item 8 on 2026-11-10; see blocks below)
  1. **Budgets:** promote, adjust or drop each B-row after phase A; which promoted rows become
     release gates.
  2. **In-app probes:** allow a debug-only main-Looper ping receiver and any app-side counters
     (subscription and instance registries), or keep the rig purely outside - the default.
  3. **Canonical rig:** which emulator API levels (phone and Wear) and which physical devices, if
     any, are the reference the numbers are cited against.
  4. **Big input:** the synthetic oversized M3U size (proposed 100,000 entries) and whether the real
     19,404-channel catalog is the only other canonical big input.
  5. **Findings:** P0/P1 promotion to tickets at once (proposed) and at what priority; the
     findings ledger's home once it outgrows `temp/`.
  6. **Cadence and retention:** per release sweep, per ticket, or on a calendar; how long run
     folders live past `Verified`; who carries out the manual phase E pass in RU and UK.
  7. **`CHECK-VERDICT` adoption:** whether this product writes the adoption row for the exit-code
     contract the rig speaks, or borrows the shape unbound - today's state.
  8. **Runs and stream fixtures for phases B-D** (raised by the queue pass of 2026-11-08, answered
     2026-11-10): when the rig sessions run, and what serves the HLS, DASH and RTSP streams the
     media scenes play.

  Items 1-7 were answered on 2026-11-08; item 8 was answered on 2026-11-10; see the blocks below.

### Quiz decisions (2026-11-13)

- 14.8.1 Phase B rig session -> the owner names the phase B session now (media core under stress: SC-07, SC-08, SC-09,
  SC-19, SC-20, SC-21 against P1-P4, SC-06 P2-P4 and SC-10 with the 100,000-entry M3U), run on the store debug build and
  the `sp_audit_api35` emulator in one pass per decision 14.8.1 of 2026-11-10 (recommendation accepted; the other options
  were phase D first and not now). The ffmpeg and mediamtx tool cache under `temp/` (14.8.2) does not exist yet
  (checked 2026-11-13) and is part of that pass; the AVD `sp_audit_api35` exists.

### Quiz decisions (2026-11-10)

- 14.8.1 Rig sessions for phases B-D -> The owner names one rig session per phase between releases (store debug build plus the `sp_audit_api35` emulator); the queue then writes that phase's drivers and runs it in one pass (phases move as fast as the owner names them; between releases only when the owner names them per CLAUDE.md section 5a)
- 14.8.2 HLS/DASH/RTSP stream fixtures -> Download an ffmpeg build and mediamtx into a gitignored tool cache under `temp/`, never committed and never shipped; ffmpeg encodes a test pattern into HLS and DASH segments served by the rig on loopback, mediamtx serves RTSP via `10.0.2.2` (no external network dependencies, no binaries committed to git, meets section 2 item 4)

### Quiz decisions (2026-11-08)

- 14.2 In-app probes -> only the debug-only main-Looper ping receiver (debug source set, nothing in release
  builds, no instance or subscription counters) (phase A showed an outside instrument cannot resolve B-2: ~2 s
  floor per cycle against a 1,000 ms budget; F-0001)
- 14.1 Budget promotion -> promote B-1, B-3, B-5a, B-7b now, each with its date and one regenerating command;
  B-2 waits for the probe, B-5b waits for the phase D overnight runs; B-4, B-6, B-8, B-9, B-10 stay hypotheses
  until their phases (the four are within budget on a trustworthy instrument; the two over rows are
  instrument-limited or unconfirmed as growth)
- 14.1 Release gates -> nothing blocks a release; the smoke gives numbers and a verdict only (an emulator with
  software rendering is not a trustworthy gate and retry-to-green is forbidden; gates are revisited after
  phases A-D and a real-GPU device)
- 14.3 Canonical rig -> emulators: phone API 35 (`google_apis_playstore` x86_64, the phase A baseline image
  family), Wear API 37; physical devices are spot checks outside the budgets; the missed-frame fraction (F-0003)
  is recorded as an emulator property (keeps the baseline valid, no re-run)
- 14.4 Big input -> synthetic M3U of 100,000 entries plus the real 19,404-channel catalog (S0007); no other
  canonical big input (the spec's proposal, about 5x the real catalog)
- 14.5 Finding promotion -> P0 and P1 become tickets at once (P0 at the head of the current release package, P1 in
  the same package by priority); P2 and P3 stay in the ledger and the owner reviews them at each phase exit
  (no P0/P1 exists today; F-0001 P2, F-0002 P3, F-0003 P3)
- 14.5 Ledger home -> committed `docs/audit/findings.jsonl` (the same no-remote reason as the tracked `PLAN/`,
  CLAUDE.md section 4.6; numbers only); run folders stay in `temp/`; section 12 amended above
- 14.6 Cadence -> the phase A smoke (cold start, catalog actions, 3 minutes idle) at every release sweep as a
  report; phases B-D re-run in full only when a ticket changes a measured surface (matches the package note in
  `PLAN/RELEASE_QUEUE.md`)
- 14.6 Retention -> after `Verified` keep the baseline report, the findings ledger and the latest run folder per
  phase; delete the rest (section 12 amended above)
- 14.6 Phase E personnel -> the owner personally carries out the manual RU and UK pass, with no third party; EN is
  covered by the rig's string audit and the owner (no personnel to appoint or brief)

**Resolved without asking**
- 14.7 `CHECK-VERDICT` adoption -> already adopted as a consumer: owner's decision of 2026-10-25 (S0035
  section 3.3 item 4), recorded in `docs/contracts/AUTOMATED-CHECKS.md` (`CHECK-VERDICT` 0.14, role "consumer,
  for the checks this repository writes itself") and in the `docs/contracts/README.md` table. The statement of
  section 0 that the product has no adoption row is stale. `tools/audit/` keeps its 0/1/2 verdict lines (the
  shape section 7 item 8 already requires); aligning them to 0.14 (exit code 3, the test verdict line with
  `run=N skipped=M`) is part of this ticket's closing check and is not claimed to conform today.

**Consequences for the work** (written for the implementation pass; nothing here is built or run by the quiz)
- App side: a debug-only receiver for the main-Looper ping under the phone `debug` source set, in the `S0025:`
  probe convention; no change to release builds or to ported files (handoff 6a unaffected).
- Rig side: `budgets.json` - set B-1, B-3, B-5a, B-7b to `promoted` with date and command; B-1 may then be re-judged
  on the probe's first-frame reading; the 100,000-entry M3U generator for SC-10 and SC-20; the ledger move to
  `docs/audit/findings.jsonl` (3 findings carried across).
- Release side: a phase A smoke step in `tools/release/pre-release-sweep.ps1`, report-only (RELEASE-class run; the
  emulator is needed, nothing here runs between releases per CLAUDE.md section 5a).
- Phases B-E: scene drivers and runs as listed under "Remaining development"; the phase E pass is the owner's.

### Queue pass 2026-11-08

**Built** - every consequence above except the optional B-1 re-judging (see below). Not built by Gradle and not run on
a device until the release (CLAUDE.md section 5a); the evidence of this pass is in "Last Audit".
- Budgets: `tools/audit/budgets.json` marks B-1, B-3, B-5a, B-7b `promoted` with the date, the baseline value and run,
  and the regenerating command (section 8); the fixture test of the real file asserts exactly these four.
- Ledger: `docs/audit/findings.jsonl` holds F-0001 (P2), F-0002 (P3), F-0003 (P3), each with the owner's decision of
  2026-11-08; the `temp/` copy is removed, so the ledger has one home.
- App: `phone/src/debug/java/com/sza/streamsplayer/debug/AuditPingProvider.kt`, declared in the debug manifest. It
  registers a DUMP-guarded receiver on a background thread; each ping posts one message to the main Looper and logs the
  queue wait on the device clock. Debug source set only: release builds and ported files are untouched (handoff 6a).
- Rig: `rig.py ping` (`auditrig/ping.py`) reads the pongs back and refuses a run with no pong or more than 10% lost;
  `rig.py big-m3u` (`auditrig/bigm3u.py`) writes the 100,000-entry playlist of 14.4 - 17,655,491 bytes, SHA-256
  `bd87cebcceedbb0b50099eb628fd18d9a41e61940e08e74f9c0d09448ee2eb26` on 2026-11-08, every address distinct and under
  the reserved `.invalid` domain, no `#EXT-X-` line.
- Release: `tools/release/pre-release-sweep.ps1 -AuditSerial <serial>` builds the store debug APK (unless
  `-SkipBuild`), installs it and runs `rig.py phase-a`, recording the result as the `AuditSmoke` report; without the
  serial the report reads `NOT RUN`. A report never changes the sweep's PASS/FAIL (14.1).
- `CHECK-VERDICT` 0.14: the two self-tests print `run=N skipped=M` test verdict lines (item A); `run-selftests.ps1`
  reads a step exit of 2 or 4 as could not verify and any other non-zero exit as FAIL (rule 2.14); the pointer
  `docs/contracts/AUTOMATED-CHECKS.md` names the alignment.

**Resolved without asking**
- The ping's log line carries the stable tag `SpAuditPing`, not the `S0025:` prefix the consequences named. The canon's
  probe invariant decides it: a ticket-id log line exists only while its ticket is in `BlockNeedUserTest`, and permanent
  logs never carry a ticket id; 14.6 makes the instrument permanent (the release smoke), and the harness deletes a
  ticket's probe lines when it leaves `BlockNeedUserTest`. The owner's choice itself - debug only, nothing in release,
  no counters - is unchanged.
- The playlist's addresses are distinct per entry: with one address per host and kind (about 5,000 distinct), an
  import that skips duplicate addresses would have met 5% of the 100,000 entries.
- B-1 is not re-judged on a ping reading: the ping measures the main-Looper queue, not the first frame, so it has no
  first-frame number to offer; B-1 stays judged on `am start -W` TotalTime (the consequence said "may").
- The ping's own footprint is the instrument self-noise of section 7 item 4: a debug build that carries it holds one
  more thread (its `HandlerThread`) and the file descriptors of one more Looper for the life of the process. Levels
  measured on such a build are not compared with the phase A build's levels; slopes and deviations (B-5a, B-5b) are
  unaffected by a constant offset.

**Owner question 14.8 (answered 2026-11-10)** - cleared `BlockQuestions`. Phase B-D scenes can only be written against a live
device (the phase A formats were all found live), and a run takes a debug build plus an emulator session of minutes
to overnight - between releases such a run happens only when the owner names it (TESTING_AND_QA section 2).
1. *When do the phase B-D rig sessions run?*
   - Answer: (a) The owner names one rig session per phase between releases (store debug build plus the
     `sp_audit_api35` emulator); the queue then writes that phase's drivers and runs it in one pass - the phases move
     as fast as the owner names them.
2. *What serves the HLS, DASH and RTSP streams of SC-08, SC-09, SC-17, SC-19 and SC-21?* Every stream must be local
   (section 2 item 4); this machine has no encoder and no RTSP server (checked 2026-11-08: no `ffmpeg`, `mediamtx` or
   GStreamer on PATH).
   - Answer: (a) Download an ffmpeg build and mediamtx into a gitignored tool cache under `temp/`,
     never committed and never shipped; ffmpeg encodes a test pattern into HLS and DASH segments served by the rig on
     loopback, mediamtx serves RTSP - the emulator reaches the host at `10.0.2.2`.

### Queue pass 2026-11-13 (phase B session)

The session the owner named on 2026-11-13 (decision 14.8.1): store debug build `2.61.1130.408` (built under the
`Build.Main` lock, 3 min 42 s, with `AuditPingProvider`), AVD `sp_audit_api35` on `emulator-5556`, one pass.

**Built**
- Tool cache (14.8.2): `rig.py tools` (`auditrig/toolcache.py`) downloads ffmpeg 9.0.2 (gyan.dev essentials, SHA-256
  `60f46726..`) and mediamtx 1.21.3 (SHA-256 `b2f5246b..`) into the gitignored `temp/S0025/tools/`, records each
  archive hash at the first download and refuses a changed archive later. Nothing is committed or shipped.
- Fixtures (`auditrig/streams.py`): on host loopback, reached by the emulator at `10.0.2.2` - an ICY radio paced at
  128 kbit/s with metadata every 16,000 bytes, live HLS and live DASH encoded by ffmpeg, RTSP served by mediamtx
  (interleaved TCP, the STREAM-BANK transport), a self-signed HTTPS copy, and the fault endpoints (dead host
  `192.0.2.1`, closed ports, 25 s slow first byte, a host that never answers, HTTP 404/403/500, a server outage on
  demand). Every request is logged to the run folder. A host-side self-test reads seven known values before a run.
- Driver (`auditrig/phase_b.py`, `rig.py phase-b`): SC-07, SC-08 (HLS, DASH), SC-09, SC-21, SC-19, SC-20 with SC-10 and
  SC-06 P2-P4, inside the disposable-profile session. New instruments: the message listener (`uiautomator events` -
  a Toast never enters the UI tree; an opened dialog counts as a message too), the focused window, the fixture
  request log, and the release wait after a stop (codec, wake lock, audio focus, foreground service).
- Rig fixes found by the run: a setting written twice recorded an intermediate value as its "old" one, so a correct
  rollback could not prove itself (`Session.put_setting` keeps the first value; fixture test added); a UI dump right
  after the message listener released UiAutomation could come back empty (`Ui.dump` retries); the DASH muxer wrote its
  segments into its working directory (stray `*.m4s` files in the repo root and `tools/audit/` from this pass were
  deleted; ffmpeg now runs in the DASH folder and the self-test fetches the init segments); the URL-leak check counted
  redacted lines and the rig's own UI dumps (corrected, and re-read against the same log: no whole URL in the app's
  log).

**Run** `temp/S0025/b-20261113-2` (command in its `environment.json`): `phase-b: FAIL (13 scene check(s) failed or over
budget)` - the findings below, not instrument failures. The in-run rollback answered `COULD NOT VERIFY` on the
rig's own recording defect above; after the fix `rig.py session rollback --run-dir temp/S0025/b-20261113-2` restored
the snapshot again and proved it (`session: PASS (rollback proved: 3 recorded change(s) gone)`); the manifest as
recorded is kept beside it. `b-20261113-1` is an invalid run: stopped by this pass after a driver error in SC-07, its
folder kept with the log (search on the real catalog 65,344 ms there).

**Numbers** (one run, the emulator of decision 14.3)

| Scene | What | Value |
| --- | --- | --- |
| SC-06 / SC-20 | search typed to the matching list, real catalog 19,550 | 67,625 ms (B-2 over; F-0004, S0070) |
| SC-20 | narrow a 15-row result to 1 row, catalog 119,550 | 4,360 ms (B-2 over) |
| SC-20 | scripted scroll of the 119,550-row catalog, longest main-thread time | 28 ms (B-3 within) |
| SC-10 | import of the 100,000-entry playlist to its message | 37,953 ms, "Imported 100000 channel(s)", PSS 148-223 MiB |
| SC-10 | fixture playlist (15 entries) to its message | 765 ms |
| SC-07 | tap to the radio bar / to the ICY title / pause / resume / switch | 3,657 / 1,984 / 2,031 / 2,875 / 2,797 ms (UI-tree instrument) |
| SC-07, SC-08, SC-09 | main-Looper queue wait while playing (ping, 20 pongs) | max 1-5 ms |
| SC-07 / SC-08 HLS / SC-09 | stop to codec, wake lock, audio focus, foreground service released (B-6) | 250 / 984 / 187 ms |
| SC-08 HLS | open to decoder / live-edge lag | 688 ms / 1 segment |
| SC-09 | open to decoder over a TCP-only server | 2,360 ms after 10 UDP SETUPs answered 461 (F-0010) |
| SC-07, SC-08, SC-09 | PSS while playing, p5-p95 | 140-151 MiB (B-4a hypothesis 60-75; F-0011) |
| SC-21 | radio fault to its message, 8 faults | 15,047-15,078 ms "No signal" (B-10 over; F-0006) |
| SC-21 | video HLS 404 to its message | 3,234 ms, dialog "Stream unavailable .. is not responding" |
| SC-21 | RTSP closed port, no network | no message within 60 s (F-0007) |
| SC-21 | next channel after any fault, no restart | 2,812-2,890 ms, every case |
| SC-19 | 20-tap switch storm | 1 stream open, 1 playing session; last tap lost once (F-0012) |
| SC-19 | log volume over the storm (B-8) | 673 KB/min device-wide, within 2 MiB/min |
| SC-19 | three 5 s network flaps | a new stream 7,032 ms after the last flap, one player |

**Assumed (unattended, 2026-11-13)** - each reversible inside the ticket; owner may overrule.
- The fixture channels enter the app through its own "Import playlist" from `10.0.2.2`, not through a debug seed
  receiver - decision 14.2 allows the ping only, and the import is itself the SC-10 scene. Alternative: a debug-only
  seed receiver beside `HelpDemoSeedReceiver`.
- The RTSP fixture accepts interleaved TCP only, the transport STREAM-BANK document 06 names. Alternative: UDP and TCP,
  which would hide F-0010.
- The user's wait for an error is read from accessibility events (Toast or dialog), timed from the tap on the host;
  floor one input round trip (~300 ms). Alternative: screenshots, which cost a second per frame.
- During video the UI thread draws nothing (the picture is a SurfaceView), so the frame clock has no subject; the
  main-Looper ping is the P1 main-thread number there. Alternative: none outside the process.
- The proposed severities of F-0005..F-0012 are the pass's; the owner confirms or changes them at the phase exit.

### Questions (v1, 2026-11-13)

- Q: How are the phase B P2 and P3 findings (F-0005..F-0012) handled at the phase exit?
  - File F-0007, F-0008, F-0009 and F-0010 as Draft tickets now; F-0006 goes to a STREAM-BANK review; the P3s stay in the ledger (recommended) - the product defects get owners, the retry window is the contract's to change
  - Keep every finding in the ledger until phases C and D are done - one review, the defects wait
  - File every P2 and P3 as its own Draft ticket - nothing waits, eight tickets at once
- Q: What happens to budget B-4a (sustained playback PSS 60-75 MiB)?
  - Adjust it to the measured band with the real catalog loaded, 135-160 MiB, still a hypothesis until the 4 h run of phase D (recommended) - the row then judges growth instead of failing every run
  - Keep 60-75 MiB - every playback run stays over until memory use falls
  - Drop the level band and keep only the slope row B-4b
- Q: Which rig session runs next?
  - Phase C - broadcast and exchange, the next by risk (section 11) (recommended)
  - Phase D - lifecycle and the overnight leak runs
  - None before the next release - the matrix stays as it is

## Last Audit

**Date:** 26-11-13 06:20 | **Mode:** live session + static | **Outcome:** Partial
**Counts:** PASS 11 | WARN 3 | FAIL 0 | MANUAL 1 | UNCHECKABLE 0 | EXEMPT 0 | RELEASE 2

Scope: the phase B session the owner named on 2026-11-13 (section 14, "Queue pass 2026-11-13") against decision
14.8.1/14.8.2 and the definition of done (section 13). Every check below ran in this pass. No compiled source was
edited (Python rig, the ticket, the ledger, the rig README), so the unattended compile gate does not apply; this repo
declares no `commands.compileCheck`.

- PASS - tool cache: `py -3 tools/audit/rig.py tools` exit 0, `tools: PASS (ffmpeg 9.0.2, mediamtx 1.21.3)` (5.1 s, the
  one download included).
- PASS - fixtures before trust: host self-test of seven known values (playlist 15 entries, ICY metaint 16000 with its
  title, 404 endpoint, live HLS segments, dynamic DASH with its init segments served, RTSP h264+aac via ffprobe, closed
  wrong ports) - every check passed in the session log and again after the DASH fix.
- PASS - store debug APK built for the named session: `:phone:assembleStoreDebug` under `Build.Main`, `BUILD SUCCESSFUL in
  3m 42s` (the lock left by this pass's own dead script process was removed after a liveness check).
- PASS - the session: `py -3 tools/audit/rig.py phase-b --serial emulator-5556 --apk phone/build/outputs/apk/store/debug/phone-store-debug.apk --run-id b-20261113-2`
  exit 1, `phase-b: FAIL (13 scene check(s) failed or over budget; run folder temp/S0025/b-20261113-2)` - instrument
  self-test 11 live checks, backup manifest, control run 120 s, the scenes, verdicts in `verdicts.json`; the FAILs are the
  findings (expected: a verdict per scene | actual: 24 scene verdicts).
- PASS - rollback proved: in-run `COULD NOT VERIFY` on the rig's recording defect, fixed, then
  `py -3 tools/audit/rig.py session rollback --serial emulator-5556 --package com.sza.streamsplayer --run-dir temp/S0025/b-20261113-2`
  exit 0, `session: PASS (rollback proved: 3 recorded change(s) gone)` (3.2 s).
- PASS - first live use of the ping: `rig.py ping` exit 0, `10 of 10 pongs, queue p50 0 ms, p95 5 ms, max 5 ms`.
- PASS - fixture tests of the changed rig code: `py -3 -m unittest auditrig.tests.test_phase_b auditrig.tests.test_session auditrig.tests.test_cli_codes`
  exit 0, `Ran 31 tests in 1.079s OK` (1.2 s wall).
- PASS - coverage gate on the real pair after the matrix edit: `pwsh -NoProfile -File tools/audit/coverage_check.ps1` exit 0,
  `coverage: PASS (12 capabilities, 110 cells)` (0.37 s).
- PASS - findings filed: `docs/audit/findings.jsonl` 12 lines (expected 3 + 9 | actual 12); the P1 is ticket S0070
  (`spec_catalog/capture-draft` exit 0, `PLAN/S0070_catalog-search-stale-list.md`), per decision 14.5.
- PASS - probe invariant: `S0025:` in `phone|wear|watchface|exchange-core/src` - 0 hits.
- PASS - filesystem: the stray DASH segments ffmpeg wrote into the repo root and `tools/audit/` during this pass were
  deleted (`find . -maxdepth 3 -name "*-stream[01]*.m4s"` outside the fixture folder: 0); no fixture process left
  (`tasklist` ffmpeg/mediamtx: 0); the emulator this pass started is stopped. Doc gates on the edited README:
  `assert-doc-links: PASS (documents=47 pages=3 links=195)`, `assert-doc-style: PASS (documents=47 sources=13)`.
- WARN - definition of done, matrix: SC-21 P3/P4 stay `open (B)`; the DASH half of SC-08 is invalid in this run (fixture
  defect, fixed); phases C and D are open.
- WARN - definition of done, baseline report: not written; it needs phases C and D.
- WARN - one-run numbers: every phase B value is one session on the emulator of decision 14.3; the UI-tree times carry
  the ~600 ms floor of their instrument.
- RELEASE - the full rig suite with its live checks and the coverage self-test:
  `pwsh -NoProfile -File tools/audit/run-selftests.ps1 -Serial emulator-5556` (inside the sweep, `AuditSelfTests`).
- RELEASE - the smoke step end to end: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1 -AuditSerial emulator-5556`.

### Action items
1. Phase B exit review, the B-4a band and the next session - section "Questions (v1, 2026-11-13)".
2. A phase B re-run for SC-21 P3/P4 and the SC-08 DASH half, with the session the owner names next.
3. Phases C and D drivers, runs and matrix cells, as listed under "Remaining development".

### Manual / on-target
- [ ] Phase E (owner): the TalkBack walk, the RU and UK string pass, an undo path per switch, gamepad and TV remote per
  `INPUT-PARITY` - passing means every P5 cell of section 5 answered `measured` or `n/a` with a reason.
