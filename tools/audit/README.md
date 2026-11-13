# tools/audit - the S0025 dynamic audit rig

Host-side instruments that read a device from outside the app process (S0025 sections 6-8). Nothing here
changes product behaviour, and nothing runs inside a release build. Run folders live under `temp/S0025/<run-id>/`
(gitignored); only this tooling, the matrix in the ticket, the journal row and the findings ledger
`docs/audit/findings.jsonl` (decision 14.5, 2026-11-08) are committed.

## Release self-tests

```
pwsh -NoProfile -File tools/audit/run-selftests.ps1 [-Serial emulator-5556]
```

The pre-release sweep runs these checks. Between releases their duration is unmeasured, so record this command as a
RELEASE item rather than running it. Three steps, all run, aggregated FAIL > COULD NOT VERIFY > PASS: the coverage gate's fixtures, the rig's fixture
suite (plus live synthetic-subject checks with `-Serial`), and the coverage gate on the real pair. A gate or an
instrument that has not run since its last change is itself unverified.

## Exit codes - CHECK-VERDICT

Every script ends with one verdict line, last on stdout: `0` within budget or collected, `1` inspected and
defective, `2` could not verify and never a pass. A script documents only the codes it can reach; none of them raises
an advisory (`3`) or waits on a lock (`4`). The two self-tests print the test verdict line of `CHECK-VERDICT` 0.14 item A
(`run=N skipped=M`, further `key=value` tokens after them).

| Script | Codes | Verdict line |
| --- | --- | --- |
| `coverage_check.ps1` | 0 1 2 | `coverage: PASS (<n> capabilities, <m> cells)` / `FAIL (<k> gap(s): ..)` / `COULD NOT VERIFY (<what>)` |
| `test-coverage-check.ps1` | 0 1 2 | `coverage-selftest: PASS (run=<n> skipped=0)` / `FAIL (run=<n> skipped=0 failed=<k>)` |
| `rig.py selftest` | 0 1 2 | `rig-selftest: PASS (run=<n> skipped=<m> fixtures=<f> live=<l>)` / `COULD NOT VERIFY (run=<n> skipped=<m>, only fixtures: ..)` |
| `run-selftests.ps1` | 0 1 2 | `audit-selftests: PASS (<n> steps, ..)` - an aggregator: each step's line quoted first, precedence FAIL > COULD NOT VERIFY > PASS |
| `rig.py sample`, `frames`, `latency`, `ping`, `session` | 0 2 | `<script>: PASS (<counts>)` - a collector collects, it does not judge |
| `rig.py big-m3u` | 0 2 | `big-m3u: PASS (<n> entries, <bytes> bytes, sha256 <hex>)` |
| `rig.py evaluate` | 0 1 2 | `scene: PASS (<n> budget row(s) within budget, <k> still hypothesis)` / `FAIL (<rows over budget>)` / `COULD NOT VERIFY (..)` |
| `rig.py phase-a` | 0 1 2 | `phase-a: ..` - the aggregate over its scenes, precedence FAIL > COULD NOT VERIFY > PASS |
| `rig.py phase-b` | 0 1 2 | `phase-b: ..` - the same aggregate over the phase B scenes and outcome checks |
| `rig.py tools` | 0 2 | `tools: PASS (ffmpeg <v>, mediamtx <v>)` |

`tests/test_cli_codes.py` forces every documented code of the entry point through a child process.

## Coverage gate (section 6)

`coverage_check.ps1` reads CLAUDE.md section 1, the scene catalog and the matrix of the S0025 spec, and
`coverage_keywords.json`. Cell grammar: `open (A..E)` (the owning phase), `n/a (<tag>)` (tags declared in the keyword
table), `measured (YYYY-MM-DD; <run-id>)` (a date and the run folder whose `environment.json` holds the command).
The keyword table pins the text of CLAUDE.md section 1 it was reviewed against: when section 1 changes the gate
fails until the table is re-reviewed and the pin regenerated with `coverage_check.ps1 -PrintSection1Hash`.

## Rig (section 7)

`python tools/audit/rig.py <command>` (Python 3.10+, standard library only).

| Command | What it does |
| --- | --- |
| `sample` | resource counters of one package every 5 s into one CSV (`elapsed_s,pss_kib,java_heap_kib,native_heap_kib,threads,fds,alarms,codecs,wakelocks,services,fg_services,cache_kib,files_kib,log_bytes,ok`) |
| `frames` | `dumpsys gfxinfo framestats` polled across a window and merged by vsync: longest main-thread time, longest frame, missed deadlines |
| `latency` | injected input to the first visible UI-tree change, with the instrument's own floor and jitter calibrated first |
| `ping` | main-Looper queue wait of a debug build on the device clock (decision 14.2): `am broadcast` pings, pongs read back from logcat |
| `big-m3u` | the synthetic oversized playlist of SC-10 and SC-20 (decision 14.4): 100,000 entries by default, deterministic, every address under `.invalid` |
| `evaluate` | judge sampler / frames / latency / events inputs against `budgets.json` rows, against the control run |
| `session` | `backup` (verified manifest + fresh snapshot `sp-audit-clean`), `check`, `rollback` (proves itself) |
| `phase-a` | the phase A driver: self-test, backup, control run, baseline scenes, proven rollback, verdict per scene |
| `tools` | the stream-fixture tool cache (decision 14.8.2): download or verify ffmpeg and mediamtx in the gitignored `temp/S0025/tools/` |
| `phase-b` | the phase B driver: the same session frame around the media scenes SC-07, SC-08, SC-09, SC-21, SC-19, SC-20 (with SC-10 and SC-06 P2-P4), played from loopback fixtures; `--only` picks scenes |

## Stream fixtures (phases B-D)

`auditrig/streams.py` serves everything a media scene plays from this host; the emulator reaches it at `10.0.2.2`, so no
scene touches an external network. HTTP on 8770 (`fixtures.m3u`, the big, malformed, oversized and long-name playlists,
an ICY radio paced at its bitrate, live HLS and DASH written by ffmpeg, the fault endpoints `/status/<code>/..`,
`/slow/..`, `/hang/..` and an on-demand outage), the same tree over a self-signed HTTPS on 8771 (certificate generated
per run under the run folder), RTSP on 8554 from mediamtx (interleaved TCP only). Ports 8799 and 8555 stay closed for
the wrong-port faults, `192.0.2.1` is the dead host. Every request is logged to `<run>/fixture-requests.jsonl`, and a
host-side self-test reads seven known values before any scene trusts the fixtures. ffmpeg runs in the folder it writes:
the DASH muxer writes its segments relative to its working directory.

Phase B instruments besides phase A's: the message listener (`uiautomator events`: a Toast never enters the UI tree,
its accessibility event carries the text; an opened dialog counts too - no tree is dumped while it listens), the focused
window (`dumpsys window`), the fixture request log, and the release wait after a stop (codec, wake lock, audio focus,
foreground service). During video the UI thread draws nothing (SurfaceView), so the main-Looper ping is the P1
main-thread number there.

Choices where the spec left room, each reversible by the owner:

- **B-1** is judged on `am start -W` TotalTime (launch start to first frame drawn, device-clock timestamps, so no
  adb floor bracket). The spec's "first catalog frame" wording would need the UI-tree instrument, whose floor
  (~2 s of adb round trip plus one tree read) cannot resolve a 2,000 ms budget - that number stays recorded beside
  the judged one in every `SC-01-events.json` (`cold_start_ms` + `cold_start_floor_ms`). The owner allowed one debug
  probe for this (decision 14.2, 2026-11-08): the main-Looper ping below.
- **Promoted rows** (decision 14.1, 2026-11-08): B-1, B-3, B-5a and B-7b carry `status: promoted`, the promotion date,
  the phase A baseline they were promoted on and the one command that regenerates it; every other row stays a
  hypothesis. No row is a release gate: the pre-release sweep runs the phase A smoke (`-AuditSerial`) as a report.
- **Main-Looper ping** (decision 14.2): a debug build carries `AuditPingProvider` (`phone/src/debug`), which registers a
  DUMP-guarded receiver on a background thread; each ping posts one message to the main Looper and logs
  `SpAuditPing: pong run=<nonce> seq=<n> queue_ms=<ms> uptime_ms=<uptime>`, both ends stamped on the device clock, so the
  reading has no adb floor. The line carries a stable tag rather than the `S0025:` probe prefix: the instrument is
  permanent (the release smoke uses it) and a ticket-id line exists only while its ticket waits for a manual test.
  A release build has no receiver; no pong reads as could not verify, never as a pass.
- **Frame rows out of timeline order** (IntendedVsync <= HandleInputStart <= SyncQueued <= FrameCompleted violated -
  seen live on 2026-10-24 on the API 35 image as one row after a `gfxinfo reset`) are dropped and counted in
  `dropped_frames`; more than 10% dropped is COULD NOT VERIFY, mirroring the sampler's lost-sample limit.
- **Slope bands** are OLS with the standard error widened for lag-1 autocorrelation (a counter's samples are not
  independent). A growth finding needs the lower bound above zero, above the budget and above the control band.
- **Blank-run overlap** (section 7 item 5) invalidates a claim of *growth*, never a claim of *bounded growth*: a
  band entirely under the budget passes whatever the control run did, because a moving control can only widen what
  the app is blamed for. Read literally, a flat app overlaps a flat control and could never pass; if the owner wants
  the literal reading, `stats.judge_growth` is the one place to change.
- **Log volume** is the size of a streaming `adb logcat` capture, not `logcat -d`: the buffer is a ring and its size is
  not monotonic over hours.
- **Private counters** (fds, cache and store sizes) need `run-as` on a debuggable build, or root; without either they are
  left empty and named in the summary, and a metric with no control band cannot become a leak finding.
- **Control run**: the same sampler on SystemUI with the app force-stopped. The second control of section 7 item 4 (app
  installed, never started) needs a profile on which the app was never launched and is not produced by `phase-a`.
- **UI-tree reads** go through `adb exec-out uiautomator dump /dev/stdout`; `/dev/tty` under `adb shell` delivers nothing
  on API 35. The tree is filtered to the app's package so the status-bar clock cannot make the screen look changed.

Formats were verified on an API 35 `google_apis_playstore` emulator on 2026-10-23; the framestats clock columns were
re-verified live on 2026-10-24 (the corrupt-reset-row rule above came out of that capture). Not yet seen with a live
subject and re-verified by the first scene that produces them: the `media.resource_manager` block of a process holding
a codec, and `isForeground=true` in an activity-services record of a foreground service.

## Disposable profile (section 7 item 7)

One emulator family for the audit: AVD `sp_audit_api35` (Pixel 9 skin, API 35 `google_apis_playstore` x86_64), booted
headless on port 5556. A session backs up the package list, the three settings dumps, the account digest and both
application data directories into `<run>/backup/` with a manifest that is re-hashed before anything runs, saves the
snapshot `sp-audit-clean`, and rolls back in the end. Every device change goes through `Session.install`,
`put_setting` or `push`, which records it in `recovery-manifest.json`; the rollback restores the snapshot, compares
package-list and settings digests with the backup, and checks every recorded change is gone. A rollback that cannot
prove itself fails the session with exit 2 and names what did not restore. `fakes.FakeDevice` forces each of those
outcomes in the fixture suite.
