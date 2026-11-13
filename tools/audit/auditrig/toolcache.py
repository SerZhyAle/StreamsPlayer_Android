"""Stream-fixture tool cache of phases B-D (S0025 decision 14.8.2, 2026-11-10): ffmpeg and mediamtx downloaded into the
gitignored temp/S0025/tools/, never committed and never shipped. The versions are pinned here; the SHA-256 of each
archive is recorded in temp/S0025/tools/manifest.json at the first download and every later use re-checks it, so the
binary a run cites is the one that was fetched (trust on first use - the publishers sign nothing the stdlib can check).
"""
import hashlib
import json
import os
import shutil
import subprocess
import urllib.request
import zipfile

from .verdict import CouldNotVerify

TOOLS = {
    'ffmpeg': {
        'version': '9.0.2',
        'url': 'https://github.com/GyanD/codexffmpeg/releases/download/9.0.2/ffmpeg-9.0.2-essentials_build.zip',
        'exe': 'ffmpeg.exe',
    },
    'mediamtx': {
        'version': '1.21.3',
        'url': 'https://github.com/bluenviron/mediamtx/releases/download/v1.21.3/mediamtx_v1.21.3_windows_amd64.zip',
        'exe': 'mediamtx.exe',
    },
}


def cache_dir(repo):
    return os.path.join(repo, 'temp', 'S0025', 'tools')


def _sha256(path):
    digest = hashlib.sha256()
    with open(path, 'rb') as handle:
        for block in iter(lambda: handle.read(1 << 20), b''):
            digest.update(block)
    return digest.hexdigest()


def _manifest_path(repo):
    return os.path.join(cache_dir(repo), 'manifest.json')


def _load_manifest(repo):
    path = _manifest_path(repo)
    if not os.path.isfile(path):
        return {}
    with open(path, encoding='utf-8') as handle:
        return json.load(handle)


def _find(root, name):
    for folder, _dirs, files in os.walk(root):
        if name in files:
            return os.path.join(folder, name)
    return None


def fetch(repo, log=print):
    """Download what is missing, verify what is there; returns {tool: {version, sha256, exe}}."""
    root = cache_dir(repo)
    os.makedirs(root, exist_ok=True)
    manifest = _load_manifest(repo)
    for name, spec in TOOLS.items():
        archive = os.path.join(root, os.path.basename(spec['url']))
        if not os.path.isfile(archive):
            log(f'download {name} {spec["version"]}: {spec["url"]}')
            partial = archive + '.part'
            try:
                with urllib.request.urlopen(spec['url'], timeout=120) as response, open(partial, 'wb') as out:
                    shutil.copyfileobj(response, out, 1 << 20)
            except OSError as error:
                raise CouldNotVerify(f'{name} download failed: {error}') from error
            os.replace(partial, archive)
        digest = _sha256(archive)
        known = manifest.get(name)
        if known and known.get('version') == spec['version'] and known.get('sha256') != digest:
            raise CouldNotVerify(f'{name} archive changed since its first download ({known["sha256"][:12]} -> {digest[:12]})')
        target = os.path.join(root, name)
        exe = _find(target, spec['exe']) if os.path.isdir(target) else None
        if not exe:
            with zipfile.ZipFile(archive) as bundle:
                bundle.extractall(target)
            exe = _find(target, spec['exe'])
        if not exe:
            raise CouldNotVerify(f'{spec["exe"]} not found in {os.path.basename(archive)}')
        manifest[name] = {'version': spec['version'], 'url': spec['url'], 'sha256': digest,
                          'exe': os.path.relpath(exe, root)}
    with open(_manifest_path(repo), 'w', encoding='utf-8') as handle:
        json.dump(manifest, handle, indent=2, sort_keys=True)
    return manifest


def exe(repo, name):
    """The verified executable of one tool, or CouldNotVerify naming the fetch command."""
    manifest = _load_manifest(repo)
    entry = manifest.get(name)
    if not entry or entry.get('version') != TOOLS[name]['version']:
        raise CouldNotVerify(f'{name} {TOOLS[name]["version"]} is not in the tool cache (run: python tools/audit/rig.py tools)')
    path = os.path.join(cache_dir(repo), entry['exe'])
    if not os.path.isfile(path):
        raise CouldNotVerify(f'{name} executable missing from the tool cache ({path})')
    return path


def version_line(path):
    done = subprocess.run([path, '-version'] if path.endswith('ffmpeg.exe') else [path, '--version'],
                          capture_output=True, timeout=30)
    text = (done.stdout or done.stderr).decode('utf-8', errors='replace').strip().splitlines()
    return text[0] if text else ''
