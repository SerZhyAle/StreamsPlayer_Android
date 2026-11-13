"""The sampler (S0025 section 7 item 2): every N seconds during a scene, the resource counters of one package, from
outside the process, into one CSV in the S0004 finish-budget.csv shape (a header, then one row per sample).

Reading a metric can fail without the run being wrong (a counter the image does not expose); that metric is
left empty in the row and named in the summary, and a row whose core liveness metrics (PSS, threads) are missing
is a lost sample. More than 10% lost samples makes the run COULD NOT VERIFY (section 7 item 5).
"""
import csv
import os
import time

from . import parsers
from .verdict import CouldNotVerify

COLUMNS = ['elapsed_s', 'pss_kib', 'java_heap_kib', 'native_heap_kib', 'threads', 'fds', 'alarms', 'codecs',
           'wakelocks', 'services', 'fg_services', 'cache_kib', 'files_kib', 'log_bytes', 'ok']
CORE = ('pss_kib', 'threads')
LOST_LIMIT = 0.10


class LogTail:
    """A streaming `adb logcat` capture to a file; its size is the log volume. A ring-buffer `logcat -d` is not
    monotonic over hours, so the volume is counted on a stream instead. Closed in finally by the caller."""

    def __init__(self, adb, path):
        self.path = path
        self._process = None
        self._adb = adb

    def start(self):
        self._adb.run('logcat', '-c', check=False)
        self._handle = open(self.path, 'wb')
        self._process = self._adb.popen('logcat', '-v', 'threadtime', stdout=self._handle, stderr=self._handle)

    def size(self):
        self._handle.flush()
        return os.path.getsize(self.path)

    def stop(self):
        if self._process is not None:
            self._process.terminate()
            try:
                self._process.wait(timeout=10)
            except Exception:  # noqa: BLE001
                self._process.kill()
            self._process = None
        if getattr(self, '_handle', None):
            self._handle.close()


class Sampler:
    """One package on one device. `adb` is any object with shell(command) -> text."""

    def __init__(self, adb, package, access='auto', log_tail=None):
        self.adb = adb
        self.package = package
        self.access = access
        self.log_tail = log_tail
        self._uid = None
        self.unavailable = {}

    def detect_access(self):
        """How to read the app's private counters: 'run-as' (debuggable build), 'shell' (readable as shell), or none."""
        if self.access != 'auto':
            return self.access
        answer = self.adb.shell(f'run-as {self.package} id')
        self.access = 'run-as' if answer.strip().startswith('uid=') else 'shell'
        return self.access

    def _private(self, command):
        return f'run-as {self.package} {command}' if self.access == 'run-as' else command

    def _note(self, metric, error):
        self.unavailable.setdefault(metric, str(error))
        return None

    def _try(self, metric, produce):
        try:
            return produce()
        except (parsers.Unreadable, CouldNotVerify) as error:
            return self._note(metric, error)

    def sample(self, elapsed_s):
        row = {name: None for name in COLUMNS}
        row['elapsed_s'] = round(elapsed_s, 1)
        pid = None
        try:
            mem = parsers.parse_meminfo(self.adb.shell(f'dumpsys meminfo {self.package}'))
            pid = mem['pid']
            row.update(pss_kib=mem['pss_kib'], java_heap_kib=mem['java_heap_kib'], native_heap_kib=mem['native_heap_kib'])
        except (parsers.Unreadable, CouldNotVerify) as error:
            self._note('pss_kib', error)
        if pid is not None:
            row['threads'] = self._try('threads', lambda: parsers.parse_threads(self.adb.shell(f'cat /proc/{pid}/status')))
            row['fds'] = self._try('fds', lambda: parsers.parse_fd_count(self.adb.shell(self._private(f'ls /proc/{pid}/fd'))))
            row['codecs'] = self._try('codecs', lambda: parsers.parse_codec_instances(
                self.adb.shell("dumpsys media.resource_manager | sed -n '1,/Events logs/p'"), pid))
        row['alarms'] = self._try('alarms', lambda: parsers.parse_alarm_count(
            self.adb.shell('dumpsys alarm | grep -E "Pending alarm batches|Alarm[{]"'), self.package))
        if self._uid is None:
            self._uid = self._try('wakelocks', lambda: parsers.parse_uid(self.adb.shell(f'pm list packages -U {self.package}'), self.package))
        if self._uid is not None:
            row['wakelocks'] = self._try('wakelocks', lambda: parsers.parse_wakelocks(
                self.adb.shell("dumpsys power | sed -n '/^Wake Locks: size=/,/^$/p'"), self._uid))
        services = self._try('services', lambda: parsers.parse_foreground_services(
            self.adb.shell(f'dumpsys activity services {self.package}'), self.package))
        if services:
            row['services'], row['fg_services'] = services
        if self.access == 'run-as':
            row['cache_kib'] = self._try('cache_kib', lambda: parsers.parse_du_kib(self.adb.shell(self._private('du -sk cache code_cache'))))
            row['files_kib'] = self._try('files_kib', lambda: parsers.parse_du_kib(self.adb.shell(self._private('du -sk files databases shared_prefs'))))
        if self.log_tail is not None:
            row['log_bytes'] = self.log_tail.size()
        row['ok'] = 1 if all(row[name] is not None for name in CORE) else 0
        return row


def collect(sampler, duration_s, interval_s, csv_path, sleep=time.sleep, clock=time.monotonic):
    """Sample for duration_s, append each row to csv_path as it is taken, return (rows, summary).

    The file is kept whatever the verdict: an invalid run stays in its run folder with the reason.
    """
    sampler.detect_access()
    start = clock()
    rows = []
    with open(csv_path, 'w', newline='', encoding='utf-8') as handle:
        writer = csv.writer(handle)
        writer.writerow(COLUMNS)
        while True:
            taken = clock() - start
            row = sampler.sample(taken)
            rows.append(row)
            writer.writerow(['' if row[name] is None else row[name] for name in COLUMNS])
            handle.flush()
            if taken + interval_s > duration_s:
                break
            sleep(max(0.0, interval_s - (clock() - start - taken)))
    lost = len([r for r in rows if not r['ok']])
    summary = {'samples': len(rows), 'lost': lost, 'lost_fraction': lost / len(rows),
               'access': sampler.access, 'unavailable': dict(sampler.unavailable)}
    if summary['lost_fraction'] > LOST_LIMIT:
        raise CouldNotVerify(f'sampler lost {lost} of {len(rows)} samples ({summary["lost_fraction"]:.0%} > {LOST_LIMIT:.0%})')
    return rows, summary


def read_csv(path):
    """Read a sampler CSV back: dict of column -> list (None for an empty cell)."""
    columns = {name: [] for name in COLUMNS}
    with open(path, newline='', encoding='utf-8') as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != COLUMNS:
            raise CouldNotVerify(f'{path} is not a sampler CSV (header {reader.fieldnames})')
        for line in reader:
            for name in COLUMNS:
                cell = line[name]
                columns[name].append(None if cell == '' else float(cell))
    if not columns['elapsed_s']:
        raise CouldNotVerify(f'{path} has no rows')
    return columns
