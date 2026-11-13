"""Phase B driver (S0025 section 11): media core under stress - SC-07, SC-08, SC-09, SC-19, SC-20, SC-21 against P1-P4,
SC-06 P2-P4 and SC-10 with the 100,000-entry M3U (decision 14.4). Owner named the session on 2026-11-13 (decision 14.8.1):
store debug build, AVD sp_audit_api35, one pass. Every stream is a loopback fixture (streams.py, decision 14.8.2).

Instruments added to phase A's for this phase:
  toasts      `uiautomator events` read on the host: a Toast never enters the UI tree, its accessibility event carries
              the text. The listener holds the UiAutomation connection, so no tree is dumped while it runs; the wait is
              timed from just before the input call (floor: one input round trip).
  focus       `dumpsys window` mCurrentFocus - which activity the user sees (fullscreen player open, closed).
  fixtures    the request log and the open-stream counter of the fixture server: the retry policy and the number of
              concurrent streams seen from the server side.
  release     after a stop, the time until the package holds no codec, no wake lock, no audio focus and no foreground
              service (B-6), each polled from outside.
Every scene writes <scene>-events.json; the run folder also holds the sampler CSVs, the logcat tails, the fixture logs and
verdicts.json. A check whose subject is an outcome (a raw class name in a message, a whole URL in the log, a retry that
does not recover, two players after a storm) is judged here; a number with a budget row is judged by evaluate.
"""
import json
import os
import re
import subprocess
import threading
import time
import xml.etree.ElementTree as ET

from . import evaluate as evaluate_module
from . import framestats, instruments, latency, parsers, ping, session, streams
from . import sampler as sampler_module
from .adb import Adb, find_adb
from .phase_a import ACTIVITY, CONTROL_PACKAGE, PACKAGE, _environment, _judge_scene, _sample, _write_json
from .verdict import COULD_NOT_VERIFY, FAIL, PASS, CouldNotVerify

PLAYER = 'FullscreenPlayerActivity'
time_scale = 1.0  # a debug pass shortens the sampling windows; a session always runs at 1.0
RAW_ERROR = re.compile(r'(Exception|java\.|kotlin\.|okhttp|androidx\.|media3|Error:|errno|ECONN|/\d+\.\d+\.\d+\.\d+)', re.I)
UNREDACTED = re.compile(r'(10\.0\.2\.2:\d+/(?!\[redacted\])\S+|192\.0\.2\.1/(?!\[redacted\])\S+|https?://[^\s/]+/(?!\[redacted\])[^\s]*\.(?:mp3|m3u8?|mpd)|rtsp://\S+/pattern)')


def unredacted(log):
    """Whole fixture URLs in log lines. System services that echo a session (MediaSessionService) and the rig's own UI
    dumps (AccessibilityNodeInfoDumper prints the on-screen channel subtitle) are not the app's log."""
    return sorted({m for line in log.splitlines() if 'MediaSessionService' not in line and 'AccessibilityNodeInfoDumper' not in line
                   for m in UNREDACTED.findall(line)})


# ---------------------------------------------------------------- device helpers
def nodes(xml_text):
    out = []
    for node in ET.fromstring(xml_text).iter('node'):
        if node.get('package') != PACKAGE:
            continue
        out.append({'id': node.get('resource-id', '').split('/')[-1], 'text': node.get('text', ''),
                    'desc': node.get('content-desc', ''), 'bounds': node.get('bounds', '')})
    return out


def center(node):
    left, top, right, bottom = map(int, re.findall(r'\d+', node['bounds']))
    return (left + right) // 2, (top + bottom) // 2


def first(xml_text, **match):
    for node in nodes(xml_text):
        if all(node.get(key) == value for key, value in match.items()):
            return node
    return None


def tap_node(adb, node):
    x, y = center(node)
    adb.shell(f'input tap {x} {y}')


def find_row(ui, title, tries=14):
    """Scroll the channel list until the row titled `title` sits fully inside it; returns (xml, node). A player left
    open by a failed step is closed first: the list is the screen every scene starts from."""
    if PLAYER in focus(ui.adb):
        cancel = first(ui.dump(), text='Cancel')  # the player's "Stream unavailable" dialog
        if cancel:
            tap_node(ui.adb, cancel)
            time.sleep(1)
        if PLAYER in focus(ui.adb):
            ui.adb.shell('input keyevent KEYCODE_BACK')
            time.sleep(2)
    for swipe in ('input swipe 540 1700 540 1100 300', 'input swipe 540 1000 540 1700 300'):
        for _ in range(tries):
            xml_text = ui.dump()
            node = first(xml_text, id='tvChannelTitle', text=title)
            if node and 760 < center(node)[1] < 2100:
                return xml_text, node
            ui.adb.shell(swipe)
            time.sleep(0.8)
    raise CouldNotVerify(f'the channel row "{title}" is not reachable in the list')


def wait_until(predicate, timeout_s, poll_s=0.25):
    """ms from the call to the first true predicate (an upper bound by one poll), or None on timeout."""
    started = time.monotonic()
    while time.monotonic() - started < timeout_s:
        value = predicate()
        if value:
            return round((time.monotonic() - started) * 1000.0, 1)
        time.sleep(poll_s)
    return None


def focus(adb):
    match = re.search(r'mCurrentFocus=Window\{[^ ]+ [^ ]+ ([^}]+)\}', adb.shell('dumpsys window | grep mCurrentFocus'))
    return match.group(1) if match else ''


def pid_of(adb):
    return adb.shell(f'pidof {PACKAGE}').strip()


def uid_of(adb):
    return parsers.parse_uid(adb.shell(f'pm list packages -U {PACKAGE}'), PACKAGE)


def holds(adb, uid, pid):
    """What the package holds right now: codecs, wake locks, audio focus, foreground services."""
    state = {}
    try:
        state['codecs'] = parsers.parse_codec_instances(adb.shell("dumpsys media.resource_manager | sed -n '1,/Events logs/p'"), pid) if pid else 0
    except parsers.Unreadable:
        state['codecs'] = None
    try:
        state['wakelocks'] = parsers.parse_wakelocks(adb.shell("dumpsys power | sed -n '/^Wake Locks: size=/,/^$/p'"), uid)
    except parsers.Unreadable:
        state['wakelocks'] = None
    focus_stack = adb.shell("dumpsys audio | sed -n '/Audio Focus stack entries/,/^$/p'")
    state['audio_focus'] = 1 if f'pack: {PACKAGE}' in focus_stack else 0
    try:
        state['fg_services'] = parsers.parse_foreground_services(adb.shell(f'dumpsys activity services {PACKAGE}'), PACKAGE)[1]
    except parsers.Unreadable:
        state['fg_services'] = None
    return state


def playing_sessions(adb):
    text = adb.shell('dumpsys media_session')
    return len(re.findall(r'package=' + re.escape(PACKAGE) + r'[\s\S]*?state=PlaybackState \{state=PLAYING', text))


class Toasts:
    """Accessibility events of the package, read through `uiautomator events` while no tree is dumped."""

    def __init__(self, adb):
        self.adb = adb
        self.lines = []
        self.process = None

    def __enter__(self):
        self.process = self.adb.popen('shell', 'uiautomator', 'events', stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        threading.Thread(target=self._read, daemon=True).start()
        time.sleep(1.5)  # the listener attaches before the input it times
        return self

    def _read(self):
        for raw in self.process.stdout:
            self.lines.append((time.monotonic(), raw.decode('utf-8', errors='replace').strip()))

    def __exit__(self, *exc):
        self.process.terminate()
        try:
            self.process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            self.process.kill()
        time.sleep(1.0)  # UiAutomation released before the next tree dump

    def toasts(self, since):
        """Toasts, and dialogs that open (a window-state change of a dialog class) - both are a message to the user."""
        found = []
        for stamp, line in list(self.lines):
            if stamp < since or f'PackageName: {PACKAGE}' not in line:
                continue
            toast = 'android.widget.Toast' in line
            dialog = 'TYPE_WINDOW_STATE_CHANGED' in line and re.search(r'ClassName: [\w.$]*Dialog', line)
            if toast or dialog:
                match = re.search(r'Text: \[(.*?)\];', line)
                text = match.group(1) if match else ''
                text = re.sub(r',\s*StreamsPlayer$', '', text)
                found.append({'ms': round((stamp - since) * 1000.0, 1), 'text': text, 'kind': 'toast' if toast else 'dialog'})
        return found

    def wait_toast(self, since, timeout_s):
        deadline = since + timeout_s
        while time.monotonic() < deadline:
            seen = self.toasts(since)
            if seen:
                return seen[0]
            time.sleep(0.2)
        return None


class Sampling:
    """The phase A sampler in a thread, so a scene acts while its counters are read."""

    def __init__(self, adb, run_dir, name, seconds, mode_package=PACKAGE, access='auto'):
        self.args = (adb, run_dir, name, mode_package, seconds, access)
        self.summary = None
        self.error = None
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.csv = os.path.join(run_dir, f'{name}.csv')

    def _run(self):
        try:
            self.summary = _sample(*self.args)
        except Exception as error:  # noqa: BLE001 - reported by join()
            self.error = error

    def start(self):
        self.thread.start()
        return self

    def join(self):
        self.thread.join()
        if self.error:
            raise CouldNotVerify(f'sampler {self.args[2]} failed: {self.error}')
        return self.summary


def check(name, ok, detail):
    return {'check': name, 'ok': bool(ok), 'detail': detail}


def outcome_verdict(label, checks, numbers=None):
    failed = [c for c in checks if not c['ok']]
    rows = [f'{"PASS " if c["ok"] else "FAIL "} {c["check"]}: {c["detail"]}' for c in checks]
    for key, value in (numbers or {}).items():
        rows.append(f'      {key} = {value}')
    if failed:
        return {'scene': label, 'code': FAIL, 'detail': f'{len(failed)} of {len(checks)} outcome checks failed: ' + ', '.join(c['check'] for c in failed), 'rows': rows}
    return {'scene': label, 'code': PASS, 'detail': f'{len(checks)} outcome checks passed', 'rows': rows}


# ---------------------------------------------------------------- scene steps
def import_playlist(adb, ui, url, timeout_s):
    """Overflow menu -> Import playlist -> URL -> Import; returns the first Toast (text, ms after the Import tap)."""
    xml_text = ui.dump()
    more = first(xml_text, desc='More options')
    if more is None:
        raise CouldNotVerify('the overflow menu is not on screen')
    tap_node(adb, more)
    time.sleep(1.2)
    item = first(ui.dump(), id='title', text='Import playlist (.m3u)')
    if item is None:
        raise CouldNotVerify('the menu has no "Import playlist (.m3u)" item')
    tap_node(adb, item)
    time.sleep(1.2)
    xml_text = ui.dump()
    field = first(xml_text, id='etPlaylistUrl')
    tap_node(adb, field)
    adb.shell(f"input text '{url}'")
    button = first(ui.dump(), id='button1')
    with Toasts(adb) as toasts:
        started = time.monotonic()
        tap_node(adb, button)
        toast = toasts.wait_toast(started, timeout_s)
    return toast


def set_search(adb, ui, text):
    xml_text = ui.dump()
    field = first(xml_text, id='etSearch')
    tap_node(adb, field)
    adb.shell('input keyevent KEYCODE_MOVE_END')
    adb.shell('input keyevent ' + ' '.join(['KEYCODE_DEL'] * 40))
    started = time.monotonic()
    adb.shell('input text ' + text.replace(' ', '%s'))
    return started


def search_latency(adb, ui, query, expect_prefix, timeout_s):
    """Type a query; ms until the first row of the list carries the expected title (the user's wait for the result)."""
    started = set_search(adb, ui, query)

    def rows_match():
        titles = [n['text'] for n in nodes(ui.dump()) if n['id'] == 'tvChannelTitle']
        return bool(titles) and all(t.startswith(expect_prefix) for t in titles)
    ms = wait_until(rows_match, timeout_s, poll_s=0.5)
    total = None if ms is None else round((time.monotonic() - started) * 1000.0, 1)
    adb.shell('input keyevent KEYCODE_BACK')  # the keyboard down
    time.sleep(1)
    return total


def radio_title(ui):
    node = first(ui.dump(), id='tvRadioTitle')
    return node['text'] if node else ''


def release_after(adb, uid, baseline_codecs, timeout_s=20):
    """ms until the package holds no more codecs than before playback, no wake lock, no audio focus, no fg service."""
    last = {}

    def released():
        last.update(holds(adb, uid, pid_of(adb)))
        return (last.get('codecs') or 0) <= baseline_codecs and not last.get('wakelocks') and not last.get('audio_focus') \
            and not last.get('fg_services')
    ms = wait_until(released, timeout_s, poll_s=0.5)
    return ms, dict(last)


def scene_radio(adb, ui, fixtures, run_dir, by_id, uid):
    """SC-07: play, metadata, pause/resume, switch, a server outage, stop - P1 waits, P2 recovery, P3 holds, P4 growth."""
    events, checks = {}, []
    _, row_a = find_row(ui, 'SP Audit Radio A')
    base_codecs = holds(adb, uid, pid_of(adb)).get('codecs') or 0
    started = time.monotonic()
    tap_node(adb, row_a)
    events['radio_bar_ms'] = wait_until(lambda: radio_title(ui).startswith('SP Audit Radio A'), 20)
    events['icy_title_ms'] = wait_until(lambda: 'Track' in radio_title(ui), 30)
    events['radio_start_ms'] = round((time.monotonic() - started) * 1000.0, 1)
    checks.append(check('ICY metadata shown', events['icy_title_ms'] is not None, radio_title(ui)))
    time.sleep(3)
    playing = holds(adb, uid, pid_of(adb))
    events['holds_playing'] = playing
    events['notification'] = 'com.sza.streamsplayer' in adb.shell('dumpsys notification --noredact | grep -E "pkg=com.sza.streamsplayer" | head -3')
    checks.append(check('one media notification while playing', events['notification'], 'dumpsys notification'))
    checks.append(check('wake lock held only while playing (partial)', (playing.get('wakelocks') or 0) <= 2, f'{playing.get("wakelocks")} wake lock(s)'))
    # P1 main thread during playback
    events['ping'] = ping.ping(adb, PACKAGE, 20, 0.5)
    seconds = max(30, int(180 * time_scale))
    checks.append(check('main-Looper queue wait during playback within the B-3 stall budget (700 ms)', events['ping']['max_ms'] <= 700,
                        f'ping max {events["ping"]["max_ms"]:g} ms, p95 {events["ping"]["p95_ms"]:g} ms'))
    sampling = Sampling(adb, run_dir, 'SC-07-playing', seconds).start()
    time.sleep(seconds - 10)
    summary = sampling.join()
    events['sampler_unavailable'] = summary['unavailable']
    # pause / resume
    xml_text = ui.dump()
    button = first(xml_text, id='btnRadioPlayPause')
    started = time.monotonic()
    tap_node(adb, button)
    events['pause_ms'] = wait_until(lambda: (first(ui.dump(), id='btnRadioPlayPause') or {}).get('desc') == 'Play', 10)
    events['paused_holds'] = holds(adb, uid, pid_of(adb))
    tap_node(adb, first(ui.dump(), id='btnRadioPlayPause'))
    events['resume_ms'] = wait_until(lambda: (first(ui.dump(), id='btnRadioPlayPause') or {}).get('desc') == 'Pause', 10)
    # switch A -> B
    _, row_b = find_row(ui, 'SP Audit Radio B')
    tap_node(adb, row_b)
    events['switch_ms'] = wait_until(lambda: radio_title(ui).startswith('SP Audit Radio B'), 20)
    time.sleep(4)
    events['open_streams_after_switch'] = fixtures.open_radio
    checks.append(check('switch leaves one stream open at the server', fixtures.open_radio == 1, f'{fixtures.open_radio} open'))
    # P2: the server drops the stream for 8 s, the player must come back by itself
    outage_at = time.time()
    fixtures.outage(8)
    time.sleep(9)
    events['recover_after_outage_ms'] = wait_until(
        lambda: any(r['status'] == 100 for r in fixtures.requests(since=outage_at + 8, prefix='/radio/')), 60)
    retries = [r for r in fixtures.requests(since=outage_at, prefix='/radio/') if r['status'] == 503]
    events['outage_retries_503'] = len(retries)
    checks.append(check('playback resumes after an 8 s server outage without a user action', events['recover_after_outage_ms'] is not None,
                        f'{len(retries)} retry request(s) answered 503 during the outage'))
    time.sleep(5)
    events['bar_after_outage'] = radio_title(ui)
    events['playing_after_outage'] = playing_sessions(adb)
    checks.append(check('the player is still playing after the outage', events['playing_after_outage'] == 1,
                        f'bar {events["bar_after_outage"]!r}, {events["playing_after_outage"]} PLAYING session(s)'))
    if not events['playing_after_outage']:  # bring playback back so the stop below measures a release
        _, row_a = find_row(ui, 'SP Audit Radio A')
        tap_node(adb, row_a)
        wait_until(lambda: 'Track' in radio_title(ui), 30)
        time.sleep(3)
    # stop: release of codec, wake lock, audio focus, foreground service (B-6)
    close = first(ui.dump(), id='btnRadioClose')
    if close is None:
        raise CouldNotVerify('the radio bar has no Close button to stop playback with')
    tap_node(adb, close)
    events['codec_release_ms'], events['holds_after_stop'] = release_after(adb, uid, base_codecs)
    checks.append(check('stop releases codec, wake lock, audio focus and the foreground service', events['codec_release_ms'] is not None,
                        json.dumps(events['holds_after_stop'])))
    path = os.path.join(run_dir, 'SC-07-events.json')
    _write_json(path, events)
    verdicts = [_judge_scene('SC-07 P4 radio playing (sampler)', [by_id[i] for i in ('B-4a', 'B-5a', 'B-5b', 'B-7a')], run_dir, 'playing', 60,
                             sampler_csv=sampling.csv, control_csv=os.path.join(run_dir, 'control.csv')),
                _judge_scene('SC-07 P3 release after stop', [by_id['B-6']], run_dir, 'playing', 0,
                             events=path if events['codec_release_ms'] is not None else None)]
    verdicts.append(outcome_verdict('SC-07 P1/P2/P3 radio outcomes', checks, {k: events[k] for k in (
        'radio_bar_ms', 'icy_title_ms', 'pause_ms', 'resume_ms', 'switch_ms', 'recover_after_outage_ms', 'codec_release_ms')}))
    return verdicts


def live_edge_lag(fixtures, since):
    """Segments between the newest one the encoder listed and the newest one the player fetched."""
    index = open(os.path.join(fixtures.www, 'hls', 'index.m3u8'), encoding='utf-8').read()
    listed = [int(m) for m in re.findall(r'index(\d+)\.ts', index)]
    fetched = [int(m.group(1)) for r in fixtures.requests(since=since, prefix='/hls/') for m in [re.search(r'index(\d+)\.ts', r['path'])] if m]
    if not listed or not fetched:
        return None
    return max(listed) - max(fetched)


def scene_video(adb, ui, fixtures, run_dir, by_id, uid, scene, title, prefix, seconds, settings):
    events, checks = {}, []
    _, row = find_row(ui, title)
    base_codecs = holds(adb, uid, pid_of(adb)).get('codecs') or 0
    since = time.time()
    started = time.monotonic()
    tap_node(adb, row)
    events['player_focus_ms'] = wait_until(lambda: PLAYER in focus(adb), 20)
    events['first_media_request_ms'] = wait_until(lambda: bool(fixtures.requests(since=since, prefix=prefix)) or prefix == 'rtsp', 20)
    events['codec_open_ms'] = wait_until(lambda: (holds(adb, uid, pid_of(adb)).get('codecs') or 0) > base_codecs, 30)
    events['open_total_ms'] = round((time.monotonic() - started) * 1000.0, 1)
    checks.append(check('player opens and a decoder starts', events['codec_open_ms'] is not None, f'focus {focus(adb)}'))
    collector = framestats.FrameCollector(adb, PACKAGE)
    collector.reset()
    slug = re.sub(r'[^A-Za-z0-9]+', '-', title.replace('SP Audit ', '')).strip('-')
    sampling = Sampling(adb, run_dir, f'{scene}-{slug}-playing', seconds).start()
    events['ping'] = ping.ping(adb, PACKAGE, 20, 0.5)
    for _ in range(int(seconds // 5) - 1):
        time.sleep(5)
        collector.poll()
    sampling.join()
    frames_path = os.path.join(run_dir, f'{scene}-{slug}-frames.json')
    try:
        _write_json(frames_path, collector.metrics())
    except CouldNotVerify as error:  # the picture is a SurfaceView: the UI thread drew nothing in the window
        events['frames'] = str(error)
        frames_path = None
    ping_max = events['ping']['max_ms']
    checks.append(check('main-Looper queue wait during playback within the B-3 stall budget (700 ms)', ping_max <= 700,
                        f'ping max {ping_max:g} ms, p95 {events["ping"]["p95_ms"]:g} ms over {events["ping"]["received"]} pongs'))
    events['holds_playing'] = holds(adb, uid, pid_of(adb))
    if prefix == '/hls/':
        events['live_edge_lag_segments'] = live_edge_lag(fixtures, since)
    # rotation: landscape and back, one decoder before and after
    before = events['holds_playing'].get('codecs')
    settings('system', 'accelerometer_rotation', '0')
    settings('system', 'user_rotation', '1')
    time.sleep(4)
    rotated = holds(adb, uid, pid_of(adb))
    settings('system', 'user_rotation', '0')
    time.sleep(4)
    events['codecs_rotated'] = rotated.get('codecs')
    checks.append(check('rotation keeps the player and one decoder', PLAYER in focus(adb) and rotated.get('codecs') == before,
                        f'codecs {before} -> {rotated.get("codecs")}'))
    adb.shell('input keyevent KEYCODE_BACK')
    events['close_focus_ms'] = wait_until(lambda: PLAYER not in focus(adb), 10)
    events['codec_release_ms'], events['holds_after_close'] = release_after(adb, uid, base_codecs)
    checks.append(check('close releases decoder, wake lock, audio focus', events['codec_release_ms'] is not None, json.dumps(events['holds_after_close'])))
    if prefix == 'rtsp':
        log = open(os.path.join(fixtures.root, 'mediamtx.log'), encoding='utf-8').read()
        events['rtsp_readers_opened'] = log.count('is reading from path')
        events['rtsp_sessions_closed'] = len(re.findall(r'\[session [0-9a-f]+\] destroyed', log))
    path = os.path.join(run_dir, f'{scene}-{slug}-events.json')
    _write_json(path, events)
    verdicts = ([_judge_scene(f'{scene} P1 frames ({title})', [by_id['B-3']], run_dir, 'playing', 0, frames=frames_path)] if frames_path else []) + [
                _judge_scene(f'{scene} P4 playing ({title})', [by_id[i] for i in ('B-4a', 'B-5a', 'B-5b')], run_dir, 'playing', 30,
                             sampler_csv=sampling.csv, control_csv=os.path.join(run_dir, 'control.csv')),
                _judge_scene(f'{scene} P3 release ({title})', [by_id['B-6']], run_dir, 'playing', 0,
                             events=path if events['codec_release_ms'] is not None else None),
                outcome_verdict(f'{scene} outcomes ({title})', checks, {k: events.get(k) for k in (
                    'player_focus_ms', 'codec_open_ms', 'open_total_ms', 'live_edge_lag_segments', 'close_focus_ms', 'codec_release_ms')})]
    return verdicts


def scene_failures(adb, ui, fixtures, run_dir, by_id, uid):
    """SC-21: every fault channel - the message, its wording, its time, the log line, and a retry without restart."""
    results, checks = [], []
    tail = sampler_module.LogTail(adb, os.path.join(run_dir, 'SC-21.logcat.txt'))
    tail.start()
    pid_before = pid_of(adb)
    try:
        for title, url, fault in streams.FAULTS:
            _, row = find_row(ui, title)
            kind = 'video' if ('hls' in url or url.startswith('rtsp')) else 'radio'
            with Toasts(adb) as toasts:
                started = time.monotonic()
                tap_node(adb, row)
                toast = toasts.wait_toast(started, 60)
            entry = {'title': title, 'fault': fault, 'kind': kind, 'toast': toast}
            if kind == 'video':
                entry['focus'] = focus(adb)
                screen = ui.dump()
                entry['screen_texts'] = [n['text'] for n in nodes(screen) if n['text']][:12]
                if PLAYER in entry['focus']:
                    adb.shell('input keyevent KEYCODE_BACK')
                    time.sleep(2)
            else:
                close = first(ui.dump(), id='btnRadioClose')
                if close:
                    tap_node(adb, close)
                    time.sleep(1.5)
            message = (toast or {}).get('text', '') or ' '.join(entry.get('screen_texts', [])[-3:])
            entry['raw_terms'] = RAW_ERROR.findall(message)
            # retry: a healthy channel must play right after, with no restart
            _, healthy = find_row(ui, 'SP Audit Radio A')
            tap_node(adb, healthy)
            entry['retry_ok_ms'] = wait_until(lambda: 'Track' in radio_title(ui), 30)
            close = first(ui.dump(), id='btnRadioClose')
            if close:
                tap_node(adb, close)
                time.sleep(1.5)
            results.append(entry)
            print(f'  fault {fault}: toast {toast} retry {entry["retry_ok_ms"]} ms')
        # no network at all
        adb.shell('svc wifi disable')
        adb.shell('svc data disable')
        time.sleep(3)
        try:
            _, row = find_row(ui, 'SP Audit Radio A')
            with Toasts(adb) as toasts:
                started = time.monotonic()
                tap_node(adb, row)
                toast = toasts.wait_toast(started, 60)
            entry = {'title': 'SP Audit Radio A', 'fault': 'no network', 'kind': 'radio', 'toast': toast,
                     'raw_terms': RAW_ERROR.findall((toast or {}).get('text', ''))}
            close = first(ui.dump(), id='btnRadioClose')
            if close:
                tap_node(adb, close)
        finally:
            adb.shell('svc wifi enable')
            adb.shell('svc data enable')
        time.sleep(8)
        _, healthy = find_row(ui, 'SP Audit Radio A')
        tap_node(adb, healthy)
        entry['retry_ok_ms'] = wait_until(lambda: 'Track' in radio_title(ui), 45)
        close = first(ui.dump(), id='btnRadioClose')
        if close:
            tap_node(adb, close)
            time.sleep(1.5)
        results.append(entry)
        print(f'  fault no network: toast {toast} retry {entry["retry_ok_ms"]} ms')
    finally:
        tail.stop()
    log = open(os.path.join(run_dir, 'SC-21.logcat.txt'), encoding='utf-8', errors='replace').read()
    own = [line for line in log.splitlines() if PACKAGE in line or 'streamsplayer' in line.lower() or 'Stream' in line]
    leaks = unredacted(log)
    surfaced = [r['toast']['ms'] for r in results if r.get('toast')]
    events = {'faults': results, 'error_surface_ms': surfaced, 'floor_ms': 300.0, 'unredacted_urls': leaks[:20],
              'crashed': pid_of(adb) != pid_before, 'log_lines': len(own)}
    path = os.path.join(run_dir, 'SC-21-events.json')
    _write_json(path, events)
    for r in results:
        checks.append(check(f'{r["fault"]}: a message is shown', bool(r.get('toast')) or bool(r.get('screen_texts')),
                            (r.get('toast') or {}).get('text') or '; '.join(r.get('screen_texts', [])[-3:])))
        checks.append(check(f'{r["fault"]}: no raw class name or address in the message', not r['raw_terms'], ', '.join(r['raw_terms']) or 'clean'))
        checks.append(check(f'{r["fault"]}: the next channel plays without a restart', r.get('retry_ok_ms') is not None, f'{r.get("retry_ok_ms")} ms'))
    checks.append(check('no whole fixture URL in the log', not leaks, '; '.join(leaks[:5]) or 'none'))
    checks.append(check('the process survived every fault', not events['crashed'], f'pid {pid_before} -> {pid_of(adb)}'))
    return [_judge_scene('SC-21 P1 error surfacing (B-10)', [by_id['B-10']], run_dir, 'playing', 0, events=path),
            outcome_verdict('SC-21 P2 wrapped errors', checks)]


def scene_storms(adb, ui, fixtures, run_dir, by_id, uid):
    """SC-19: channel-switch storm, rapid pin taps, a network flap under playback; log volume over the storm."""
    events, checks = {}, []
    sampling = Sampling(adb, run_dir, 'SC-19-storm', 150).start()
    time.sleep(10)
    xml_text, row_a = find_row(ui, 'SP Audit Radio A')
    row_b = first(xml_text, id='tvChannelTitle', text='SP Audit Radio B')
    if row_b is None:
        raise CouldNotVerify('Radio A and Radio B rows are not on one screen')
    storm_at = time.time()
    for index in range(20):
        tap_node(adb, row_a if index % 2 == 0 else row_b)
        time.sleep(0.3)
    time.sleep(12)
    events['open_streams_after_storm'] = fixtures.open_radio
    events['playing_sessions_after_storm'] = playing_sessions(adb)
    events['storm_requests'] = len([r for r in fixtures.requests(since=storm_at, prefix='/radio/') if r['status'] == 100])
    events['title_after_storm'] = radio_title(ui)
    checks.append(check('one stream open after a 20-tap switch storm', fixtures.open_radio == 1, f'{fixtures.open_radio} open, {events["storm_requests"]} streams opened'))
    checks.append(check('one playing session after the storm', events['playing_sessions_after_storm'] == 1, f'{events["playing_sessions_after_storm"]} PLAYING'))
    checks.append(check('the last tapped channel plays', events['title_after_storm'].startswith('SP Audit Radio B'), events['title_after_storm']))
    # network flap: three 5 s outages of the whole network while Radio B plays
    flap_at = time.time()
    for _ in range(3):
        adb.shell('svc wifi disable')
        adb.shell('svc data disable')
        time.sleep(5)
        adb.shell('svc wifi enable')
        adb.shell('svc data enable')
        time.sleep(8)
    last_flap_end = time.time() - 8
    events['flap_recover_ms'] = wait_until(
        lambda: any(r['status'] == 100 for r in fixtures.requests(since=last_flap_end, prefix='/radio/')), 60)
    time.sleep(3)
    events['flap_streams_opened'] = len([r for r in fixtures.requests(since=flap_at, prefix='/radio/') if r['status'] == 100])
    events['flap_playing_sessions'] = playing_sessions(adb)
    events['flap_server_open'] = fixtures.open_radio  # host-side view: a connection cut under slirp can stay open here
    checks.append(check('playback comes back after the last of three network flaps, one player',
                        events['flap_recover_ms'] is not None and events['flap_playing_sessions'] == 1,
                        f'{events["flap_streams_opened"]} stream(s) opened over the flaps, new one after the last: '
                        f'{events["flap_recover_ms"]} ms, {events["flap_playing_sessions"]} PLAYING'))
    close = first(ui.dump(), id='btnRadioClose')
    if close:
        tap_node(adb, close)
        time.sleep(2)
    # rapid pin taps: 20 taps on one row's pin, an even count must leave it as it was
    xml_text, row = find_row(ui, 'SP Audit DASH')
    pins = [n for n in nodes(xml_text) if n['id'] == 'btnPin']
    pin = min(pins, key=lambda n: abs(center(n)[1] - center(row)[1]))
    before = pin['desc']
    for _ in range(20):
        tap_node(adb, pin)
        time.sleep(0.15)
    time.sleep(4)
    xml_text, row = find_row(ui, 'SP Audit DASH')
    pins = [n for n in nodes(xml_text) if n['id'] == 'btnPin']
    after = min(pins, key=lambda n: abs(center(n)[1] - center(row)[1]))['desc']
    events['pin_before'], events['pin_after'] = before, after
    checks.append(check('20 rapid pin taps end where they started', before == after, f'{before} -> {after}'))
    summary = sampling.join()
    rows = sampler_module.read_csv(sampling.csv)
    logs = [v for v in rows['log_bytes'] if v is not None]
    seconds = rows['elapsed_s'][-1] - rows['elapsed_s'][0] if rows['elapsed_s'] else 0
    events['log_bytes_per_min'] = round((logs[-1] - logs[0]) / (seconds / 60.0)) if len(logs) > 1 and seconds else None
    checks.append(check('log volume over the storm <= 2 MiB/min (B-8)', events['log_bytes_per_min'] is not None and events['log_bytes_per_min'] <= 2 * 1024 * 1024,
                        f'{events["log_bytes_per_min"]} B/min (device-wide logcat stream, an upper bound for the app)'))
    path = os.path.join(run_dir, 'SC-19-events.json')
    _write_json(path, events)
    return [_judge_scene('SC-19 P4 storm (sampler)', [by_id[i] for i in ('B-5b',)], run_dir, 'storm', 10,
                         sampler_csv=sampling.csv, control_csv=os.path.join(run_dir, 'control.csv')),
            outcome_verdict('SC-19 P1-P4 storm outcomes', checks, {k: events.get(k) for k in ('flap_recover_ms', 'log_bytes_per_min', 'storm_requests')})]


def scene_big_inputs(adb, ui, fixtures, run_dir, by_id, uid):
    """SC-10 and SC-20 (with SC-06 P2-P4): the 100,000-entry playlist, the malformed, oversized, unreachable and
    long-name playlists; the catalog at its new size - search, scroll, idle memory."""
    events, checks = {}, []
    pid_before = pid_of(adb)
    base = f'http://{streams.HOST}:{streams.HTTP_PORT}'
    for name, url in (('malformed', f'{base}/malformed.m3u'), ('oversized', f'{base}/oversized.m3u'),
                      ('unreachable', f'http://{streams.DEAD_HOST}/list.m3u'), ('http_404', f'{base}/status/404/list.m3u')):
        toast = import_playlist(adb, ui, url, 150)
        events[f'import_{name}'] = toast
        text = (toast or {}).get('text', '')
        checks.append(check(f'import {name}: a message is shown', bool(toast), text or 'none within 150 s'))
        checks.append(check(f'import {name}: no raw class name, address or untranslated internal reason', not RAW_ERROR.findall(text)
                            and not re.search(r'(exceeds the|download error|parse error|failed to connect|timeout)', text, re.I), text))
        time.sleep(2)
    sampling = Sampling(adb, run_dir, 'SC-10-import', 240).start()
    time.sleep(10)
    toast = import_playlist(adb, ui, f'{base}/big-100000.m3u', 220)
    events['import_big'] = toast
    sampling.join()
    pss = [v for v in sampler_module.read_csv(sampling.csv)['pss_kib'] if v is not None]
    events['import_pss_kib_min_max'] = [min(pss), max(pss)] if pss else None
    checks.append(check('the 100,000-entry playlist imports and says how many', bool(toast) and bool(re.search(r'\d', toast.get('text', ''))),
                        (toast or {}).get('text', 'no message within 220 s')))
    events['import_big_ms'] = (toast or {}).get('ms')
    toast = import_playlist(adb, ui, f'{base}/long-names.m3u', 60)
    events['import_long_names'] = toast
    checks.append(check('long and unicode names import', bool(toast), (toast or {}).get('text', 'none')))
    # the list at its new size: search latency for a narrow query, the empty message, scroll frames
    events['search_big_ms'] = search_latency(adb, ui, 'SP Audit Emoji', 'SP Audit Emoji', 300)
    set_search(adb, ui, 'zzzz-no-such-channel')
    events['empty_message_ms'] = wait_until(lambda: first(ui.dump(), id='emptyTextView') is not None, 300, 1.0)
    empty = first(ui.dump(), id='emptyTextView')
    events['empty_message'] = (empty or {}).get('text')
    stale = [n['text'] for n in nodes(ui.dump()) if n['id'] == 'tvChannelTitle']
    events['rows_beside_empty_message'] = len(stale)
    checks.append(check('the empty-result message is human and alone on screen', bool(empty) and not stale,
                        f'{events["empty_message"]!r} with {len(stale)} stale row(s) beside it'))
    adb.shell('input keyevent KEYCODE_BACK')
    clear = first(ui.dump(), id='btnBannerClearFilters')
    if clear:
        tap_node(adb, clear)
    events['clear_filter_ms'] = wait_until(lambda: first(ui.dump(), id='emptyTextView') is None and
                                           any(n['id'] == 'tvChannelTitle' for n in nodes(ui.dump())), 300, 1.0)
    collector = framestats.FrameCollector(adb, PACKAGE)
    collector.reset()
    for step in range(8):
        adb.shell('input swipe 540 1700 540 700 250' if step % 2 == 0 else 'input swipe 540 700 540 1700 250')
        collector.poll()
    frames = collector.metrics()
    frames_path = os.path.join(run_dir, 'SC-20-frames.json')
    _write_json(frames_path, frames)
    idle = Sampling(adb, run_dir, 'SC-06-idle-big', 120).start()
    idle.join()
    events['crashed'] = pid_of(adb) != pid_before
    checks.append(check('the process survived every big input', not events['crashed'], f'pid {pid_before} -> {pid_of(adb)}'))
    path = os.path.join(run_dir, 'SC-20-events.json')
    _write_json(path, events)
    latency_path = os.path.join(run_dir, 'SC-20-search-latency.json')
    _write_json(latency_path, {'samples_ms': [v for v in (events['search_big_ms'],) if v is not None], 'floor_ms': 600.0})
    return [_judge_scene('SC-20 P1 search and scroll on the grown catalog', [by_id['B-2'], by_id['B-3']], run_dir, 'playing', 0,
                         latency=latency_path if events['search_big_ms'] is not None else None, frames=frames_path),
            _judge_scene('SC-10 P4 big import (sampler)', [by_id['B-5b']], run_dir, 'playing', 0, sampler_csv=sampling.csv),
            _judge_scene('SC-06 P3/P4 idle on the grown catalog', [by_id[i] for i in ('B-5a', 'B-7b')], run_dir, 'idle', 30,
                         sampler_csv=idle.csv, control_csv=os.path.join(run_dir, 'control.csv')),
            outcome_verdict('SC-10/SC-20/SC-06 big-input outcomes', checks, {k: events.get(k) for k in (
                'import_big_ms', 'import_pss_kib_min_max', 'search_big_ms', 'empty_message_ms', 'clear_filter_ms')})]


# ---------------------------------------------------------------- the session
def run_phase_b(serial, apk, run_id, repo, control_s=120, command='', only=None):
    adb = Adb(serial, find_adb(repo)).require_device()
    run_dir = os.path.join(repo, 'temp', 'S0025', run_id)
    os.makedirs(run_dir, exist_ok=True)
    by_id = {r['id']: r for r in evaluate_module.load_budgets(os.path.join(repo, 'tools', 'audit', 'budgets.json'))}
    passed = instruments.live_selftest(adb)
    print(f'instrument self-test: {len(passed)} live checks passed')
    _write_json(os.path.join(run_dir, 'environment.json'), _environment(adb, apk, repo, command))
    big = os.path.join(repo, 'temp', 'S0025', 'inputs', 'big-100000.m3u')
    if not os.path.isfile(big):
        raise CouldNotVerify('temp/S0025/inputs/big-100000.m3u is missing (run: python tools/audit/rig.py big-m3u --out temp/S0025/inputs/big-100000.m3u)')
    fixtures = streams.FixtureServers(repo, run_dir).start(big_m3u=big)
    verdicts = []
    try:
        fixture_checks = fixtures.selftest()
        print('fixture self-test: ' + '; '.join(fixture_checks))
        ui = latency.Ui(adb, PACKAGE)

        def steps(current):
            current.put_setting('system', 'screen_off_timeout', '2147483647')
            adb.shell('input keyevent KEYCODE_WAKEUP')
            adb.shell('wm dismiss-keyguard')
            adb.shell(f'am force-stop {PACKAGE}')
            time.sleep(2)
            print(f'control run: {control_s} s on {CONTROL_PACKAGE}')
            _sample(adb, run_dir, 'control', CONTROL_PACKAGE, control_s, 'shell')
            adb.shell(f'am start -W -n {ACTIVITY}')
            time.sleep(5)
            uid = uid_of(adb)
            # SC-10 small: the fixture playlist itself, then the search that brings its rows on screen (SC-06 P1 on
            # the real catalog: the user's wait for a search result)
            toast = import_playlist(adb, ui, f'http://{streams.HOST}:{streams.HTTP_PORT}/fixtures.m3u', 60)
            print(f'fixture import: {toast}')
            if not toast:
                raise CouldNotVerify('the fixture playlist import showed no result message')
            search_ms = search_latency(adb, ui, 'SP Audit', 'SP Audit', 300)
            print(f'search on the real catalog: {search_ms} ms')
            latency_path = os.path.join(run_dir, 'SC-06-search-latency.json')
            _write_json(latency_path, {'samples_ms': [search_ms] if search_ms else [], 'floor_ms': 600.0,
                                       'note': 'query typed to the first list whose rows all match; one sample, a tree read per 0.5 s'})
            if search_ms is None:
                raise CouldNotVerify('the search for the fixture rows did not settle within 300 s')
            _write_json(os.path.join(run_dir, 'SC-10-small-events.json'), {'import_fixture_toast': toast, 'search_ms': search_ms})
            verdicts.append(_judge_scene('SC-06 P1 search on the real catalog (B-2)', [by_id['B-2']], run_dir, 'playing', 0, latency=latency_path))
            scenes = [('SC-07', lambda: scene_radio(adb, ui, fixtures, run_dir, by_id, uid)),
                      ('SC-08', lambda: scene_video(adb, ui, fixtures, run_dir, by_id, uid, 'SC-08', 'SP Audit HLS', '/hls/', 120, current.put_setting)
                       + scene_video(adb, ui, fixtures, run_dir, by_id, uid, 'SC-08', 'SP Audit DASH', '/dash/', 60, current.put_setting)),
                      ('SC-09', lambda: scene_video(adb, ui, fixtures, run_dir, by_id, uid, 'SC-09', 'SP Audit RTSP', 'rtsp', 90, current.put_setting)),
                      ('SC-21', lambda: scene_failures(adb, ui, fixtures, run_dir, by_id, uid)),
                      ('SC-19', lambda: scene_storms(adb, ui, fixtures, run_dir, by_id, uid)),
                      ('SC-20', lambda: scene_big_inputs(adb, ui, fixtures, run_dir, by_id, uid))]
            for name, scene in scenes:
                if only and name not in only:
                    continue
                print(f'=== {name}')
                try:
                    verdicts.extend(scene())
                except Exception as error:  # noqa: BLE001 - one scene's failure is recorded, the session goes on
                    error = f'{type(error).__name__}: {error}' if not isinstance(error, CouldNotVerify) else str(error)
                    verdicts.append({'scene': name, 'code': COULD_NOT_VERIFY, 'detail': error, 'rows': []})
                    print(f'  {name} could not verify: {error}')
                    adb.shell(f'am start -n {ACTIVITY}')
                    time.sleep(3)
                _write_json(os.path.join(run_dir, 'verdicts.partial.json'), {'verdicts': verdicts})
            adb.shell(f'am force-stop {PACKAGE}')
            return PASS, 'scenes ran'

        try:
            session.run_session(session.AdbDevice(adb), run_dir, PACKAGE, steps)
            rollback = 'rollback proved: snapshot restored, package list and settings digests equal the backup'
        except CouldNotVerify as error:
            verdicts.append({'scene': 'session', 'code': COULD_NOT_VERIFY, 'detail': str(error), 'rows': []})
            rollback = f'rollback or session did not verify: {error}'
    finally:
        fixtures.stop()
    _write_json(os.path.join(run_dir, 'verdicts.json'), {'verdicts': verdicts, 'rollback': rollback})
    for entry in verdicts:
        print(f'--- {entry["scene"]}')
        for line in entry['rows']:
            print(f'    {line}')
        print(f'    => exit {entry["code"]}: {entry["detail"]}')
    print(rollback)
    codes = [v['code'] for v in verdicts]
    if FAIL in codes:
        return FAIL, f'{codes.count(FAIL)} scene check(s) failed or over budget; run folder temp/S0025/{run_id}'
    if COULD_NOT_VERIFY in codes:
        return COULD_NOT_VERIFY, f'{codes.count(COULD_NOT_VERIFY)} scene check(s) undecided or invalid; run folder temp/S0025/{run_id}'
    return PASS, f'{len(codes)} scene check(s) within budget; run folder temp/S0025/{run_id}'
