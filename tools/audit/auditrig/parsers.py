"""Pure parsers for the device text the rig reads. Each takes text, returns numbers, raises Unreadable on a shape it
does not know - an impossible reading stops the session before it can become a finding.

Formats verified against an API 35 google_apis_playstore emulator on 2026-10-23; formats that could only be seen
empty on that image are marked below and re-verified the first time a scene produces them.
"""
import re


class Unreadable(Exception):
    """The text is not the shape this parser was written against."""


def parse_meminfo(text):
    """`dumpsys meminfo <pkg>` -> pid, total_pss_kib, java_heap_kib, native_heap_kib (App Summary, private dirty)."""
    if 'No process found' in text or 'MEMINFO' not in text:
        raise Unreadable('no process in the meminfo output')
    pid = re.search(r'MEMINFO in pid (\d+)', text)
    total = re.search(r'TOTAL PSS:\s+(\d+)', text) or re.search(r'^\s*TOTAL\s+(\d+)', text, re.M)
    java = re.search(r'Java Heap:\s+(\d+)', text)
    native = re.search(r'Native Heap:\s+(\d+)', text)
    if not (pid and total and java and native):
        raise Unreadable('meminfo lacks pid, TOTAL PSS, Java Heap or Native Heap')
    return {'pid': int(pid.group(1)), 'pss_kib': int(total.group(1)),
            'java_heap_kib': int(java.group(1)), 'native_heap_kib': int(native.group(1))}


def parse_threads(text):
    """`/proc/<pid>/status` -> Threads."""
    match = re.search(r'^Threads:\s+(\d+)', text, re.M)
    if not match:
        raise Unreadable('no Threads line in /proc status')
    return int(match.group(1))


def parse_fd_count(text):
    """`ls /proc/<pid>/fd` (one name per line) -> count. A permission error is not zero."""
    if 'Permission denied' in text or 'No such file' in text:
        raise Unreadable('fd listing refused: ' + text.strip().splitlines()[0])
    return len([line for line in text.splitlines() if line.strip().isdigit()])


def parse_alarm_count(text, package):
    """`dumpsys alarm` -> pending alarms owned by the package (lines `<TYPE> #n: Alarm{id type N ... <package>}`)."""
    if 'Pending alarm batches' not in text and 'Alarm{' not in text:
        raise Unreadable('alarm dump has neither a batch header nor an alarm')
    pattern = re.compile(r'^\s*\w+ #\d+: Alarm\{[0-9a-f]+ type \d+ [^}]*(?<![\w.])' + re.escape(package) + r'\}', re.M)
    return len(pattern.findall(text))


def parse_uid(text, package):
    """`pm list packages -U <pkg>` -> uid of exactly that package."""
    match = re.search(r'^package:' + re.escape(package) + r'\s+uid:(\d+)', text, re.M)
    if not match:
        raise Unreadable(f'{package} has no uid line')
    return int(match.group(1))


def parse_wakelocks(text, uid):
    """`dumpsys power` -> count of wake locks held by the uid (the `Wake Locks: size=n` section, `uid=<n> pid=`)."""
    section = re.search(r'^Wake Locks: size=(\d+)\n((?:[ \t]+.*\n?)*)', text, re.M)
    if not section:
        raise Unreadable('no Wake Locks section in dumpsys power')
    held = [line for line in section.group(2).splitlines() if '_WAKE_LOCK' in line]
    return len([line for line in held if re.search(r'\(uid=' + str(uid) + r'\b', line)])


def parse_foreground_services(text, package):
    """`dumpsys activity services <pkg>` -> (services_running, foreground_services)."""
    records = [m.start() for m in re.finditer(r'^\s*\* ServiceRecord\{', text, re.M)]
    if not records and 'ACTIVITY MANAGER SERVICES' not in text:
        raise Unreadable('services dump has neither a record nor the header')
    running = foreground = 0
    for index, start in enumerate(records):
        block = text[start:records[index + 1] if index + 1 < len(records) else len(text)]
        if not re.search(r'^\s*\* ServiceRecord\{[^}]* ' + re.escape(package) + r'/', block, re.M):
            continue
        running += 1
        if re.search(r'isForeground=true', block):
            foreground += 1
    return running, foreground


def parse_codec_instances(text, pid):
    """`dumpsys media.resource_manager` -> codec resources held by the pid, from the Processes state block.

    Format seen on the audit image only with no codec client (every process block empty). A non-empty block is
    counted by the `secure-codec/` and `non-secure-codec/` resource types (the spelling the service's own event log
    uses) inside that pid's block; the first scene that holds a codec re-verifies it.
    """
    if 'ResourceManagerService' not in text:
        raise Unreadable('no ResourceManagerService header')
    processes = re.search(r'^\s*Processes:\n(.*?)(?=^\s*Process Pid override:|^\s*Events logs|\Z)', text, re.M | re.S)
    if not processes:
        raise Unreadable('no Processes block in media.resource_manager')
    blocks = re.split(r'(?m)^\s*Pid: ', processes.group(1))
    for block in blocks[1:]:
        if block.split()[0] == str(pid):
            return len(re.findall(r'\b(?:non-)?secure-codec/', block))
    return 0


def parse_du_kib(text):
    """`du -sk <dirs>` -> total KiB over the listed directories; missing directories count as 0 and are not errors."""
    total = 0
    for line in text.splitlines():
        match = re.match(r'^(\d+)\s+\S', line)
        if match:
            total += int(match.group(1))
    return total


GFX_REQUIRED = ('Flags', 'IntendedVsync', 'HandleInputStart', 'SyncQueued', 'FrameCompleted')


def parse_gfx_framestats(text):
    """`dumpsys gfxinfo <pkg> framestats` -> list of frame dicts (ns), layout-change frames (Flags != 0) dropped.

    Each dict: intended_vsync, handle_input_start, deadline (or None), sync_queued, completed. Several windows (one
    per surface) are merged. An unknown header is Unreadable, an empty window is an empty list.
    """
    frames = []
    inside = False
    header = None
    for line in text.splitlines():
        line = line.strip()
        if line == '---PROFILEDATA---':
            inside = not inside
            header = None
            continue
        if not inside or not line:
            continue
        if header is None:
            header = line.rstrip(',').split(',')
            if not all(name in header for name in GFX_REQUIRED):
                raise Unreadable('framestats header lacks one of ' + ', '.join(GFX_REQUIRED))
            continue
        cells = line.rstrip(',').split(',')
        if len(cells) < len(header):
            continue
        row = dict(zip(header, cells))
        try:
            if int(row['Flags']) != 0:
                continue
            frames.append({
                'intended_vsync': int(row['IntendedVsync']),
                'handle_input_start': int(row['HandleInputStart']),
                'deadline': int(row['FrameDeadline']) if row.get('FrameDeadline') else None,
                'sync_queued': int(row['SyncQueued']),
                'completed': int(row['FrameCompleted']),
            })
        except ValueError as error:
            raise Unreadable(f'framestats row is not numeric: {error}') from error
    return frames
