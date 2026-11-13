"""Main-Looper ping (S0025 section 7 item 2, decision 14.2): the counterpart of a WM_NULL ping. A debug build of the
phone app carries AuditPingProvider, whose receiver runs on a background thread, posts one message to the main Looper
and logs how long that message waited, both ends stamped on the device clock:

    SpAuditPing: pong run=<nonce> seq=<n> queue_ms=<ms> uptime_ms=<device uptime when the main thread ran it>

The rig sends the pings with `am broadcast` and reads the pongs back from logcat, so no adb round trip enters the
number and the reading carries no instrument floor. A release build has no receiver and an app that is not running
cannot answer; both read as no pong. A run that lost more than 10% of its pongs is invalid (section 7 item 5).
"""
import re
import secrets
import time

from .stats import percentile
from .verdict import CouldNotVerify

ACTION = 'com.sza.streamsplayer.debug.AUDIT_PING'
TAG = 'SpAuditPing'
LOST_LIMIT = 0.10
PONG = re.compile(r'\bpong run=(\S+) seq=(-?\d+) queue_ms=(\d+) uptime_ms=(\d+)')


def parse_pongs(text, run):
    """-> {seq: (queue_ms, uptime_ms)} for the pongs of one run; a seq answered twice keeps its first pong."""
    pongs = {}
    for match in PONG.finditer(text):
        if match.group(1) == run:
            pongs.setdefault(int(match.group(2)), (int(match.group(3)), int(match.group(4))))
    return pongs


def ping(adb, package, count, interval_s=0.5, settle_s=1.0, sleep=time.sleep, run=None):
    """Send `count` pings and read the pongs back -> the document for the run folder."""
    if count < 1:
        raise CouldNotVerify(f'a ping run needs at least one ping, got {count}')
    run = run or secrets.token_hex(4)
    for seq in range(count):
        adb.shell(f'am broadcast -a {ACTION} -p {package} --ei seq {seq} --es run {run}')
        sleep(interval_s)
    sleep(settle_s)
    pongs = parse_pongs(adb.shell(f'logcat -d -s {TAG}:I'), run)
    answered = [seq for seq in range(count) if seq in pongs]
    if not answered:
        raise CouldNotVerify(f'no pong for any of {count} pings: {package} is not running, or the build has no '
                             'AuditPingProvider (a release build never has it)')
    lost = count - len(answered)
    if lost / count > LOST_LIMIT:
        raise CouldNotVerify(f'{lost} of {count} pings got no pong (more than {LOST_LIMIT:.0%}): invalid run')
    samples = [pongs[seq][0] for seq in answered]
    return {'run': run, 'sent': count, 'received': len(answered), 'lost': lost, 'samples_ms': samples,
            'uptime_ms': [pongs[seq][1] for seq in answered], 'p50_ms': percentile(samples, 50),
            'p95_ms': percentile(samples, 95), 'max_ms': max(samples)}
