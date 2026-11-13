"""The main-Looper ping collector against a scripted device, and the synthetic oversized playlist."""
import hashlib
import os
import re
import unittest

from auditrig import bigm3u, ping
from auditrig.fakes import ScriptedAdb
from auditrig.tests.helpers import tempdir
from auditrig.verdict import CouldNotVerify

PACKAGE = 'com.sza.streamsplayer'


def pinged_device(answer=lambda seq: True, queue_ms=lambda seq: 3 + seq):
    """A device whose debug receiver answers the pings `answer` lets through, with a known queue wait each."""
    adb = ScriptedAdb({})
    sent = []

    def broadcast(command):
        sent.append(command)
        return 'Broadcasting: Intent { act=com.sza.streamsplayer.debug.AUDIT_PING }\nBroadcast completed: result=0\n'

    def logcat(command):
        lines = ['I/SpAuditPing( 4242): pong run=other seq=0 queue_ms=9999 uptime_ms=1']
        for command_sent in sent:
            seq = int(re.search(r'--ei seq (\d+)', command_sent).group(1))
            run = re.search(r'--es run (\S+)', command_sent).group(1)
            if answer(seq):
                lines.append(f'I/SpAuditPing( 4242): pong run={run} seq={seq} queue_ms={queue_ms(seq)} uptime_ms={100000 + seq}')
        return '\n'.join(lines) + '\n'

    adb.table = [('am broadcast', broadcast), ('logcat -d', logcat)]
    return adb, sent


class PingTests(unittest.TestCase):
    def test_every_ping_answered_gives_the_device_clock_queue_waits(self):
        adb, sent = pinged_device()
        result = ping.ping(adb, PACKAGE, 10, sleep=lambda s: None, run='abcd1234')
        self.assertEqual((result['sent'], result['received'], result['lost']), (10, 10, 0))
        self.assertEqual(result['samples_ms'], [3 + s for s in range(10)])
        self.assertEqual(result['max_ms'], 12)
        self.assertNotIn('floor_ms', result)  # both ends on the device clock: no adb floor to bracket
        self.assertTrue(all(f'-a {ping.ACTION} -p {PACKAGE} ' in c and '--es run abcd1234' in c for c in sent))

    def test_a_pong_of_another_run_is_never_counted(self):
        pongs = ping.parse_pongs('pong run=other seq=0 queue_ms=9999 uptime_ms=1\npong run=mine seq=0 queue_ms=4 uptime_ms=2\n'
                                 'pong run=mine seq=0 queue_ms=5 uptime_ms=3\n', 'mine')
        self.assertEqual(pongs, {0: (4, 2)})

    def test_no_pong_at_all_could_not_verify(self):
        adb, _ = pinged_device(answer=lambda seq: False)
        with self.assertRaises(CouldNotVerify) as caught:
            ping.ping(adb, PACKAGE, 5, sleep=lambda s: None)
        self.assertIn('AuditPingProvider', str(caught.exception))

    def test_more_than_ten_percent_lost_is_an_invalid_run(self):
        adb, _ = pinged_device(answer=lambda seq: seq % 5 != 0)
        with self.assertRaises(CouldNotVerify) as caught:
            ping.ping(adb, PACKAGE, 10, sleep=lambda s: None)
        self.assertIn('invalid run', str(caught.exception))

    def test_ten_percent_lost_is_still_a_reading(self):
        adb, _ = pinged_device(answer=lambda seq: seq != 3)
        result = ping.ping(adb, PACKAGE, 10, sleep=lambda s: None)
        self.assertEqual((result['received'], result['lost']), (9, 1))

    def test_a_run_of_no_pings_could_not_verify(self):
        adb, _ = pinged_device()
        with self.assertRaises(CouldNotVerify):
            ping.ping(adb, PACKAGE, 0, sleep=lambda s: None)


class BigM3uTests(unittest.TestCase):
    def test_the_playlist_has_every_entry_with_a_distinct_unresolvable_address(self):
        with tempdir() as d:
            path = os.path.join(d, 'big.m3u')
            result = bigm3u.write(path, 2000)
            with open(path, encoding='utf-8') as handle:
                lines = handle.read().split('\n')
        self.assertEqual(lines[0], '#EXTM3U')
        urls = [line for line in lines[1:] if line and not line.startswith('#')]
        self.assertEqual((result['entries'], len(urls), len(set(urls))), (2000, 2000, 2000))
        self.assertTrue(all(re.match(r'^(https?|rtsp)://[a-z0-9]+\.example\.invalid[:/]', u) for u in urls))
        self.assertFalse(any(line.startswith('#EXT-X-') for line in lines))  # would turn the file into an HLS manifest
        self.assertEqual(len([line for line in lines if line.startswith('#EXTINF:')]), 2000)

    def test_the_same_arguments_give_the_same_bytes(self):
        with tempdir() as d:
            first, second = os.path.join(d, 'a.m3u'), os.path.join(d, 'b.m3u')
            a, b = bigm3u.write(first, 500), bigm3u.write(second, 500)
            with open(first, 'rb') as handle:
                content = handle.read()
        self.assertEqual(a['sha256'], b['sha256'])
        self.assertEqual((a['sha256'], a['bytes']), (hashlib.sha256(content).hexdigest(), len(content)))

    def test_long_and_unicode_names_and_quoted_commas_are_present(self):
        names = [bigm3u.entry(i).split('\n')[0] for i in range(200)]
        titles = [n.split('",', 1)[1] for n in names]  # the title starts after the first comma outside quotes
        self.assertIn(200, {len(t) for t in titles})
        self.assertTrue(any(re.search('[Ѐ-ӿ]', t) for t in titles))
        self.assertTrue(any('ї' in t for t in titles))
        self.assertTrue(any(re.search('[぀-ヿ]', t) for t in titles))
        self.assertTrue(any('group-title="Rock, Pop"' in n for n in names))

    def test_a_playlist_of_no_entries_could_not_verify(self):
        with tempdir() as d:
            with self.assertRaises(CouldNotVerify):
                bigm3u.write(os.path.join(d, 'empty.m3u'), 0)


if __name__ == '__main__':
    unittest.main()
