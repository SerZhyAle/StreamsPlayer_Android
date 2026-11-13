"""Loopback stream fixtures of phases B-D (S0025 decision 14.8.2): everything the media scenes play is served from
this host, which the emulator reaches at 10.0.2.2 (the host loopback alias), so no scene touches an external network
(section 2 item 4). ffmpeg and mediamtx come from the tool cache (toolcache.py).

What runs while a FixtureServers context is open:
  http  127.0.0.1:8770  fixtures.m3u, the big and malformed playlists, /radio/<name>.mp3 (ICY radio paced at its
                        bitrate, metadata every 16,000 bytes when the client asks), /hls/ and /dash/ (a live encoder
                        writes them), and the fault endpoints: /status/<code>/.., /slow/.. (25 s before the first
                        byte), /hang/.. (never answers)
  https 127.0.0.1:8771  the same tree behind a self-signed certificate generated per run (never committed)
  rtsp  127.0.0.1:8554  mediamtx, path /pattern published by ffmpeg
Every HTTP request is logged (time, path, status, bytes) to <run>/fixture-requests.jsonl - the retry-policy evidence
of the storm and failure scenes. Ports 8799 (HTTP) and 8555 (RTSP) are left closed on purpose: the wrong-port faults.
"""
import http.server
import json
import os
import re
import shutil
import socket
import socketserver
import ssl
import subprocess
import threading
import time
import urllib.request

from . import toolcache
from .verdict import CouldNotVerify

HOST = '10.0.2.2'
HTTP_PORT, HTTPS_PORT, RTSP_PORT = 8770, 8771, 8554
CLOSED_HTTP_PORT, CLOSED_RTSP_PORT = 8799, 8555
DEAD_HOST = '192.0.2.1'  # TEST-NET-1 (RFC 5737): never routed, a connect attempt times out
RADIO_BITRATE = 128000
ICY_METAINT = 16000

HEALTHY = [
    ('SP Audit Radio A', f'http://{HOST}:{HTTP_PORT}/radio/a.mp3'),
    ('SP Audit Radio B', f'http://{HOST}:{HTTP_PORT}/radio/b.mp3'),
    ('SP Audit HLS', f'http://{HOST}:{HTTP_PORT}/hls/index.m3u8'),
    ('SP Audit DASH', f'http://{HOST}:{HTTP_PORT}/dash/manifest.mpd'),
    ('SP Audit RTSP', f'rtsp://{HOST}:{RTSP_PORT}/pattern'),
]
FAULTS = [
    ('SP Audit Fail Dead Host', f'http://{DEAD_HOST}/radio/a.mp3', 'dead host'),
    ('SP Audit Fail Wrong Port', f'http://{HOST}:{CLOSED_HTTP_PORT}/radio/a.mp3', 'wrong port'),
    ('SP Audit Fail Slow Host', f'http://{HOST}:{HTTP_PORT}/slow/radio/a.mp3', 'slow host'),
    ('SP Audit Fail Hang', f'http://{HOST}:{HTTP_PORT}/hang/radio/a.mp3', 'host never answers'),
    ('SP Audit Fail 404', f'http://{HOST}:{HTTP_PORT}/status/404/radio/a.mp3', 'HTTP 404'),
    ('SP Audit Fail 403', f'http://{HOST}:{HTTP_PORT}/status/403/radio/a.mp3', 'HTTP 403'),
    ('SP Audit Fail 500', f'http://{HOST}:{HTTP_PORT}/status/500/radio/a.mp3', 'HTTP 500'),
    ('SP Audit Fail TLS', f'https://{HOST}:{HTTPS_PORT}/radio/a.mp3', 'self-signed certificate'),
    ('SP Audit Fail Video 404', f'http://{HOST}:{HTTP_PORT}/status/404/hls/index.m3u8', 'HLS manifest 404'),
    ('SP Audit Fail RTSP Port', f'rtsp://{HOST}:{CLOSED_RTSP_PORT}/pattern', 'RTSP wrong port'),
]
LONG_NAMES = [
    ('SP Audit Long ' + 'Ж' * 180 + ' ' + '漢字' * 40, f'http://{HOST}:{HTTP_PORT}/radio/a.mp3?long=1'),
    ('SP Audit Emoji 📻🎶 Ünïcödé ÀÉÎÕÜ Ελληνικά עברית العربية', f'http://{HOST}:{HTTP_PORT}/radio/b.mp3?uni=1'),
    ('SP Audit RTL ‮evil‬ name', f'http://{HOST}:{HTTP_PORT}/radio/a.mp3?rtl=1'),
]


def playlist_text(entries):
    lines = ['#EXTM3U']
    for title, url in entries:
        lines.append(f'#EXTINF:-1,{title}')
        lines.append(url)
    return '\n'.join(lines) + '\n'


class _Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    server_version = 'SpAuditFixture/1'

    def log_message(self, *args):  # the request log below is the record; stderr stays quiet
        pass

    def _log(self, status, sent):
        self.server.fixtures.record({'t': round(time.time(), 3), 'scheme': self.server.scheme, 'path': self.path,
                                     'status': status, 'bytes': sent, 'icy': self.headers.get('Icy-MetaData', '')})

    def do_HEAD(self):
        self.do_GET(head=True)

    def do_GET(self, head=False):
        fixtures = self.server.fixtures
        path = self.path.split('?', 1)[0]
        try:
            if path.startswith('/status/'):
                code = int(path.split('/')[2])
                body = f'fixture status {code}\n'.encode()
                self.send_response(code)
                self.send_header('Content-Type', 'text/plain')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                if not head:
                    self.wfile.write(body)
                self._log(code, len(body))
                return
            if path.startswith('/hang/'):
                self._log(0, 0)
                fixtures.stopping.wait(180)
                return
            if path.startswith('/slow/'):
                self._log(-1, 0)
                if fixtures.stopping.wait(25):
                    return
                path = path[len('/slow'):]
            if path.startswith('/radio/'):
                self._radio(path, head)
                return
            local = os.path.normpath(os.path.join(fixtures.www, path.lstrip('/')))
            if not local.startswith(fixtures.www + os.sep) or not os.path.isfile(local):
                self.send_response(404)
                self.send_header('Content-Length', '0')
                self.end_headers()
                self._log(404, 0)
                return
            with open(local, 'rb') as handle:
                body = handle.read()
            kind = {'.m3u8': 'application/vnd.apple.mpegurl', '.m3u': 'audio/x-mpegurl', '.mpd': 'application/dash+xml',
                    '.ts': 'video/mp2t', '.m4s': 'video/iso.segment', '.mp4': 'video/mp4'}.get(os.path.splitext(local)[1], 'application/octet-stream')
            self.send_response(200)
            self.send_header('Content-Type', kind)
            self.send_header('Content-Length', str(len(body)))
            self.send_header('Cache-Control', 'no-cache')
            self.end_headers()
            if not head:
                self.wfile.write(body)
            self._log(200, len(body))
        except (ConnectionError, OSError):
            pass

    def _radio(self, path, head):
        fixtures = self.server.fixtures
        name = os.path.splitext(os.path.basename(path))[0]
        source = os.path.join(fixtures.media, f'radio-{name}.mp3')
        if not os.path.isfile(source):
            self.send_response(404)
            self.send_header('Content-Length', '0')
            self.end_headers()
            self._log(404, 0)
            return
        if time.monotonic() < fixtures.outage_until:
            self.send_response(503)
            self.send_header('Content-Length', '0')
            self.end_headers()
            self._log(503, 0)
            return
        with open(source, 'rb') as handle:
            audio = handle.read()
        fixtures.radio_open(+1)
        self._log(100, 0)  # stream opened; the closing record carries the byte count
        metadata = self.headers.get('Icy-MetaData', '') == '1'
        self.send_response(200)
        self.send_header('Content-Type', 'audio/mpeg')
        self.send_header('icy-name', f'SP Audit Radio {name.upper()}')
        self.send_header('icy-br', str(RADIO_BITRATE // 1000))
        if metadata:
            self.send_header('icy-metaint', str(ICY_METAINT))
        self.send_header('Connection', 'close')
        self.end_headers()
        if head:
            self._log(200, 0)
            return
        sent, offset, block, track = 0, 0, 0, 0
        per_second = RADIO_BITRATE // 8
        started = time.monotonic()
        try:
            while not fixtures.stopping.is_set() and time.monotonic() >= fixtures.outage_until:
                chunk = audio[offset:offset + ICY_METAINT]
                if len(chunk) < ICY_METAINT:
                    chunk += audio[:ICY_METAINT - len(chunk)]
                offset = (offset + ICY_METAINT) % len(audio)
                self.wfile.write(chunk)
                sent += len(chunk)
                if metadata:
                    block += 1
                    if block % 10 == 1:
                        track += 1
                        text = f"StreamTitle='SP Audit {name.upper()} - Track {track}';".encode('utf-8')
                        padded = text + b'\0' * (-len(text) % 16)
                        self.wfile.write(bytes([len(padded) // 16]) + padded)
                    else:
                        self.wfile.write(b'\0')
                ahead = sent / per_second - (time.monotonic() - started)
                if ahead > 2.0:  # keep at most 2 s ahead of real time, like a live server
                    time.sleep(ahead - 2.0)
        except (ConnectionError, OSError):
            pass
        finally:
            fixtures.radio_open(-1)
            self._log(200, sent)


class _Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = False


class FixtureServers:
    def __init__(self, repo, run_dir, log=print):
        self.repo = repo
        self.run_dir = run_dir
        self.root = os.path.normpath(os.path.abspath(os.path.join(run_dir, 'fixtures')))
        self.www = os.path.join(self.root, 'www')
        self.media = os.path.join(self.root, 'media')
        self.log = log
        self.stopping = threading.Event()
        self.processes = []
        self.servers = []
        self._lock = threading.Lock()
        self.outage_until = 0.0
        self.open_radio = 0
        self._requests = open(os.path.join(run_dir, 'fixture-requests.jsonl'), 'a', encoding='utf-8')
        self.ffmpeg = toolcache.exe(repo, 'ffmpeg')
        self.mediamtx = toolcache.exe(repo, 'mediamtx')

    def record(self, entry):
        with self._lock:
            self._requests.write(json.dumps(entry) + '\n')
            self._requests.flush()

    def radio_open(self, delta):
        with self._lock:
            self.open_radio += delta

    def outage(self, seconds):
        """The radio server drops every stream and answers 503 for `seconds`: a network flap seen from the app."""
        self.outage_until = time.monotonic() + seconds

    def requests(self, since=0.0, prefix=''):
        with self._lock:
            self._requests.flush()
        out = []
        with open(os.path.join(self.run_dir, 'fixture-requests.jsonl'), encoding='utf-8') as handle:
            for line in handle:
                entry = json.loads(line)
                if entry['t'] >= since and entry['path'].startswith(prefix):
                    out.append(entry)
        return out

    # --- media
    def _ffmpeg(self, args, name, wait=True, cwd=None):
        # cwd matters: the DASH muxer writes its segments relative to the working directory, not beside the manifest
        # (seen 2026-11-13: segments landed in the caller's folder and the manifest's init segment answered 404).
        log = open(os.path.join(self.root, f'{name}.log'), 'w', encoding='utf-8')
        process = subprocess.Popen([self.ffmpeg, '-hide_banner', '-loglevel', 'warning', '-y'] + args,
                                   stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL, cwd=cwd or self.root)
        if wait:
            if process.wait(timeout=180) != 0:
                raise CouldNotVerify(f'ffmpeg {name} exited {process.returncode} (see fixtures/{name}.log)')
            return None
        self.processes.append(process)
        return process

    def _prepare_media(self):
        for name, frequency in (('a', 440), ('b', 660)):
            target = os.path.join(self.media, f'radio-{name}.mp3')
            if not os.path.isfile(target):
                self._ffmpeg(['-f', 'lavfi', '-i', f'sine=frequency={frequency}:duration=60:sample_rate=44100',
                              '-ac', '2', '-c:a', 'libmp3lame', '-b:a', '128k', target], f'radio-{name}')

    def _tls_pair(self):
        cert, key = os.path.join(self.root, 'tls-cert.pem'), os.path.join(self.root, 'tls-key.pem')
        if os.path.isfile(cert) and os.path.isfile(key):
            return cert, key
        script = ("$rsa=[Security.Cryptography.RSA]::Create(2048);"
                  "$req=[Security.Cryptography.X509Certificates.CertificateRequest]::new('CN=sp-audit-fixture',$rsa,"
                  "[Security.Cryptography.HashAlgorithmName]::SHA256,[Security.Cryptography.RSASignaturePadding]::Pkcs1);"
                  "$c=$req.CreateSelfSigned([DateTimeOffset]::Now.AddDays(-1),[DateTimeOffset]::Now.AddDays(30));"
                  f"[IO.File]::WriteAllText('{cert}',$c.ExportCertificatePem());"
                  f"[IO.File]::WriteAllText('{key}',$rsa.ExportPkcs8PrivateKeyPem())")
        done = subprocess.run(['pwsh', '-NoProfile', '-Command', script], capture_output=True, timeout=60)
        if done.returncode != 0 or not os.path.isfile(cert):
            raise CouldNotVerify(f'self-signed fixture certificate not generated: {done.stderr.decode(errors="replace")[:200]}')
        return cert, key

    def _write_playlists(self, big_m3u=None):
        with open(os.path.join(self.www, 'fixtures.m3u'), 'w', encoding='utf-8', newline='\n') as handle:
            handle.write(playlist_text(HEALTHY + [(t, u) for t, u, _ in FAULTS]))
        with open(os.path.join(self.www, 'long-names.m3u'), 'w', encoding='utf-8', newline='\n') as handle:
            handle.write(playlist_text(LONG_NAMES))
        with open(os.path.join(self.www, 'malformed.m3u'), 'wb') as handle:
            handle.write(b'#EXTM3U\n#EXTINF:-1,\x00\xff\xfe broken\nnot a url at all\n#EXTINF:garbage\n\n::::\n')
        oversized = os.path.join(self.www, 'oversized.m3u')
        if not os.path.isfile(oversized):  # past the importer's 32 MiB cap
            line = f'#EXTINF:-1,SP Audit Oversized\nhttp://over.example.invalid/{"x" * 200}\n'.encode()
            with open(oversized, 'wb') as handle:
                handle.write(b'#EXTM3U\n')
                for _ in range((33 * 1024 * 1024) // len(line) + 1):
                    handle.write(line)
        if big_m3u:
            target = os.path.join(self.www, 'big-100000.m3u')
            if not os.path.isfile(target):
                shutil.copyfile(big_m3u, target)

    def _wait_file(self, path, timeout_s, what):
        deadline = time.monotonic() + timeout_s
        while time.monotonic() < deadline:
            if os.path.isfile(path) and os.path.getsize(path) > 0:
                return
            time.sleep(0.5)
        raise CouldNotVerify(f'{what} did not appear within {timeout_s} s')

    def start(self, big_m3u=None):
        for folder in (self.www, self.media, os.path.join(self.www, 'hls'), os.path.join(self.www, 'dash')):
            os.makedirs(folder, exist_ok=True)
        self._prepare_media()
        self._write_playlists(big_m3u)
        pattern = ['-re', '-f', 'lavfi', '-i', 'testsrc2=size=640x360:rate=25', '-f', 'lavfi', '-i', 'sine=frequency=550:sample_rate=48000']
        video = ['-c:v', 'libx264', '-preset', 'veryfast', '-tune', 'zerolatency', '-pix_fmt', 'yuv420p', '-g', '50',
                 '-keyint_min', '50', '-sc_threshold', '0', '-c:a', 'aac', '-b:a', '96k']
        self._ffmpeg(pattern + video + ['-f', 'hls', '-hls_time', '2', '-hls_list_size', '6', '-hls_flags', 'delete_segments',
                                        os.path.join(self.www, 'hls', 'index.m3u8')], 'live-hls', wait=False)
        self._ffmpeg(pattern + video + ['-f', 'dash', '-seg_duration', '2', '-window_size', '6', '-extra_window_size', '2',
                                        '-use_template', '1', '-use_timeline', '1', '-streaming', '0',
                                        'manifest.mpd'], 'live-dash', wait=False, cwd=os.path.join(self.www, 'dash'))
        config = os.path.join(self.root, 'mediamtx.yml')
        with open(config, 'w', encoding='utf-8') as handle:
            handle.write(f'logLevel: debug\nrtspAddress: 127.0.0.1:{RTSP_PORT}\nrtsp: yes\nrtmp: no\nhls: no\nwebrtc: no\nsrt: no\n'
                         'api: no\nmetrics: no\npprof: no\nplayback: no\nprotocols: [tcp]\npaths:\n  pattern:\n    source: publisher\n')
        mtx_log = open(os.path.join(self.root, 'mediamtx.log'), 'w', encoding='utf-8')
        self.processes.append(subprocess.Popen([self.mediamtx, config], stdout=mtx_log, stderr=subprocess.STDOUT,
                                               stdin=subprocess.DEVNULL, cwd=self.root))
        time.sleep(2)
        self._ffmpeg(pattern + video + ['-f', 'rtsp', '-rtsp_transport', 'tcp', f'rtsp://127.0.0.1:{RTSP_PORT}/pattern'],
                     'live-rtsp', wait=False)
        cert, key = self._tls_pair()
        for port, scheme in ((HTTP_PORT, 'http'), (HTTPS_PORT, 'https')):
            try:
                server = _Server(('127.0.0.1', port), _Handler)
            except OSError as error:
                raise CouldNotVerify(f'fixture port {port} is taken: {error}') from error
            server.fixtures, server.scheme = self, scheme
            if scheme == 'https':
                context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
                context.load_cert_chain(cert, key)
                server.socket = context.wrap_socket(server.socket, server_side=True)
            threading.Thread(target=server.serve_forever, name=f'fixture-{scheme}', daemon=True).start()
            self.servers.append(server)
        self._wait_file(os.path.join(self.www, 'hls', 'index.m3u8'), 30, 'the live HLS playlist')
        self._wait_file(os.path.join(self.www, 'dash', 'manifest.mpd'), 30, 'the live DASH manifest')
        time.sleep(6)  # three segments of history before the first client
        return self

    def stop(self):
        self.stopping.set()
        for server in self.servers:
            server.shutdown()
            server.server_close()
        for process in self.processes:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
        self._requests.close()

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.stop()

    # --- instrument self-test: known-value readings from the host side before any scene trusts the fixtures
    def selftest(self):
        base = f'http://127.0.0.1:{HTTP_PORT}'
        checks = []
        text = urllib.request.urlopen(f'{base}/fixtures.m3u', timeout=10).read().decode('utf-8')
        entries = text.count('#EXTINF')
        if entries != len(HEALTHY) + len(FAULTS):
            raise CouldNotVerify(f'fixtures.m3u holds {entries} entries, expected {len(HEALTHY) + len(FAULTS)}')
        checks.append(f'fixtures.m3u {entries} entries')
        request = urllib.request.Request(f'{base}/radio/a.mp3', headers={'Icy-MetaData': '1'})
        with urllib.request.urlopen(request, timeout=10) as response:
            metaint = int(response.headers.get('icy-metaint', '0'))
            head = response.read(metaint + 1 + 64)
        if metaint != ICY_METAINT or b"StreamTitle='SP Audit A - Track 1'" not in head[metaint:]:
            raise CouldNotVerify('the ICY radio fixture did not deliver its metadata block where it said it would')
        checks.append(f'ICY radio metaint {metaint} with StreamTitle')
        try:
            urllib.request.urlopen(f'{base}/status/404/x', timeout=10)
            raise CouldNotVerify('the 404 fault endpoint answered 2xx')
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise CouldNotVerify(f'the 404 fault endpoint answered {error.code}') from error
        checks.append('status fault 404')
        hls = urllib.request.urlopen(f'{base}/hls/index.m3u8', timeout=10).read().decode()
        segments = [line for line in hls.splitlines() if line.endswith('.ts')]
        if len(segments) < 2:
            raise CouldNotVerify(f'the live HLS playlist has {len(segments)} segment(s)')
        checks.append(f'live HLS {len(segments)} segments')
        mpd = urllib.request.urlopen(f'{base}/dash/manifest.mpd', timeout=10).read().decode()
        if 'type="dynamic"' not in mpd:
            raise CouldNotVerify('the DASH manifest is not a live (dynamic) manifest')
        for template in sorted(set(re.findall(r'initialization="([^"]+)"', mpd))):
            for representation in ('0', '1'):
                path = template.replace('$RepresentationID$', representation)
                try:
                    urllib.request.urlopen(f'{base}/dash/{path}', timeout=10).read()
                except urllib.error.HTTPError as error:
                    raise CouldNotVerify(f'the DASH init segment {path} answers {error.code}') from error
        checks.append('live DASH manifest dynamic, init segments served')
        probe = os.path.join(os.path.dirname(self.ffmpeg), 'ffprobe.exe')
        done = subprocess.run([probe, '-v', 'error', '-rtsp_transport', 'tcp', '-show_entries', 'stream=codec_name',
                               '-of', 'csv=p=0', f'rtsp://127.0.0.1:{RTSP_PORT}/pattern'], capture_output=True, timeout=30)
        codecs = done.stdout.decode(errors='replace').split()
        if 'h264' not in codecs:
            raise CouldNotVerify(f'the RTSP fixture has no h264 stream (ffprobe: {codecs or done.stderr.decode(errors="replace")[:120]})')
        checks.append(f'RTSP streams {",".join(codecs)}')
        for port in (CLOSED_HTTP_PORT, CLOSED_RTSP_PORT):
            with socket.socket() as probe_socket:
                probe_socket.settimeout(1)
                if probe_socket.connect_ex(('127.0.0.1', port)) == 0:
                    raise CouldNotVerify(f'port {port} must stay closed for the wrong-port fault, and it is open')
        checks.append('wrong-port faults closed')
        return checks
