"""Every documented exit code of the rig scripts, forced through the real entry point as a child process."""
import json
import os
import subprocess
import sys
import unittest

from auditrig.tests.helpers import series, tempdir, write_json, write_sampler_csv, write_text

RIG = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), 'rig.py')
HOUR = [i * 5 for i in range(720)]


def rig(*args, env=None):
    merged = dict(os.environ, **(env or {}))
    done = subprocess.run([sys.executable, RIG] + list(args), capture_output=True, text=True, env=merged)
    lines = [line for line in done.stdout.splitlines() if line.strip()]
    return done.returncode, (lines[-1] if lines else ''), done.stdout


def budgets(directory):
    path = os.path.join(directory, 'budgets.json')
    row = {'id': 'T-1', 'title': 'pss slope', 'source': 'sampler:pss_kib', 'kind': 'slope_per_hour_max', 'limit': 5000, 'unit': 'KiB/h', 'status': 'hypothesis'}
    write_json(path, {'rows': [row]})
    return path


class ExitCodeTests(unittest.TestCase):
    def test_evaluate_exits_0_when_the_scene_is_within_budget(self):
        with tempdir() as d:
            scene = os.path.join(d, 'scene.csv')
            write_sampler_csv(scene, HOUR, {'pss_kib': series(HOUR, 65000, noise=200), 'threads': [40] * len(HOUR)})
            code, last, _ = rig('evaluate', '--budgets', budgets(d), '--sampler', scene)
            self.assertEqual((code, last), (0, 'scene: PASS (1 budget row(s) within budget, 1 still hypothesis)'))

    def test_evaluate_exits_1_on_a_defect_and_prints_the_reason_before_the_verdict_line(self):
        with tempdir() as d:
            scene, control = os.path.join(d, 'scene.csv'), os.path.join(d, 'control.csv')
            write_sampler_csv(scene, HOUR, {'pss_kib': series(HOUR, 65000, per_hour=20000, noise=200), 'threads': [40] * len(HOUR)})
            write_sampler_csv(control, HOUR, {'pss_kib': series(HOUR, 90000, noise=200), 'threads': [40] * len(HOUR)})
            code, last, out = rig('evaluate', '--budgets', budgets(d), '--sampler', scene, '--control', control)
            self.assertEqual((code, last), (1, 'scene: FAIL (1 of 1 rows over budget: T-1)'))
            self.assertIn('T-1   OVER', out)

    def test_evaluate_exits_2_when_nothing_could_be_judged(self):
        with tempdir() as d:
            code, last, _ = rig('evaluate', '--budgets', budgets(d))
            self.assertEqual((code, last), (2, 'scene: COULD NOT VERIFY (no budget row had an input: nothing was judged)'))

    def test_evaluate_exits_2_on_an_unreadable_input_and_never_0(self):
        with tempdir() as d:
            code, last, out = rig('evaluate', '--budgets', budgets(d), '--sampler', os.path.join(d, 'absent.csv'))
            self.assertEqual(code, 2)
            self.assertTrue(last.startswith('scene: COULD NOT VERIFY ('), last)
            self.assertIn('reason:', out)

    def test_evaluate_exits_2_on_an_invalid_run(self):
        with tempdir() as d:
            scene = os.path.join(d, 'scene.csv')
            ok = [0 if i % 4 == 0 else 1 for i in range(len(HOUR))]
            write_sampler_csv(scene, HOUR, {'pss_kib': series(HOUR, 65000), 'threads': [40] * len(HOUR)}, ok)
            code, last, _ = rig('evaluate', '--budgets', budgets(d), '--sampler', scene)
            self.assertEqual(code, 2)
            self.assertIn('invalid run', last)

    def test_a_collector_exits_2_when_the_tool_it_drives_is_absent(self):
        with tempdir() as d:
            env = {'SP_AUDIT_ADB': os.path.join(d, 'no-such-adb.exe'), 'ANDROID_HOME': d, 'ANDROID_SDK_ROOT': d, 'PATH': d}
            code, last, _ = rig('sample', '--serial', 'x', '--package', 'p', '--duration', '1', '--out-dir', d, '--name', 's', env=env)
            self.assertEqual((code, last.split(' (')[0]), (2, 'sampler: COULD NOT VERIFY'))

    def test_the_ping_collector_exits_2_when_adb_is_absent(self):
        with tempdir() as d:
            env = {'SP_AUDIT_ADB': os.path.join(d, 'no-such-adb.exe'), 'ANDROID_HOME': d, 'ANDROID_SDK_ROOT': d, 'PATH': d}
            code, last, _ = rig('ping', '--serial', 'x', '--package', 'p', '--out', os.path.join(d, 'ping.json'), env=env)
            self.assertEqual((code, last.split(' (')[0]), (2, 'ping: COULD NOT VERIFY'))

    def test_big_m3u_exits_0_with_its_size_and_digest_on_the_verdict_line(self):
        with tempdir() as d:
            code, last, _ = rig('big-m3u', '--entries', '50', '--out', os.path.join(d, 'big.m3u'))
            self.assertEqual(code, 0)
            self.assertRegex(last, r'^big-m3u: PASS \(50 entries, \d+ bytes, sha256 [0-9a-f]{64}\)$')

    def test_big_m3u_exits_2_on_an_empty_size(self):
        with tempdir() as d:
            code, last, _ = rig('big-m3u', '--entries', '0', '--out', os.path.join(d, 'big.m3u'))
            self.assertEqual((code, last), (2, 'big-m3u: COULD NOT VERIFY (a playlist needs at least one entry, got 0)'))

    def test_an_internal_error_still_ends_in_a_verdict_line_and_exit_2(self):
        with tempdir() as d:
            bad = os.path.join(d, 'budgets.json')
            write_text(bad, '{ not json')
            code, last, _ = rig('evaluate', '--budgets', bad)
            self.assertEqual(code, 2)
            self.assertTrue(last.startswith('scene: COULD NOT VERIFY (internal error:'), last)


if __name__ == '__main__':
    unittest.main()
