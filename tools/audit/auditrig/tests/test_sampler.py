import csv
import os
import unittest

from auditrig import sampler
from auditrig.fakes import ScriptedAdb
from auditrig.tests.helpers import PACKAGE, FakeClock, data, tempdir
from auditrig.verdict import CouldNotVerify

DEBUGGABLE = 'uid=10211(u0_a211) gid=10211(u0_a211) groups=10211(u0_a211) context=u:r:untrusted_app:s0'


def table(meminfo=None, resource_manager='resource_manager_empty.txt', run_as=DEBUGGABLE):
    return {
        f'run-as {PACKAGE} id': run_as,
        'dumpsys meminfo': meminfo if meminfo is not None else data('meminfo.txt'),
        'cat /proc/845/status': data('proc_status.txt'),
        'cat /proc/4242/status': data('proc_status.txt'),
        f'run-as {PACKAGE} ls /proc/': ''.join(f'{n}\n' for n in range(10)),
        'ls /proc/': ''.join(f'{n}\n' for n in range(7)),
        'dumpsys media.resource_manager': data(resource_manager),
        'dumpsys alarm': data('alarm.txt'),
        'pm list packages -U': data('pm_uid.txt'),
        'dumpsys power': data('wakelocks_held.txt'),
        'dumpsys activity services': data('services_foreground.txt'),
        'du -sk cache': '1024\tcache\n256\tcode_cache\n',
        'du -sk files': '512\tfiles\n64\tdatabases\n8\tshared_prefs\n',
    }


class SamplerTests(unittest.TestCase):
    def test_one_sample_of_a_known_subject_reads_every_known_value(self):
        subject = sampler.Sampler(ScriptedAdb(table()), PACKAGE)
        self.assertEqual(subject.detect_access(), 'run-as')
        row = subject.sample(12.34)
        self.assertEqual(row, {'elapsed_s': 12.3, 'pss_kib': 109670, 'java_heap_kib': 32328, 'native_heap_kib': 13760,
                               'threads': 75, 'fds': 10, 'alarms': 2, 'codecs': 0, 'wakelocks': 1, 'services': 1,
                               'fg_services': 1, 'cache_kib': 1280, 'files_kib': 584, 'log_bytes': None, 'ok': 1})

    def test_codec_instances_follow_the_pid_of_the_sample(self):
        meminfo = data('meminfo.txt').replace('pid 845', 'pid 4242')
        row = sampler.Sampler(ScriptedAdb(table(meminfo, 'resource_manager_codec.txt')), PACKAGE, 'run-as').sample(0)
        self.assertEqual(row['codecs'], 2)

    def test_without_run_as_private_counters_are_left_empty_and_named(self):
        subject = sampler.Sampler(ScriptedAdb(table(run_as='run-as: package not debuggable: ' + PACKAGE)), PACKAGE)
        self.assertEqual(subject.detect_access(), 'shell')
        row = subject.sample(0)
        self.assertEqual((row['fds'], row['cache_kib'], row['files_kib']), (7, None, None))
        self.assertEqual(row['ok'], 1)

    def test_a_dead_process_is_a_lost_sample_not_a_zero(self):
        subject = sampler.Sampler(ScriptedAdb(table(meminfo='No process found for: ' + PACKAGE + '\n')), PACKAGE, 'run-as')
        row = subject.sample(0)
        self.assertEqual((row['pss_kib'], row['threads'], row['ok']), (None, None, 0))
        self.assertIn('pss_kib', subject.unavailable)

    def test_an_unknown_dump_shape_stops_instead_of_guessing(self):
        subject = sampler.Sampler(ScriptedAdb(table(meminfo='MEMINFO in pid 845 [x]\nsomething else\n')), PACKAGE, 'run-as')
        self.assertEqual(subject.sample(0)['ok'], 0)


class CollectTests(unittest.TestCase):
    def collect(self, directory, adb, duration=60, interval=5):
        clock = FakeClock()
        path = os.path.join(directory, 'scene.csv')
        subject = sampler.Sampler(adb, PACKAGE, 'run-as')
        return path, sampler.collect(subject, duration, interval, path, sleep=clock.sleep, clock=clock)

    def test_a_clean_run_writes_every_row_and_reads_back(self):
        with tempdir() as directory:
            path, (rows, summary) = self.collect(directory, ScriptedAdb(table()))
            self.assertEqual(len(rows), 13)
            self.assertEqual((summary['lost'], summary['access']), (0, 'run-as'))
            columns = sampler.read_csv(path)
            self.assertEqual(columns['elapsed_s'][:3], [0.0, 5.0, 10.0])
            self.assertEqual(set(columns['pss_kib']), {109670.0})

    def test_a_few_lost_samples_are_tolerated_and_counted(self):
        calls = {'n': 0}

        def flaky(command):
            calls['n'] += 1
            return 'No process found for: x\n' if calls['n'] == 5 else data('meminfo.txt')
        fixture = table()
        fixture['dumpsys meminfo'] = flaky
        with tempdir() as directory:
            _, (rows, summary) = self.collect(directory, ScriptedAdb(fixture))
            self.assertEqual(summary['lost'], 1)

    def test_more_than_ten_percent_lost_is_could_not_verify_and_the_file_is_kept(self):
        calls = {'n': 0}

        def dying(command):
            calls['n'] += 1
            return data('meminfo.txt') if calls['n'] <= 9 else 'No process found for: x\n'
        fixture = table()
        fixture['dumpsys meminfo'] = dying
        with tempdir() as directory:
            path = os.path.join(directory, 'scene.csv')
            with self.assertRaises(CouldNotVerify) as caught:
                self.collect(directory, ScriptedAdb(fixture))
            self.assertIn('lost 4 of 13', str(caught.exception))
            with open(path, newline='', encoding='utf-8') as handle:
                self.assertEqual(len(list(csv.reader(handle))), 14)

    def test_read_csv_refuses_a_file_that_is_not_a_sampler_csv(self):
        with tempdir() as directory:
            path = os.path.join(directory, 'x.csv')
            with open(path, 'w', encoding='utf-8') as handle:
                handle.write('a,b\n1,2\n')
            with self.assertRaises(CouldNotVerify):
                sampler.read_csv(path)


if __name__ == '__main__':
    unittest.main()
