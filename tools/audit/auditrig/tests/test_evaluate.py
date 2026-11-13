import os
import unittest

from auditrig import evaluate, sampler
from auditrig.tests.helpers import series, tempdir, write_json, write_sampler_csv
from auditrig.verdict import COULD_NOT_VERIFY, FAIL, PASS, CouldNotVerify

HOUR = [i * 5 for i in range(720)]
BUDGETS_JSON = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), 'budgets.json')

PSS_BAND = {'id': 'T-1', 'title': 'pss band', 'source': 'sampler:pss_kib', 'kind': 'level_band', 'min': 60000, 'max': 76000, 'unit': 'KiB', 'status': 'hypothesis'}
PSS_SLOPE = {'id': 'T-2', 'title': 'pss slope', 'source': 'sampler:pss_kib', 'kind': 'slope_per_hour_max', 'limit': 5000, 'unit': 'KiB/h', 'status': 'hypothesis'}
THREADS = {'id': 'T-3', 'title': 'threads', 'source': 'sampler:threads', 'kind': 'deviation_max', 'limit': 3, 'unit': 'threads', 'status': 'promoted'}
STALL = {'id': 'T-4', 'title': 'stall', 'source': 'frames:longest_main_thread_ms', 'kind': 'max', 'limit': 700, 'unit': 'ms', 'status': 'hypothesis'}
TAP = {'id': 'T-5', 'title': 'tap', 'source': 'latency:samples_ms', 'kind': 'percentile_max', 'percentile': 95, 'limit': 1000, 'unit': 'ms', 'status': 'hypothesis'}
CODEC = {'id': 'T-6', 'title': 'codec release', 'source': 'events:codec_release_ms', 'kind': 'event_max', 'limit': 10000, 'unit': 'ms', 'status': 'hypothesis'}


def csv_inputs(directory, name, pss, threads=None, **extra):
    path = os.path.join(directory, name)
    columns = {'pss_kib': pss, 'threads': threads or [40] * len(HOUR)}
    columns.update(extra)
    write_sampler_csv(path, HOUR, columns)
    return path


def run(rows, directory, scene, control=None, mode='playing', frames=None, latency=None, events=None, warmup=300):
    inputs = evaluate.load_inputs(scene, control, frames, latency, events)
    return evaluate.judge(evaluate.evaluate(rows, inputs, mode, warmup))


class EvaluateTests(unittest.TestCase):
    def test_a_scene_within_every_budget_passes(self):
        with tempdir() as d:
            scene = csv_inputs(d, 'scene.csv', series(HOUR, 65000, per_hour=100, noise=200), series(HOUR, 40, noise=0.5))
            code, detail = run([PSS_BAND, PSS_SLOPE, THREADS], d, scene)
            self.assertEqual(code, PASS, detail)
            self.assertIn('3 budget row(s) within budget, 2 still hypothesis', detail)

    def test_a_leak_clear_of_the_control_run_fails(self):
        with tempdir() as d:
            scene = csv_inputs(d, 'scene.csv', series(HOUR, 65000, per_hour=20000, noise=200))
            control = csv_inputs(d, 'control.csv', series(HOUR, 90000, per_hour=10, noise=200))
            code, detail = run([PSS_SLOPE], d, scene, control)
            self.assertEqual(code, FAIL, detail)
            self.assertIn('T-2', detail)

    def test_growth_the_control_run_shares_is_could_not_verify_not_a_finding(self):
        with tempdir() as d:
            scene = csv_inputs(d, 'scene.csv', series(HOUR, 65000, per_hour=20000, noise=200))
            control = csv_inputs(d, 'control.csv', series(HOUR, 90000, per_hour=20000, noise=200, seed=9))
            code, detail = run([PSS_SLOPE], d, scene, control)
            self.assertEqual(code, COULD_NOT_VERIFY, detail)

    def test_growth_with_no_control_run_cannot_be_called_a_leak(self):
        with tempdir() as d:
            scene = csv_inputs(d, 'scene.csv', series(HOUR, 65000, per_hour=20000, noise=200))
            self.assertEqual(run([PSS_SLOPE], d, scene)[0], COULD_NOT_VERIFY)

    def test_a_level_outside_its_band_fails(self):
        with tempdir() as d:
            scene = csv_inputs(d, 'scene.csv', series(HOUR, 90000, noise=100))
            self.assertEqual(run([PSS_BAND], d, scene)[0], FAIL)

    def test_thread_count_drifting_past_the_bound_fails(self):
        with tempdir() as d:
            threads = [40] * 400 + [47] * 320
            scene = csv_inputs(d, 'scene.csv', series(HOUR, 65000, noise=100), threads)
            self.assertEqual(run([THREADS], d, scene)[0], FAIL)

    def test_scalar_and_list_sources(self):
        with tempdir() as d:
            frames = os.path.join(d, 'frames.json')
            latency = os.path.join(d, 'latency.json')
            events = os.path.join(d, 'events.json')
            write_json(frames, {'longest_main_thread_ms': 512.0})
            write_json(latency, {'samples_ms': [300, 320, 340, 900, 950]})
            write_json(events, {'codec_release_ms': 4200})
            self.assertEqual(run([STALL, TAP, CODEC], d, None, frames=frames, latency=latency, events=events)[0], PASS)
            write_json(frames, {'longest_main_thread_ms': 912.0})
            code, detail = run([STALL, TAP, CODEC], d, None, frames=frames, latency=latency, events=events)
            self.assertEqual((code, 'T-4' in detail), (FAIL, True))

    def test_an_instrument_floor_turns_a_reading_into_a_bracket(self):
        with tempdir() as d:
            latency = os.path.join(d, 'latency.json')
            write_json(latency, {'samples_ms': [2150, 2171, 2234], 'floor_ms': 2039})
            code, detail = run([TAP], d, None, latency=latency)
            self.assertEqual(code, COULD_NOT_VERIFY, detail)
            state, why = evaluate.evaluate_row(TAP, evaluate.load_inputs(latency=latency), 'playing', 0)
            self.assertEqual(state, evaluate.UNDECIDED)
            self.assertIn('cannot resolve the budget', why)
            write_json(latency, {'samples_ms': [2150, 2171, 2234], 'floor_ms': 300})
            self.assertEqual(run([TAP], d, None, latency=latency)[0], FAIL)
            write_json(latency, {'samples_ms': [700, 800, 900], 'floor_ms': 2039})
            self.assertEqual(run([TAP], d, None, latency=latency)[0], PASS)

    def test_a_row_with_no_input_is_listed_and_nothing_evaluated_is_could_not_verify(self):
        with tempdir() as d:
            self.assertEqual(run([PSS_BAND, STALL], d, None)[0], COULD_NOT_VERIFY)

    def test_a_row_limited_to_another_mode_is_skipped(self):
        with tempdir() as d:
            scene = csv_inputs(d, 'scene.csv', series(HOUR, 65000, noise=100))
            only_idle = dict(PSS_BAND, modes=['idle'])
            self.assertEqual(run([only_idle], d, scene, mode='playing')[0], COULD_NOT_VERIFY)

    def test_a_budget_that_needs_hours_does_not_judge_a_one_hour_window(self):
        with tempdir() as d:
            scene = csv_inputs(d, 'scene.csv', series(HOUR, 65000, noise=100))
            long_row = dict(PSS_SLOPE, min_duration_s=14400)
            code, detail = run([long_row], d, scene)
            self.assertEqual(code, COULD_NOT_VERIFY, detail)

    def test_more_than_ten_percent_lost_samples_is_an_invalid_run(self):
        with tempdir() as d:
            path = os.path.join(d, 'scene.csv')
            ok = [0 if i % 5 == 0 else 1 for i in range(len(HOUR))]
            write_sampler_csv(path, HOUR, {'pss_kib': series(HOUR, 65000), 'threads': [40] * len(HOUR)}, ok)
            with self.assertRaises(CouldNotVerify):
                run([PSS_BAND], d, path)

    def test_the_real_budget_file_is_well_formed_and_only_the_owner_promoted_rows_are_promoted(self):
        rows = evaluate.load_budgets(BUDGETS_JSON)
        self.assertEqual(sorted({r['id'].split('-')[0] + '-' + r['id'].split('-')[1].rstrip('ab') for r in rows}),
                         sorted({f'B-{n}' for n in range(1, 11)}))
        promoted = {'B-1', 'B-3', 'B-5a', 'B-7b'}  # decision 14.1, 2026-11-08
        for row in rows:
            self.assertEqual(row['status'], 'promoted' if row['id'] in promoted else 'hypothesis', row['id'])
            if row['id'] in promoted:
                self.assertRegex(row['promoted'], r'^\d{4}-\d{2}-\d{2}$', row['id'])
                self.assertTrue(row['baseline'], row['id'])
                self.assertTrue(row['command'].startswith('py -3 tools/audit/rig.py '), row['id'])
            state, _ = evaluate.evaluate_row(row, {}, 'playing', 0)
            self.assertEqual(state, evaluate.SKIPPED, row['id'])

    def test_every_sampler_column_a_budget_names_exists(self):
        names = {r['source'].partition(':')[2] for r in evaluate.load_budgets(BUDGETS_JSON) if r['source'].startswith('sampler:')}
        self.assertTrue(names - {'store_kib'} <= set(sampler.COLUMNS))


if __name__ == '__main__':
    unittest.main()
