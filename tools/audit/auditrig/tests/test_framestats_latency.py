import unittest

from auditrig import framestats, latency
from auditrig.fakes import ScriptedAdb
from auditrig.tests.helpers import PACKAGE, data
from auditrig.verdict import CouldNotVerify


class FrameStatsTests(unittest.TestCase):
    def test_known_frames_give_known_stalls(self):
        collector = framestats.FrameCollector(ScriptedAdb({'framestats': data('gfxinfo.txt')}), PACKAGE)
        collector.poll()
        metrics = collector.metrics()
        self.assertEqual((metrics['frames'], metrics['longest_main_thread_ms'], metrics['longest_frame_ms'],
                          metrics['missed_deadlines'], metrics['missed_fraction']), (4, 512.0, 522.0, 2, 0.5))
        self.assertEqual(metrics['dropped_frames'], 0)

    def test_polls_merge_by_vsync_and_never_double_count(self):
        collector = framestats.FrameCollector(ScriptedAdb({'framestats': data('gfxinfo.txt')}), PACKAGE)
        self.assertEqual(collector.poll(), 4)
        self.assertEqual(collector.poll(), 4)

    def test_a_window_with_no_frames_observed_nothing(self):
        collector = framestats.FrameCollector(ScriptedAdb({'framestats': data('gfxinfo_empty.txt')}), PACKAGE)
        collector.poll()
        with self.assertRaises(CouldNotVerify) as caught:
            collector.metrics()
        self.assertIn('no frames', str(caught.exception))

    def test_an_unknown_header_is_could_not_verify(self):
        collector = framestats.FrameCollector(ScriptedAdb({'framestats': '---PROFILEDATA---\nA,B,\n1,2,\n---PROFILEDATA---\n'}), PACKAGE)
        with self.assertRaises(CouldNotVerify):
            collector.poll()

    def test_a_frame_out_of_timeline_order_is_dropped_and_counted(self):
        """Live 2026-10-24, API 35 audit image: after a gfxinfo reset one row's FrameCompleted sat 65 s before its
        vsync while every healthy frame keeps IntendedVsync <= HandleInputStart <= SyncQueued <= FrameCompleted."""
        def frame(iv, his, sq, fc):
            return {'intended_vsync': iv, 'handle_input_start': his, 'deadline': iv + 16_666_666, 'sync_queued': sq, 'completed': fc}

        healthy = [frame(i * 16_666_666, i * 16_666_666 + 2_000_000, i * 16_666_666 + 8_000_000, i * 16_666_666 + 12_000_000)
                   for i in range(12)]
        corrupt = frame(1_000_000_000_000, 1_000_002_000_000, 1_000_008_000_000, 934_765_000_000)  # completed < sync_queued
        metrics = framestats.frame_metrics(healthy + [corrupt])
        self.assertEqual(metrics['frames'], 12)
        self.assertEqual(metrics['dropped_frames'], 1)

    def test_more_than_ten_percent_out_of_order_frames_is_could_not_verify(self):
        good = {'intended_vsync': 0, 'handle_input_start': 2, 'deadline': 16, 'sync_queued': 8, 'completed': 12}
        corrupt = {'intended_vsync': 100, 'handle_input_start': 102, 'deadline': 116, 'sync_queued': 108, 'completed': 50}
        with self.assertRaises(CouldNotVerify) as caught:
            framestats.frame_metrics([good, corrupt])
        self.assertIn('timeline order', str(caught.exception))

    def test_a_window_of_only_out_of_order_frames_means_the_instrument_is_broken(self):
        corrupt = {'intended_vsync': 100, 'handle_input_start': 102, 'deadline': 116, 'sync_queued': 108, 'completed': 50}
        with self.assertRaises(CouldNotVerify) as caught:
            framestats.frame_metrics([corrupt])
        self.assertIn('timeline order', str(caught.exception))

    def test_run_window_polls_until_the_time_is_up(self):
        adb = ScriptedAdb({'framestats': data('gfxinfo.txt')})
        collector = framestats.FrameCollector(adb, PACKAGE)
        now = {'t': 0.0}

        def clock():
            return now['t']

        def sleep(seconds):
            now['t'] += seconds
        metrics = collector.run_window(3, poll_s=1.0, sleep=sleep, clock=clock)
        self.assertEqual(metrics['frames'], 4)
        self.assertEqual(len(adb.commands), 4)


class FakeUi:
    """A screen that changes `change_after` seconds after the tap and goes back on `back`. Every call costs time."""

    def __init__(self, change_after=0.7, input_s=0.12, read_s=0.30, noop_costs=None, restores=True):
        self.t = 0.0
        self.state = 'A'
        self.pending = None
        self.change_after, self.input_s, self.read_s = change_after, input_s, read_s
        self.noop_costs, self.restores = list(noop_costs or []), restores
        self.noop_calls = 0

    def clock(self):
        return self.t

    def digest(self):
        self.t += self.read_s
        if self.pending is not None and self.t >= self.pending:
            self.state, self.pending = 'B', None
        return self.state, self.read_s * 1000

    def inject(self, command):
        cost = self.input_s
        if command == 'tap' and self.change_after is not None:
            self.pending = self.t + self.change_after
        elif command == 'back' and self.restores:
            self.state, self.pending = 'A', None
        elif command == 'noop' and self.noop_costs:
            cost = self.noop_costs[self.noop_calls % len(self.noop_costs)]
            self.noop_calls += 1
        self.t += cost
        return cost * 1000


class LatencyTests(unittest.TestCase):
    def test_tree_digest_ignores_other_packages_and_sees_the_apps_own_change(self):
        own = '<node package="com.sza.streamsplayer" class="v" resource-id="a" text="Radio" content-desc="" bounds="[0,0][9,9]" checked="false" selected="false" focused="false"/>'
        clock = lambda text: f'<node package="com.android.systemui" class="v" resource-id="clock" text="{text}" content-desc="" bounds="[0,0][1,1]"/>'
        page = lambda text, body: f'<hierarchy>{clock(text)}{body}</hierarchy>'
        base = latency.tree_digest(page('12:00', own), PACKAGE)
        self.assertEqual(latency.tree_digest(page('12:01', own), PACKAGE), base)
        self.assertNotEqual(latency.tree_digest(page('12:00', own.replace('Radio', 'Radio 2')), PACKAGE), base)
        self.assertNotEqual(latency.tree_digest(page('12:00', own.replace('focused="false"', 'focused="true"')), PACKAGE), base)
        with self.assertRaises(CouldNotVerify):
            latency.tree_digest(page('12:00', ''), PACKAGE)
        with self.assertRaises(CouldNotVerify):
            latency.tree_digest('not xml', PACKAGE)

    def test_a_known_delay_is_measured_within_one_instrument_step(self):
        ui = FakeUi(change_after=0.7)
        got = latency.measure_once(ui, 'tap')
        self.assertGreaterEqual(got, 700)
        self.assertLessEqual(got, 700 + 120 + 300)

    def test_measure_reports_percentiles_floor_and_jitter(self):
        ui = FakeUi(change_after=0.7)
        result = latency.measure(ui, 'tap', 'back', repeats=5, sleep=lambda s: None)
        self.assertEqual(result['repeats'], 5)
        self.assertEqual(result['floor_ms'], 420.0)
        self.assertEqual(result['jitter_ms'], 0.0)
        self.assertTrue(700 <= result['p50_ms'] <= 1120)

    def test_jitter_above_thirty_percent_of_the_effect_is_not_reported(self):
        ui = FakeUi(change_after=0.7, noop_costs=[0.1, 0.9])
        original = latency.calibrate
        latency.calibrate = lambda u: original(u, noop_command='noop')
        try:
            with self.assertRaises(CouldNotVerify) as caught:
                latency.measure(ui, 'tap', 'back', repeats=3, sleep=lambda s: None)
        finally:
            latency.calibrate = original
        self.assertIn('jitter', str(caught.exception))

    def test_a_screen_that_does_not_come_back_invalidates_the_run(self):
        with self.assertRaises(CouldNotVerify) as caught:
            latency.measure(FakeUi(restores=False), 'tap', 'back', repeats=3, sleep=lambda s: None)
        self.assertIn('baseline', str(caught.exception))

    def test_an_input_that_changes_nothing_times_out_as_could_not_verify(self):
        with self.assertRaises(CouldNotVerify) as caught:
            latency.measure_once(FakeUi(change_after=None), 'tap', timeout_s=3)
        self.assertIn('did not change', str(caught.exception))


if __name__ == '__main__':
    unittest.main()


class NodeTests(unittest.TestCase):
    XML = ('<hierarchy><node package="com.sza.streamsplayer" resource-id="com.sza.streamsplayer:id/tvChannelTitle" text="# Row" '
           'bounds="[100,200][300,260]"/><node package="com.sza.streamsplayer" resource-id="" text="Radio" bounds="[10,20][50,60]"/>'
           '<node package="com.android.systemui" resource-id="x:id/tvChannelTitle" text="decoy" bounds="[0,0][1,1]"/></hierarchy>')

    def test_has_node_matches_the_id_suffix_of_the_package_only(self):
        self.assertTrue(latency.has_node(self.XML, PACKAGE, 'tvChannelTitle'))
        self.assertFalse(latency.has_node(self.XML, PACKAGE, 'tvOther'))
        self.assertFalse(latency.has_node(self.XML.replace('com.sza.streamsplayer"', 'other"'), PACKAGE, 'tvChannelTitle'))

    def test_node_center_by_text_and_by_id(self):
        self.assertEqual(latency.node_center(self.XML, PACKAGE, text='Radio'), (30, 40))
        self.assertEqual(latency.node_center(self.XML, PACKAGE, id_suffix='tvChannelTitle'), (200, 230))
        with self.assertRaises(CouldNotVerify):
            latency.node_center(self.XML, PACKAGE, text='Absent')

    def test_time_to_node_waits_for_the_node_and_gives_up(self):
        class Ui:
            package = PACKAGE

            def __init__(self, appears_after):
                self.t, self.appears_after, self.launched = 0.0, appears_after, None

            def clock(self):
                return self.t

            def inject(self, command):
                self.launched = self.t
                self.t += 0.2

            def dump(self):
                self.t += 0.3
                return NodeTests.XML if self.appears_after is not None and self.t - self.launched >= self.appears_after else '<hierarchy/>'
        got = latency.time_to_node(Ui(1.0), 'launch', 'tvChannelTitle')
        self.assertTrue(1000 <= got <= 1000 + 500, got)
        with self.assertRaises(CouldNotVerify):
            latency.time_to_node(Ui(None), 'launch', 'tvChannelTitle', timeout_s=3)
