"""Phase A driver (S0025 section 11): rig self-test, disposable-profile backup, control run, the baseline scenes,
a proven rollback, and a verdict per scene. One run folder under temp/S0025/<run-id>/ keeps everything, including an
invalid run with its reason.

Scenes of this driver (each names the matrix cell and budget rows it feeds):
  control     the identical sampler on an idle system process (SystemUI) with the app force-stopped: the system's
              own churn and the probe's self-noise (section 7 item 4)
  SC-01 P1    N cold launches (B-1): `am start -W` TotalTime is the judged number; the UI-tree time to the first
              catalog row is recorded beside it with its instrument floor. The profile already holds the imported
              catalog, so this is a cold start with a full catalog, not the first start after install.
  SC-06 P1    injected tap on a catalog filter chip to the visible change (B-2); the frame clock over a scripted scroll
              of the catalog (B-3)
  SC-22 P3/P4 the app left alone on the catalog screen, sampled (mode idle: B-5, B-7b)
The sampler windows are minutes, not the hours the slope budgets name: a slope the window cannot decide is reported
undecided (exit 2), never guessed.
"""
import hashlib
import json
import os
import re
import subprocess
import time

from . import evaluate as evaluate_module
from . import framestats, instruments, latency, session
from . import sampler as sampler_module
from .adb import Adb, find_adb
from .verdict import COULD_NOT_VERIFY, FAIL, PASS, CouldNotVerify

PACKAGE = 'com.sza.streamsplayer'
ACTIVITY = f'{PACKAGE}/.MainActivity'
CONTROL_PACKAGE = instruments.REFERENCE_PACKAGE
CATALOG_ROW = 'tvChannelTitle'


def _write_json(path, document):
    with open(path, 'w', encoding='utf-8') as handle:
        json.dump(document, handle, indent=2, sort_keys=True)


def _sha256(path):
    return session.digest_file(path)


def _git_head(repo):
    try:
        return subprocess.run(['git', '-C', repo, 'rev-parse', '--short', 'HEAD'], capture_output=True, text=True, timeout=20).stdout.strip()
    except Exception:  # noqa: BLE001
        return 'unknown'


def _environment(adb, apk, repo, command):
    props = {key: adb.shell(f'getprop {name}').strip() for key, name in
             (('sdk', 'ro.build.version.sdk'), ('model', 'ro.product.model'), ('abi', 'ro.product.cpu.abi'), ('fingerprint', 'ro.build.fingerprint'))}
    return {'date': time.strftime('%Y-%m-%d %H:%M:%S'), 'command': command, 'git_head': _git_head(repo), 'device': props,
            'apk': {'path': os.path.relpath(apk, repo), 'bytes': os.path.getsize(apk), 'sha256': _sha256(apk),
                    'modified': time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(os.path.getmtime(apk)))}}


def _sample(adb, run_dir, name, package, seconds, access):
    tail = sampler_module.LogTail(adb, os.path.join(run_dir, f'{name}.logcat.txt'))
    tail.start()
    try:
        rows, summary = sampler_module.collect(sampler_module.Sampler(adb, package, access, tail), seconds, 5.0,
                                               os.path.join(run_dir, f'{name}.csv'))
    finally:
        tail.stop()
    _write_json(os.path.join(run_dir, f'{name}.summary.json'), summary)
    return summary


def _judge_scene(label, rows, run_dir, mode, warmup, **paths):
    inputs = evaluate_module.load_inputs(**paths)
    try:
        results = evaluate_module.evaluate(rows, inputs, mode, warmup)
        lines = []
        for row, state, detail in results:
            lines.append(f'{row["id"]:5} {state.upper():9} {detail}  [{row.get("status", "hypothesis")}] {row["title"]}')
        code, detail = evaluate_module.judge(results)
    except CouldNotVerify as error:
        lines, code, detail = [f'invalid run: {error}'], COULD_NOT_VERIFY, str(error)
    return {'scene': label, 'code': code, 'detail': detail, 'rows': lines}


def run_phase_a(serial, apk, run_id, repo, control_s=120, idle_s=180, cold_starts=5, tap_repeats=10, command=''):
    adb = Adb(serial, find_adb(repo)).require_device()
    run_dir = os.path.join(repo, 'temp', 'S0025', run_id)
    os.makedirs(run_dir, exist_ok=True)
    rows = [r for r in evaluate_module.load_budgets(os.path.join(repo, 'tools', 'audit', 'budgets.json'))]
    by_id = {r['id']: r for r in rows}

    # --- instrument self-test first: an impossible reading stops the session before any scene runs
    passed = instruments.live_selftest(adb)
    print(f'instrument self-test: {len(passed)} live checks passed')
    _write_json(os.path.join(run_dir, 'environment.json'), _environment(adb, apk, repo, command))

    verdicts = []

    def steps(current):
        # The screen must stay on for the whole session: a sleeping screen hides the app's window from the UI tree
        # (the first two runs of this driver died on exactly that). The change is recorded, so rollback proves it undone.
        current.put_setting('system', 'screen_off_timeout', '2147483647')  # not plugged in: stay_on_while_plugged_in would not apply
        adb.shell('input keyevent KEYCODE_WAKEUP')
        adb.shell('wm dismiss-keyguard')
        # control: app force-stopped, sampler on an idle system process
        adb.shell(f'am force-stop {PACKAGE}')
        time.sleep(2)
        print(f'control run: {control_s} s on {CONTROL_PACKAGE}')
        _sample(adb, run_dir, 'control', CONTROL_PACKAGE, control_s, 'shell')

        ui = latency.Ui(adb, PACKAGE)
        # SC-01 P1 / B-1, two instruments over the same launches: `am start -W` TotalTime (device-clock timestamps,
        # launch start to first frame drawn - the judged number, no adb floor) and the UI-tree time to the first
        # catalog row (carries the instrument floor; recorded as evidence, it cannot resolve a 2,000 ms budget).
        adb.shell(f'am start -W -n {ACTIVITY}')  # calibration reads the app's own tree, so the app must be on screen
        deadline = time.monotonic() + 30
        while not latency.has_node(ui.dump(), PACKAGE, CATALOG_ROW):
            if time.monotonic() > deadline:
                raise CouldNotVerify('the catalog did not show before the instrument calibration')
        floor = latency.calibrate(ui)
        cold, am_total = [], []
        for _ in range(cold_starts):
            adb.shell(f'am force-stop {PACKAGE}')
            time.sleep(2)
            started = time.monotonic()
            out = adb.shell(f'am start -W -n {ACTIVITY}')
            matched = re.search(r'TotalTime: (\d+)', out)
            if not matched or 'LaunchState: COLD' not in out:
                raise CouldNotVerify(f'a launch that was meant to be cold was not: {out.strip()[:160]!r}')
            am_total.append(int(matched.group(1)))
            while True:
                if latency.has_node(ui.dump(), PACKAGE, CATALOG_ROW):
                    cold.append((time.monotonic() - started) * 1000.0)
                    break
                if time.monotonic() - started > 30:
                    raise CouldNotVerify('no catalog row within 30 s of a cold launch')
        print(f'cold start: am TotalTime {am_total} ms; first catalog row {[round(c) for c in cold]} ms')
        events = os.path.join(run_dir, 'SC-01-events.json')
        _write_json(events, {'am_total_time_ms': am_total, 'cold_start_ms': [round(c, 1) for c in cold],
                             'cold_start_floor_ms': round(floor['floor_ms'], 1), 'cold_start_jitter_ms': round(floor['jitter_ms'], 1)})
        verdicts.append(_judge_scene('SC-01 P1 cold start', [by_id['B-1']], run_dir, 'playing', 0, events=events))

        # SC-06 P1 / B-2: filter chip tap to visible change; B-3: scripted scroll under the frame clock
        xml = ui.dump()
        radio = latency.node_center(xml, PACKAGE, text='Radio')
        everything = latency.node_center(xml, PACKAGE, text='All')
        result = latency.measure(ui, f'input tap {radio[0]} {radio[1]}', f'input tap {everything[0]} {everything[1]}', tap_repeats)
        lat_path = os.path.join(run_dir, 'SC-06-latency.json')
        _write_json(lat_path, result)
        print(f'filter chip tap: p50 {result["p50_ms"]} ms, p95 {result["p95_ms"]} ms, floor {result["floor_ms"]} ms')
        collector = framestats.FrameCollector(adb, PACKAGE)
        collector.reset()
        for step in range(8):
            up = step % 2 == 0
            adb.shell('input swipe 540 1700 540 700 250' if up else 'input swipe 540 700 540 1700 250')
            collector.poll()
        frames = collector.metrics()
        frames_path = os.path.join(run_dir, 'SC-06-frames.json')
        _write_json(frames_path, frames)
        print(f'scripted scroll: {frames}')
        verdicts.append(_judge_scene('SC-06 P1 catalog actions', [by_id['B-2'], by_id['B-3']], run_dir, 'playing', 0,
                                     latency=lat_path, frames=frames_path))

        # SC-22 / P3, P4: the app left alone on the catalog screen
        adb.shell(f'am force-stop {PACKAGE}')
        time.sleep(1)
        adb.shell(f'am start -W -n {ACTIVITY}')
        time.sleep(5)
        print(f'idle scene: {idle_s} s on {PACKAGE}')
        summary = _sample(adb, run_dir, 'SC-22-idle', PACKAGE, idle_s, 'auto')
        for metric, reason in summary['unavailable'].items():
            print(f'unavailable  {metric}: {reason}')
        idle_rows = [by_id[i] for i in ('B-5a', 'B-5b', 'B-7b')]
        verdicts.append(_judge_scene('SC-22 P3/P4 idle', idle_rows, run_dir, 'idle', 30,
                                     sampler_csv=os.path.join(run_dir, 'SC-22-idle.csv'), control_csv=os.path.join(run_dir, 'control.csv')))
        adb.shell(f'am force-stop {PACKAGE}')
        return PASS, 'scenes ran'

    # --- the whole session runs inside the disposable profile; rollback proves itself or the session fails
    try:
        device = session.AdbDevice(adb)
        session.run_session(device, run_dir, PACKAGE, steps)
        rollback = 'rollback proved: snapshot restored, package list and settings digests equal the backup'
    except CouldNotVerify as error:
        verdicts.append({'scene': 'session', 'code': COULD_NOT_VERIFY, 'detail': str(error), 'rows': []})
        rollback = f'rollback or session did not verify: {error}'
    _write_json(os.path.join(run_dir, 'verdicts.json'), {'verdicts': verdicts, 'rollback': rollback})
    for entry in verdicts:
        print(f'--- {entry["scene"]}')
        for line in entry['rows']:
            print(f'    {line}')
        print(f'    => exit {entry["code"]}: {entry["detail"]}')
    print(rollback)
    codes = [v['code'] for v in verdicts]
    if FAIL in codes:
        return FAIL, f'{codes.count(FAIL)} scene(s) over budget; run folder temp/S0025/{run_id}'
    if COULD_NOT_VERIFY in codes:
        return COULD_NOT_VERIFY, f'{codes.count(COULD_NOT_VERIFY)} scene(s) undecided or invalid; run folder temp/S0025/{run_id}'
    return PASS, f'{len(codes)} scene(s) within budget; run folder temp/S0025/{run_id}'
