import unittest

from auditrig import parsers
from auditrig.tests.helpers import PACKAGE, data


class ParserTests(unittest.TestCase):
    def test_meminfo_reads_the_real_dump(self):
        mem = parsers.parse_meminfo(data('meminfo.txt'))
        self.assertEqual(mem, {'pid': 845, 'pss_kib': 109670, 'java_heap_kib': 32328, 'native_heap_kib': 13760})

    def test_meminfo_of_a_dead_process_is_unreadable_not_zero(self):
        with self.assertRaises(parsers.Unreadable):
            parsers.parse_meminfo('No process found for: com.sza.streamsplayer\n')

    def test_threads(self):
        self.assertEqual(parsers.parse_threads(data('proc_status.txt')), 75)
        with self.assertRaises(parsers.Unreadable):
            parsers.parse_threads('Name:\tx\n')

    def test_fd_count_counts_numeric_entries_and_refuses_a_denied_listing(self):
        self.assertEqual(parsers.parse_fd_count('0\n1\n2\n3\n10\n'), 5)
        with self.assertRaises(parsers.Unreadable):
            parsers.parse_fd_count('ls: /proc/845/fd: Permission denied\n')

    def test_alarm_count_is_exact_on_the_package_name(self):
        text = data('alarm.txt')
        self.assertEqual(parsers.parse_alarm_count(text, PACKAGE), 2)
        self.assertEqual(parsers.parse_alarm_count(text, PACKAGE + '.test'), 0)
        lookalike = text + '    ELAPSED #9: Alarm{1 type 3 origWhen 1 whenElapsed 1 other.' + PACKAGE + '}\n'
        self.assertEqual(parsers.parse_alarm_count(lookalike, PACKAGE), 2)
        with self.assertRaises(parsers.Unreadable):
            parsers.parse_alarm_count('nothing here', PACKAGE)

    def test_uid_matches_the_exact_package(self):
        self.assertEqual(parsers.parse_uid(data('pm_uid.txt'), PACKAGE), 10211)
        with self.assertRaises(parsers.Unreadable):
            parsers.parse_uid('package:com.sza.streamsplayer.test uid:10215\n', PACKAGE)

    def test_wakelocks_count_the_uid_only(self):
        self.assertEqual(parsers.parse_wakelocks(data('wakelocks_idle.txt'), 10211), 0)
        self.assertEqual(parsers.parse_wakelocks(data('wakelocks_held.txt'), 10211), 1)
        self.assertEqual(parsers.parse_wakelocks(data('wakelocks_held.txt'), 1000), 1)
        self.assertEqual(parsers.parse_wakelocks(data('wakelocks_held.txt'), 1021), 0)
        with self.assertRaises(parsers.Unreadable):
            parsers.parse_wakelocks('no section\n', 10211)

    def test_services_count_running_and_foreground(self):
        self.assertEqual(parsers.parse_foreground_services(data('services_foreground.txt'), PACKAGE), (1, 1))
        self.assertEqual(parsers.parse_foreground_services(data('services_foreground.txt'), 'com.android.systemui'), (2, 0))
        self.assertEqual(parsers.parse_foreground_services(data('services_none.txt'), PACKAGE), (0, 0))
        with self.assertRaises(parsers.Unreadable):
            parsers.parse_foreground_services('garbage', PACKAGE)

    def test_codec_instances_per_pid(self):
        self.assertEqual(parsers.parse_codec_instances(data('resource_manager_codec.txt'), 4242), 2)
        self.assertEqual(parsers.parse_codec_instances(data('resource_manager_codec.txt'), 845), 0)
        self.assertEqual(parsers.parse_codec_instances(data('resource_manager_empty.txt'), 845), 0)
        self.assertEqual(parsers.parse_codec_instances(data('resource_manager_empty.txt'), 9999), 0)
        with self.assertRaises(parsers.Unreadable):
            parsers.parse_codec_instances("Can't find service: media.resource_manager", 845)

    def test_du(self):
        self.assertEqual(parsers.parse_du_kib('1024\tcache\n256\tcode_cache\n'), 1280)
        self.assertEqual(parsers.parse_du_kib('du: files: No such file or directory\n512\tdatabases\n'), 512)

    def test_gfx_framestats_drops_layout_frames_and_keeps_the_rest(self):
        frames = parsers.parse_gfx_framestats(data('gfxinfo.txt'))
        self.assertEqual(len(frames), 4)
        self.assertEqual(frames[0]['sync_queued'] - frames[0]['intended_vsync'], 8_000_000)
        self.assertEqual(parsers.parse_gfx_framestats(data('gfxinfo_empty.txt')), [])
        with self.assertRaises(parsers.Unreadable):
            parsers.parse_gfx_framestats('---PROFILEDATA---\nFoo,Bar,\n1,2,\n---PROFILEDATA---\n')


if __name__ == '__main__':
    unittest.main()
