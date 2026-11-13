"""Injected-input latency (S0025 section 7 item 2): `adb shell input` timestamped on the host; the wait ends at the
first UI-tree change the driver observes. This is the "user wait" number; the frame clock is the "main thread wait".

The instrument has a floor: the input call itself takes a round trip and each UI-tree read takes one more. The floor
and its jitter are calibrated before the scene (a no-op key and an unchanged tree), and a reading whose jitter is more
than 30% of the effect is not reported (section 7 item 5).
"""
import hashlib
import statistics
import time
import xml.etree.ElementTree as ET

from .stats import percentile
from .verdict import CouldNotVerify

JITTER_LIMIT = 0.30
VOLATILE = ('checked', 'selected', 'focused')


def tree_digest(xml_text, package):
    """Digest of the nodes of one package: class, id, text, description, bounds and the state flags that a user can
    see change. Nodes of other packages (the status-bar clock) cannot make the tree look changed."""
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError as error:
        raise CouldNotVerify(f'UI tree is not XML: {error}') from error
    parts = []
    for node in root.iter('node'):
        if node.get('package') != package:
            continue
        parts.append('|'.join([node.get('class', ''), node.get('resource-id', ''), node.get('text', ''),
                               node.get('content-desc', ''), node.get('bounds', '')] +
                              [node.get(flag, '') for flag in VOLATILE]))
    if not parts:
        raise CouldNotVerify(f'UI tree has no node of {package} (the app is not on screen)')
    return hashlib.sha1('\n'.join(parts).encode('utf-8')).hexdigest()


class Ui:
    """The device side of the instrument: a UI-tree read and an input call, both timed on the host clock."""

    def __init__(self, adb, package, clock=time.monotonic):
        self.adb = adb
        self.package = package
        self.clock = clock

    def dump(self, attempts=3):
        for attempt in range(attempts):  # a UiAutomation connection just released can refuse one dump (seen 2026-11-13)
            xml_text = self.adb.exec_out('uiautomator', 'dump', '/dev/stdout').split('UI hierchary dumped', 1)[0]
            if '<hierarchy' in xml_text:
                break
            time.sleep(1.0)
        else:
            raise CouldNotVerify('uiautomator returned no hierarchy')
        return xml_text[xml_text.index('<'):]

    def digest(self):
        started = self.clock()
        xml_text = self.dump()
        return tree_digest(xml_text, self.package), (self.clock() - started) * 1000.0

    def inject(self, command):
        started = self.clock()
        self.adb.shell(command)
        return (self.clock() - started) * 1000.0


def calibrate(ui, noop_command='input keyevent KEYCODE_UNKNOWN', repeats=8):
    """Floor of the instrument: median input-call and tree-read times, and their jitter."""
    inputs = [ui.inject(noop_command) for _ in range(repeats)]
    reads = []
    digests = set()
    for _ in range(repeats):
        digest, ms = ui.digest()
        digests.add(digest)
        reads.append(ms)
    if len(digests) != 1:
        raise CouldNotVerify('the UI tree changed while the instrument was being calibrated (a moving screen has no floor)')
    return {
        'input_ms': statistics.median(inputs),
        'read_ms': statistics.median(reads),
        'floor_ms': statistics.median(inputs) + statistics.median(reads),
        'jitter_ms': statistics.pstdev(inputs) + statistics.pstdev(reads),
    }


def has_node(xml_text, package, id_suffix):
    """True when the tree holds a node of the package whose resource id ends with id_suffix."""
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError as error:
        raise CouldNotVerify(f'UI tree is not XML: {error}') from error
    return any(n.get('package') == package and n.get('resource-id', '').endswith(id_suffix) for n in root.iter('node'))


def node_center(xml_text, package, text=None, id_suffix=None):
    """Centre (x, y) of the first node of the package with this text or id suffix; absent is COULD NOT VERIFY."""
    import re
    root = ET.fromstring(xml_text)
    for node in root.iter('node'):
        if node.get('package') != package:
            continue
        if (text is not None and node.get('text') == text) or (id_suffix and node.get('resource-id', '').endswith(id_suffix)):
            left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
            return (left + right) // 2, (top + bottom) // 2
    raise CouldNotVerify(f'no node with text={text!r} id suffix={id_suffix!r} on screen')


def time_to_node(ui, command, id_suffix, timeout_s=30.0):
    """Run `command` (a launch), then read the tree until a node ending in id_suffix shows. Returns ms from just
    before the command to the end of the first read that saw the node (an upper bound by at most one read)."""
    started = ui.clock()
    ui.inject(command)
    while True:
        seen = has_node(ui.dump(), ui.package, id_suffix)
        now = ui.clock()
        if seen:
            return (now - started) * 1000.0
        if now - started > timeout_s:
            raise CouldNotVerify(f'no node ending {id_suffix} within {timeout_s:.0f} s of "{command}"')


def measure_once(ui, command, timeout_s=10.0):
    """Inject, then read the tree until it differs from the one before. Returns latency in ms: from just before the
    input call to the end of the first read that saw the change (an upper bound by at most one read)."""
    before, _ = ui.digest()
    started = ui.clock()
    ui.inject(command)
    while True:
        digest, _ = ui.digest()
        now = ui.clock()
        if digest != before:
            return (now - started) * 1000.0
        if now - started > timeout_s:
            raise CouldNotVerify(f'the UI tree did not change within {timeout_s:.0f} s of "{command}"')


def measure(ui, command, restore_command, repeats=10, timeout_s=10.0, settle_s=0.5, sleep=time.sleep):
    """N repeats of one action. After each, restore_command returns the screen, and the tree must be the baseline
    again - a scene step that failed mid-way and was not restored invalidates the run."""
    floor = calibrate(ui)
    baseline, _ = ui.digest()
    samples = []
    for _ in range(repeats):
        samples.append(measure_once(ui, command, timeout_s))
        ui.inject(restore_command)
        sleep(settle_s)
        restored, _ = ui.digest()
        if restored != baseline:
            raise CouldNotVerify('the screen did not return to the baseline after the restore step')
    p50, p95 = percentile(samples, 50), percentile(samples, 95)
    if floor['jitter_ms'] > JITTER_LIMIT * p50:
        raise CouldNotVerify(f'instrument jitter {floor["jitter_ms"]:.0f} ms exceeds {JITTER_LIMIT:.0%} of the {p50:.0f} ms effect')
    return {'repeats': repeats, 'p50_ms': round(p50, 1), 'p95_ms': round(p95, 1), 'max_ms': round(max(samples), 1),
            'floor_ms': round(floor['floor_ms'], 1), 'jitter_ms': round(floor['jitter_ms'], 1), 'samples_ms': [round(s, 1) for s in samples]}
