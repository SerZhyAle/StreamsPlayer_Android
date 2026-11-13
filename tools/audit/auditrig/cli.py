"""Command line of the rig: python tools/audit/rig.py <command>. Every command ends with one CHECK-VERDICT line."""
import argparse
import contextlib
import io
import json
import os
import sys
import unittest

from . import evaluate as evaluate_module
from . import bigm3u, framestats, instruments, latency, ping, session, toolcache
from . import sampler as sampler_module
from .adb import Adb, find_adb
from .verdict import CouldNotVerify, COULD_NOT_VERIFY, FAIL, PASS, run

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
AUDIT = os.path.join(REPO, 'tools', 'audit')


def _adb(args):
    return Adb(args.serial, find_adb(REPO)).require_device()


def _write_json(path, document):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as handle:
        json.dump(document, handle, indent=2, sort_keys=True)


def cmd_selftest(args):
    """Fixture suite always; the live synthetic-subject checks when a serial is given. The verdict line is a test
    verdict line (CHECK-VERDICT 0.14 item A): run counts every executed fixture test and live check, skipped the
    fixture tests that were selected and did not execute."""
    stream = io.StringIO()
    suite = unittest.defaultTestLoader.discover(os.path.join(AUDIT, 'auditrig', 'tests'), top_level_dir=AUDIT)
    with contextlib.redirect_stdout(io.StringIO()):  # the fixtures print the lines a scene verdict would print
        result = unittest.TextTestRunner(stream=stream, verbosity=0).run(suite)
    skipped = len(result.skipped)
    run_count = result.testsRun - skipped
    if result.testsRun == 0:
        raise CouldNotVerify('run=0 skipped=0, the fixture suite found no tests')
    for test, reason in result.skipped:
        print(f'  skipped  {test.id()}: {reason}')
    if not result.wasSuccessful():
        print(stream.getvalue())
        return FAIL, f'run={run_count} skipped={skipped} failed={len(result.failures) + len(result.errors)}'
    if not args.serial:
        print('  live checks not run (no --serial)')
        return PASS, f'run={run_count} skipped={skipped} fixtures={run_count} live=0'
    try:
        passed = instruments.live_selftest(_adb(args))
    except CouldNotVerify as error:
        raise CouldNotVerify(f'run={run_count} skipped={skipped}, only fixtures: {error}') from error
    for name in passed:
        print(f'  live ok  {name}')
    return PASS, f'run={run_count + len(passed)} skipped={skipped} fixtures={run_count} live={len(passed)}'


def cmd_sample(args):
    adb = _adb(args)
    os.makedirs(args.out_dir, exist_ok=True)
    tail = sampler_module.LogTail(adb, os.path.join(args.out_dir, f'{args.name}.logcat.txt'))
    tail.start()
    try:
        rows, summary = sampler_module.collect(sampler_module.Sampler(adb, args.package, args.access, tail),
                                               args.duration, args.interval, os.path.join(args.out_dir, f'{args.name}.csv'))
    finally:
        tail.stop()
    _write_json(os.path.join(args.out_dir, f'{args.name}.summary.json'), summary)
    for metric, reason in summary['unavailable'].items():
        print(f'unavailable  {metric}: {reason}')
    return PASS, f'{summary["samples"]} samples, {summary["lost"]} lost, access {summary["access"]}'


def cmd_frames(args):
    collector = framestats.FrameCollector(_adb(args), args.package)
    if args.reset:
        collector.reset()
    metrics = collector.run_window(args.duration, args.poll)
    _write_json(args.out, metrics)
    return PASS, f'{metrics["frames"]} frames, longest main thread {metrics["longest_main_thread_ms"]} ms, {metrics["missed_deadlines"]} missed'


def cmd_latency(args):
    ui = latency.Ui(_adb(args), args.package)
    result = latency.measure(ui, args.command, args.restore, args.repeats)
    _write_json(args.out, result)
    return PASS, f'p50 {result["p50_ms"]} ms, p95 {result["p95_ms"]} ms, floor {result["floor_ms"]} ms over {result["repeats"]} repeats'


def cmd_ping(args):
    result = ping.ping(_adb(args), args.package, args.count, args.interval)
    _write_json(args.out, result)
    return PASS, f'{result["received"]} of {result["sent"]} pongs, queue p50 {result["p50_ms"]:g} ms, p95 {result["p95_ms"]:g} ms, max {result["max_ms"]:g} ms'


def cmd_big_m3u(args):
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    result = bigm3u.write(args.out, args.entries)
    return PASS, f'{result["entries"]} entries, {result["bytes"]} bytes, sha256 {result["sha256"]}'


def cmd_tools(args):
    manifest = toolcache.fetch(REPO)
    for name in sorted(manifest):
        print(f'  {name} {manifest[name]["version"]} sha256 {manifest[name]["sha256"]}: {toolcache.version_line(toolcache.exe(REPO, name))}')
    return PASS, ', '.join(f'{name} {manifest[name]["version"]}' for name in sorted(manifest))


def cmd_evaluate(args):
    rows = evaluate_module.load_budgets(args.budgets)
    inputs = evaluate_module.load_inputs(args.sampler, args.control, args.frames, args.latency, args.events)
    results = evaluate_module.evaluate(rows, inputs, args.mode, args.warmup)
    return evaluate_module.judge(results)


def cmd_session(args):
    adb = _adb(args)
    current = session.Session(session.AdbDevice(adb), args.run_dir, args.package)
    if args.action == 'backup':
        manifest = current.backup()
        return PASS, f'backup verified: {len(manifest["files"])} files, {manifest["package_count"]} packages, snapshot {manifest["snapshot"]}'
    if args.action == 'check':
        manifest = current.require_verified_manifest()
        return PASS, f'manifest verified: {len(manifest["files"])} files'
    current.recovery = json.load(open(current.recovery_path, encoding='utf-8')) if os.path.isfile(current.recovery_path) else []
    current.rollback()
    return PASS, f'rollback proved: {len(current.recovery)} recorded change(s) gone'


def cmd_phase_a(args):
    from .phase_a import run_phase_a
    command = 'python tools/audit/rig.py phase-a ' + ' '.join(sys.argv[2:])
    return run_phase_a(args.serial, os.path.abspath(args.apk), args.run_id, REPO, args.control_seconds, args.idle_seconds,
                       args.cold_starts, args.tap_repeats, command)


def cmd_phase_b(args):
    from .phase_b import run_phase_b
    command = 'python tools/audit/rig.py phase-b ' + ' '.join(sys.argv[2:])
    only = set(args.only.split(',')) if args.only else None
    return run_phase_b(args.serial, os.path.abspath(args.apk), args.run_id, REPO, args.control_seconds, command, only)


def build_parser():
    parser = argparse.ArgumentParser(prog='rig.py', description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)

    p = sub.add_parser('selftest', help='fixture suite; live synthetic-subject checks with --serial')
    p.add_argument('--serial')
    p.set_defaults(fn=cmd_selftest, subject='rig-selftest')

    p = sub.add_parser('sample', help='sample one package for a scene into <out-dir>/<name>.csv')
    p.add_argument('--serial', required=True)
    p.add_argument('--package', required=True)
    p.add_argument('--duration', type=float, required=True, help='seconds')
    p.add_argument('--interval', type=float, default=5.0)
    p.add_argument('--out-dir', required=True)
    p.add_argument('--name', required=True, help='scene id, or control')
    p.add_argument('--access', default='auto', choices=['auto', 'shell', 'run-as'])
    p.set_defaults(fn=cmd_sample, subject='sampler')

    p = sub.add_parser('frames', help='frame-clock window for a package')
    p.add_argument('--serial', required=True)
    p.add_argument('--package', required=True)
    p.add_argument('--duration', type=float, required=True)
    p.add_argument('--poll', type=float, default=1.0)
    p.add_argument('--reset', action='store_true')
    p.add_argument('--out', required=True)
    p.set_defaults(fn=cmd_frames, subject='framestats')

    p = sub.add_parser('latency', help='injected input to visible change')
    p.add_argument('--serial', required=True)
    p.add_argument('--package', required=True)
    p.add_argument('--command', required=True, help='shell command that injects the input, e.g. "input tap 540 1200"')
    p.add_argument('--restore', required=True, help='shell command that returns the screen, e.g. "input keyevent KEYCODE_BACK"')
    p.add_argument('--repeats', type=int, default=10)
    p.add_argument('--out', required=True)
    p.set_defaults(fn=cmd_latency, subject='latency')

    p = sub.add_parser('ping', help='main-Looper queue wait of a debug build, on the device clock (decision 14.2)')
    p.add_argument('--serial', required=True)
    p.add_argument('--package', required=True)
    p.add_argument('--count', type=int, default=20)
    p.add_argument('--interval', type=float, default=0.5, help='seconds between pings')
    p.add_argument('--out', required=True)
    p.set_defaults(fn=cmd_ping, subject='ping')

    p = sub.add_parser('big-m3u', help='the synthetic oversized playlist of SC-10 and SC-20 (decision 14.4)')
    p.add_argument('--entries', type=int, default=bigm3u.DEFAULT_ENTRIES)
    p.add_argument('--out', required=True)
    p.set_defaults(fn=cmd_big_m3u, subject='big-m3u')

    p = sub.add_parser('tools', help='stream-fixture tool cache: download or verify ffmpeg and mediamtx (decision 14.8.2)')
    p.set_defaults(fn=cmd_tools, subject='tools')

    p = sub.add_parser('evaluate', help='judge a scene against the budget rows')
    p.add_argument('--budgets', default=os.path.join(AUDIT, 'budgets.json'))
    p.add_argument('--sampler')
    p.add_argument('--control')
    p.add_argument('--frames')
    p.add_argument('--latency')
    p.add_argument('--events')
    p.add_argument('--mode', default='playing', choices=['idle', 'playing', 'storm'])
    p.add_argument('--warmup', type=float, default=300.0, help='seconds excluded from slopes and bands')
    p.set_defaults(fn=cmd_evaluate, subject='scene')

    p = sub.add_parser('phase-a', help='phase A session: self-test, backup, control run, baseline scenes, proven rollback')
    p.add_argument('--serial', required=True)
    p.add_argument('--apk', required=True, help='the debug APK already installed on the device (recorded, not installed)')
    p.add_argument('--run-id', required=True)
    p.add_argument('--control-seconds', type=float, default=120)
    p.add_argument('--idle-seconds', type=float, default=180)
    p.add_argument('--cold-starts', type=int, default=5)
    p.add_argument('--tap-repeats', type=int, default=10)
    p.set_defaults(fn=cmd_phase_a, subject='phase-a')

    p = sub.add_parser('phase-b', help='phase B session: media core under stress against loopback stream fixtures')
    p.add_argument('--serial', required=True)
    p.add_argument('--apk', required=True, help='the debug APK already installed on the device (recorded, not installed)')
    p.add_argument('--run-id', required=True)
    p.add_argument('--control-seconds', type=float, default=120)
    p.add_argument('--only', help='comma-separated scene ids (SC-07,SC-08,SC-09,SC-21,SC-19,SC-20); default all')
    p.set_defaults(fn=cmd_phase_b, subject='phase-b')

    p = sub.add_parser('session', help='disposable-profile backup / check / rollback')
    p.add_argument('action', choices=['backup', 'check', 'rollback'])
    p.add_argument('--serial', required=True)
    p.add_argument('--package', required=True)
    p.add_argument('--run-dir', required=True)
    p.set_defaults(fn=cmd_session, subject='session')
    return parser


def main(argv=None):
    args = build_parser().parse_args(argv)
    run(args.subject, lambda: args.fn(args))
