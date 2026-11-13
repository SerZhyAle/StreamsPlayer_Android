"""Live instrument self-test (S0025 section 7 item 6): run against a device before every session.

A synthetic subject with known values - two shell-owned `sleep` processes, one holding exactly four more file
descriptors than the other, each with one thread - proves the thread and fd readers on the real device; the parsers
are then run over the live output of every dumpsys the sampler uses, for a process that always exists (SystemUI),
so a device image whose text no longer matches stops the session before any scene runs. An impossible reading
raises CouldNotVerify: the instrument is broken, not the subject.
"""
from . import parsers
from .verdict import CouldNotVerify

REFERENCE_PACKAGE = 'com.android.systemui'
EXTRA_FDS = 4


def _spawn_sleeper(adb, digits):
    """Start an immortal shell that holds exactly the given (single-digit) fds open over /dev/null.
    The shell itself is the subject: an `exec sleep` would drop the descriptors, because the device shell opens
    descriptors above 2 close-on-exec (the first draft of this subject read a delta of 0). The digits must be
    single-digit - the 2026-10-24 API 35 image's sh rejects `exec 10</dev/null` outright ("10: inaccessible or
    not found") - and must not be one the spawn environment itself already leaks (fd 6 in every background shell
    on that image: an `exec 6</dev/null` replaces the leaked slot instead of adding one and reads a delta of 3)."""
    opens = ' '.join(f'exec {d}</dev/null;' for d in digits)
    inner = (opens + ' ' if digits else '') + 'while :; do sleep 1; done'
    out = adb.shell(f"nohup sh -c '{inner}' </dev/null >/dev/null 2>&1 & echo $!")
    try:
        return int(out.strip().splitlines()[-1])
    except (ValueError, IndexError) as error:
        raise CouldNotVerify(f'could not start the synthetic subject: {out!r}') from error


def _free_fd_digits(adb, pid):
    """Single-digit fd numbers (3..9) the given process does NOT hold - verified against the subject, never assumed."""
    held = {line.strip() for line in adb.shell(f'ls /proc/{pid}/fd').splitlines() if line.strip().isdigit()}
    return [str(d) for d in range(3, 10) if str(d) not in held]


def live_selftest(adb):
    """-> list of check names that passed. Raises CouldNotVerify on the first impossible reading."""
    passed = []
    pids = []
    try:
        plain = _spawn_sleeper(adb, [])
        pids.append(plain)
        digits = _free_fd_digits(adb, plain)[:EXTRA_FDS]
        if len(digits) < EXTRA_FDS:
            raise CouldNotVerify(f'cannot build the fd subject: only {len(digits)} free single-digit fd(s), need {EXTRA_FDS}')
        holder = _spawn_sleeper(adb, digits)
        pids.append(holder)
        for pid in pids:
            threads = parsers.parse_threads(adb.shell(f'cat /proc/{pid}/status'))
            if threads != 1:
                raise CouldNotVerify(f'known subject has 1 thread, the instrument read {threads}')
        passed.append('thread count of a known subject')
        fd_plain = parsers.parse_fd_count(adb.shell(f'ls /proc/{plain}/fd'))
        fd_holder = parsers.parse_fd_count(adb.shell(f'ls /proc/{holder}/fd'))
        if fd_holder - fd_plain != EXTRA_FDS:
            raise CouldNotVerify(f'known subjects differ by {EXTRA_FDS} fds, the instrument read {fd_holder - fd_plain}')
        passed.append('fd delta of a known subject')
        stable = parsers.parse_fd_count(adb.shell(f'ls /proc/{holder}/fd'))
        if stable != fd_holder:
            raise CouldNotVerify('the fd count of an idle subject moved between two reads')
        passed.append('fd count of an idle subject is stable')
    finally:
        for pid in pids:
            adb.shell(f'kill {pid}')
    device_ms, round_trip = adb.device_time_ms()
    if device_ms <= 0 or round_trip > 2000:
        raise CouldNotVerify(f'device clock reading is not plausible ({device_ms} ms, round trip {round_trip:.0f} ms)')
    passed.append('device clock round trip')
    package = REFERENCE_PACKAGE
    mem = parsers.parse_meminfo(adb.shell(f'dumpsys meminfo {package}'))
    if not 1000 < mem['pss_kib'] < 4_000_000 or mem['java_heap_kib'] < 0 or mem['native_heap_kib'] < 0:
        raise CouldNotVerify(f'meminfo of {package} reads an impossible {mem}')
    passed.append('meminfo format')
    if parsers.parse_threads(adb.shell(f'cat /proc/{mem["pid"]}/status')) < 2:
        raise CouldNotVerify(f'{package} reads fewer than 2 threads')
    passed.append('proc status format')
    parsers.parse_alarm_count(adb.shell('dumpsys alarm | grep -E "Pending alarm batches|Alarm[{]"'), package)
    passed.append('alarm dump format')
    parsers.parse_wakelocks(adb.shell("dumpsys power | sed -n '/^Wake Locks: size=/,/^$/p'"), 1000)
    passed.append('wake lock section format')
    parsers.parse_foreground_services(adb.shell(f'dumpsys activity services {package}'), package)
    passed.append('activity services format')
    parsers.parse_codec_instances(adb.shell("dumpsys media.resource_manager | sed -n '1,/Events logs/p'"), mem['pid'])
    passed.append('media resource manager format')
    parsers.parse_gfx_framestats(adb.shell(f'dumpsys gfxinfo {package} framestats'))
    passed.append('gfxinfo framestats header')
    return passed
