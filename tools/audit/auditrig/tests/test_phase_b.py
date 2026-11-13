import unittest

from auditrig import phase_b, streams
from auditrig.verdict import FAIL, PASS

TOAST = ('11-13 04:23:11.994 EventType: TYPE_NOTIFICATION_STATE_CHANGED; EventTime: 902991; PackageName: com.sza.streamsplayer; '
         'MovementGranularity: 0; Action: 0; [ ClassName: android.widget.Toast; Text: [No signal, StreamsPlayer]; ContentDescription: null')
DIALOG = ('EventType: TYPE_WINDOW_STATE_CHANGED; EventTime: 1; PackageName: com.sza.streamsplayer; [ ClassName: android.app.AlertDialog; '
          'Text: [Stream unavailable, "X" is not responding. Remove it from your list?, Cancel, Delete, Retry]; ContentDescription: null')
ACTIVITY = ('EventType: TYPE_WINDOW_STATE_CHANGED; EventTime: 1; PackageName: com.sza.streamsplayer; [ ClassName: '
            'com.sza.streamsplayer.ui.player.FullscreenPlayerActivity; Text: [StreamsPlayer]; ContentDescription: null')
OTHER = TOAST.replace('com.sza.streamsplayer', 'com.android.systemui')


class PhaseBTests(unittest.TestCase):
    def test_a_redacted_address_is_not_a_leak_and_a_whole_one_is(self):
        log = '\n'.join([
            'I StreamInlineAudioManager: inline audio start - http://192.0.2.1/[redacted] (bg=true)',
            'W StreamInlineAudioManager: inline audio error - http://10.0.2.2:8770/[redacted]',
            'W Some: failed http://10.0.2.2:8770/status/404/radio/a.mp3',
        ])
        self.assertEqual(phase_b.unredacted(log), ['http://10.0.2.2:8770/status/404/radio/a.mp3'])

    def test_the_rigs_own_ui_dumps_and_the_session_service_are_not_the_apps_log(self):
        log = '\n'.join([
            'I AccessibilityNodeInfoDumper: Skipping invisible child: text http://10.0.2.2:8799/radio/a.mp3',
            'D MediaSessionService: record uri=rtsp://10.0.2.2:8555/pattern',
        ])
        self.assertEqual(phase_b.unredacted(log), [])

    def test_toasts_and_dialogs_of_the_package_are_messages_and_activity_changes_are_not(self):
        listener = phase_b.Toasts(adb=None)
        listener.lines = [(10.0, OTHER), (10.5, ACTIVITY), (11.0, TOAST), (13.0, DIALOG)]
        found = listener.toasts(since=10.0)
        self.assertEqual([(m['kind'], m['text'][:18]) for m in found], [('toast', 'No signal'), ('dialog', 'Stream unavailable')])
        self.assertEqual(found[0]['ms'], 1000.0)

    def test_an_outcome_check_that_fails_fails_the_scene_and_is_named(self):
        verdict = phase_b.outcome_verdict('scene', [phase_b.check('a', True, ''), phase_b.check('b', False, 'why')])
        self.assertEqual(verdict['code'], FAIL)
        self.assertIn('b', verdict['detail'])
        self.assertEqual(phase_b.outcome_verdict('scene', [phase_b.check('a', True, '')])['code'], PASS)

    def test_the_fixture_playlist_lists_every_healthy_and_fault_channel_once(self):
        text = streams.playlist_text(streams.HEALTHY + [(t, u) for t, u, _ in streams.FAULTS])
        self.assertEqual(text.count('#EXTINF'), len(streams.HEALTHY) + len(streams.FAULTS))
        self.assertTrue(all('.invalid' not in line and 'example' not in line for line in text.splitlines()))
        self.assertTrue(all(u.split('/')[2].split(':')[0] in (streams.HOST, streams.DEAD_HOST) for _, u, _ in streams.FAULTS))


if __name__ == '__main__':
    unittest.main()
