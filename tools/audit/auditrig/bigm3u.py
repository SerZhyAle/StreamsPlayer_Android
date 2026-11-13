"""The synthetic oversized playlist of SC-10 and SC-20 (S0025 decision 14.4: 100,000 entries, beside the real
19,404-channel catalog of S0007 as the only other canonical big input).

Deterministic: the same arguments give the same bytes, so a run folder names the file by its SHA-256 and any session
regenerates it. Every address uses the reserved `.invalid` top-level domain, which never resolves, so importing the
file can never reach a real server, and every address is distinct, so an import that skips duplicates still meets
every entry. The mix is what real playlists carry: radio, HLS, DASH and RTSP addresses, groups (some with a comma
inside the quotes, USER-PLAYLIST section 8 item 2) and logos; every 37th name is 200 characters long and every 11th
is Russian, Ukrainian, Japanese or carries an emoji - the long and unicode names of SC-20. No line starts with
`#EXT-X-`, which would make the whole file read as an HLS manifest instead of a playlist.
"""
import hashlib

from .verdict import CouldNotVerify

DEFAULT_ENTRIES = 100_000
LONG_EVERY, LONG_LENGTH = 37, 200
UNICODE_EVERY = 11
GROUPS = ('News', 'Music', 'Rock, Pop', 'Talk', 'Cameras', 'Sport', 'Kids', 'Jazz, Blues')
UNICODE_NAMES = ('Радио Север', 'Радіо Київ ґ є ї', '東京ラジオ', 'Radio 🎧 Live')
URLS = (
    'http://radio{n}.example.invalid/{i}/stream.mp3',
    'https://hls{n}.example.invalid/live/{i}/index.m3u8',
    'https://dash{n}.example.invalid/live/{i}/manifest.mpd',
    'https://aac{n}.example.invalid/{i}/stream.aac',
    'rtsp://cam{n}.example.invalid:554/{i}/stream1',
)


def name_of(index):
    if index % LONG_EVERY == 0:
        prefix = f'Long Channel {index} '
        return prefix + 'x' * (LONG_LENGTH - len(prefix))
    if index % UNICODE_EVERY == 0:
        return f'{UNICODE_NAMES[(index // UNICODE_EVERY) % len(UNICODE_NAMES)]} {index}'
    return f'Channel {index}'


def entry(index):
    url = URLS[index % len(URLS)]
    group = GROUPS[index % len(GROUPS)]
    return (f'#EXTINF:-1 tvg-id="sp-audit-{index}" tvg-logo="https://logo.example.invalid/{index}.png" '
            f'group-title="{group}",{name_of(index)}\n{url.format(n=index % 997, i=index)}\n')


def write(path, entries=DEFAULT_ENTRIES):
    """Write the playlist -> {'path', 'entries', 'bytes', 'sha256'}."""
    if entries < 1:
        raise CouldNotVerify(f'a playlist needs at least one entry, got {entries}')
    digest = hashlib.sha256()
    size = 0
    with open(path, 'wb') as handle:
        for chunk in ['#EXTM3U\n'] + [entry(i) for i in range(entries)]:
            data = chunk.encode('utf-8')
            handle.write(data)
            digest.update(data)
            size += len(data)
    return {'path': path, 'entries': entries, 'bytes': size, 'sha256': digest.hexdigest()}
