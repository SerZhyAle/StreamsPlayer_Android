"""Isolated disposable profile (S0025 section 7 item 7).

Before a session: a verified backup manifest (package list, settings dumps, both application data directories) and a
fresh emulator snapshot. The session refuses to start without a manifest it has re-verified complete. Every change
the rig makes to the device goes through Session.install / put_setting / push, which executes it and writes it into
the recovery manifest. Rollback runs after the steps, whatever they did: restore the snapshot, compare the package
list and settings digests with the backup, and check that every recorded change is gone. A rollback that cannot
prove itself fails the session with COULD NOT VERIFY and names what did not restore.
"""
import hashlib
import json
import os
import re
import time

from .verdict import CouldNotVerify

SETTINGS_NAMESPACES = ('global', 'system', 'secure')
SNAPSHOT = 'sp-audit-clean'

# Keys the OS itself rewrites when a snapshot is loaded, so a settings comparison must not count them. Each entry is
# dated and says what was observed; a key the rig changed itself is never excused here, because every recorded change
# is verified separately against its old value.
OS_CHURN = {
    'global': {
        'zen_mode_config_etag': 'rewritten by the notification policy on every restore; seen 2026-10-23 on API 35 (S0025 smoke run)',
    },
}


def _pairs(text):
    pairs = {}
    for line in text.splitlines():
        if '=' in line:
            key, value = line.split('=', 1)
            pairs[key] = value
    return pairs


def digest_text(text):
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


def digest_file(path):
    sha = hashlib.sha256()
    with open(path, 'rb') as handle:
        for block in iter(lambda: handle.read(1 << 20), b''):
            sha.update(block)
    return sha.hexdigest()


class AdbDevice:
    """The real device ops over adb. Account names are never stored: only the first 8 hex of their hashes."""

    def __init__(self, adb):
        self.adb = adb

    def packages(self):
        return {line.split(':', 1)[1].strip() for line in self.adb.shell('pm list packages').splitlines() if line.startswith('package:')}

    def settings(self, namespace):
        pairs = {}
        for line in self.adb.shell(f'settings list {namespace}').splitlines():
            if '=' in line:
                key, value = line.split('=', 1)
                pairs[key] = value
        return pairs

    def accounts(self):
        names = re.findall(r'Account \{name=([^,]+), type=', self.adb.shell('dumpsys account'))
        return sorted(hashlib.sha256(n.encode('utf-8')).hexdigest()[:8] for n in names)

    def existing_files(self, paths):
        return [p for p in paths if self.adb.shell(f'ls -d {p} 2>/dev/null').strip() == p]

    def archive_app_data(self, package, destination):
        data = self.adb.exec_out_bytes('run-as', package, 'tar', '-cf', '-', '.')
        with open(destination, 'wb') as handle:
            handle.write(data)

    def archive_external_data(self, package, destination):
        """The external app directory is created on demand; an app that never wrote there has none, and that is
        an empty archive, not a failed one (the existence check tells the two apart)."""
        directory = f'/sdcard/Android/data/{package}'
        present = self.adb.shell(f'ls -d {directory} 2>/dev/null').strip() == directory
        data = self.adb.exec_out_bytes('tar', '-cf', '-', '-C', directory, '.') if present else b''
        with open(destination, 'wb') as handle:
            handle.write(data)

    def install(self, apk):
        out = self.adb.run('install', '-r', apk)
        if 'Success' not in out:
            raise CouldNotVerify(f'install of {os.path.basename(apk)} did not succeed: {out.strip()[:120]}')

    def put_setting(self, namespace, key, value):
        self.adb.shell(f'settings put {namespace} {key} {value}')

    def push(self, local, remote):
        self.adb.run('push', local, remote)

    def snapshot_exists(self, name):
        return name in self.adb.run('emu', 'avd', 'snapshot', 'list', check=False)

    def snapshot_save(self, name):
        out = self.adb.run('emu', 'avd', 'snapshot', 'save', name, check=False)
        if 'OK' not in out:
            raise CouldNotVerify(f'snapshot save {name} failed: {out.strip()[:120]}')

    def snapshot_load(self, name):
        out = self.adb.run('emu', 'avd', 'snapshot', 'load', name, check=False)
        if 'OK' not in out:
            raise CouldNotVerify(f'snapshot load {name} failed: {out.strip()[:120]}')
        self.adb.run('wait-for-device', timeout=180)
        deadline = time.monotonic() + 180
        since_reconnect = 0.0
        while time.monotonic() < deadline:
            if self.adb.shell('getprop sys.boot_completed', timeout=30).strip() == '1':
                return
            time.sleep(2)
            since_reconnect += 2
            if since_reconnect >= 10:
                # A guest restarted by a snapshot load can leave adb stuck in authorizing/offline
                # (seen 2026-10-24 on the API 35 swiftshader image); kicking the connection revives it.
                self.adb.run('reconnect', 'offline', check=False, timeout=15)
                since_reconnect = 0.0
        raise CouldNotVerify('the device did not report boot_completed after the snapshot load')


class Session:
    def __init__(self, device, run_dir, package, snapshot=SNAPSHOT):
        self.device = device
        self.run_dir = run_dir
        self.package = package
        self.snapshot = snapshot
        self.backup_dir = os.path.join(run_dir, 'backup')
        self.manifest_path = os.path.join(self.backup_dir, 'manifest.json')
        self.recovery_path = os.path.join(run_dir, 'recovery-manifest.json')
        self.recovery = []

    # --- backup -----------------------------------------------------------------------------------------------
    def backup(self):
        os.makedirs(self.backup_dir, exist_ok=True)
        files = {}
        packages = sorted(self.device.packages())
        self._write('packages.txt', '\n'.join(packages) + '\n', files)
        for namespace in SETTINGS_NAMESPACES:
            pairs = self.device.settings(namespace)
            self._write(f'settings-{namespace}.txt', ''.join(f'{k}={v}\n' for k, v in sorted(pairs.items())), files)
        self._write('accounts.txt', '\n'.join(self.device.accounts()) + '\n', files)
        self.device.archive_app_data(self.package, os.path.join(self.backup_dir, 'appdata-internal.tar'))
        self.device.archive_external_data(self.package, os.path.join(self.backup_dir, 'appdata-external.tar'))
        for name in ('appdata-internal.tar', 'appdata-external.tar'):
            path = os.path.join(self.backup_dir, name)
            files[name] = {'bytes': os.path.getsize(path), 'sha256': digest_file(path)}
        self.device.snapshot_save(self.snapshot)  # always fresh: a stale snapshot would restore a stale device
        manifest = {'package': self.package, 'snapshot': self.snapshot, 'files': files,
                    'package_count': len(packages), 'package_list_sha256': digest_text('\n'.join(packages) + '\n'),
                    'settings_sha256': {ns: files[f'settings-{ns}.txt']['sha256'] for ns in SETTINGS_NAMESPACES}}
        with open(self.manifest_path, 'w', encoding='utf-8') as handle:
            json.dump(manifest, handle, indent=2, sort_keys=True)
        return self.require_verified_manifest()

    def _write(self, name, text, files):
        path = os.path.join(self.backup_dir, name)
        with open(path, 'w', encoding='utf-8', newline='\n') as handle:
            handle.write(text)
        files[name] = {'bytes': os.path.getsize(path), 'sha256': digest_file(path)}

    def require_verified_manifest(self):
        """Re-read the manifest and re-hash every file it names. A session never starts on anything less."""
        if not os.path.isfile(self.manifest_path):
            raise CouldNotVerify('no backup manifest: the session refuses to start')
        with open(self.manifest_path, encoding='utf-8') as handle:
            manifest = json.load(handle)
        problems = []
        for name, expected in manifest['files'].items():
            path = os.path.join(self.backup_dir, name)
            if not os.path.isfile(path):
                problems.append(f'{name} is missing')
            elif os.path.getsize(path) != expected['bytes'] or digest_file(path) != expected['sha256']:
                problems.append(f'{name} does not match its manifest entry')
        needed = ['packages.txt', 'accounts.txt', 'appdata-internal.tar', 'appdata-external.tar'] + [f'settings-{ns}.txt' for ns in SETTINGS_NAMESPACES]
        problems += [f'{name} is not in the manifest' for name in needed if name not in manifest['files']]
        for name in ('packages.txt', 'settings-global.txt', 'settings-secure.txt', 'settings-system.txt', 'appdata-internal.tar'):
            if name in manifest['files'] and manifest['files'][name]['bytes'] == 0:
                problems.append(f'{name} is empty (a backup of nothing is not a backup)')
        if not problems and self.package not in self._read_lines('packages.txt'):
            problems.append(f'{self.package} is not in the backed-up package list')
        if problems:
            raise CouldNotVerify('backup manifest incomplete: ' + '; '.join(problems))
        return manifest

    def _read_lines(self, name):
        with open(os.path.join(self.backup_dir, name), encoding='utf-8') as handle:
            return [line.rstrip('\n') for line in handle if line.strip()]

    # --- recorded changes --------------------------------------------------------------------------------------
    def _record(self, entry):
        self.recovery.append(entry)
        with open(self.recovery_path, 'w', encoding='utf-8') as handle:
            json.dump(self.recovery, handle, indent=2)

    def install(self, apk, package):
        self._record({'kind': 'install', 'package': package, 'was_installed': package in self.device.packages()})
        self.device.install(apk)

    def put_setting(self, namespace, key, value):
        # Only the first change of a key records its old value: a key set twice (rotate, rotate back) must be proved
        # back at the value it had before the session, not at an intermediate one (phase B, 2026-11-13).
        if not any(e['kind'] == 'setting' and e['namespace'] == namespace and e['key'] == key for e in self.recovery):
            self._record({'kind': 'setting', 'namespace': namespace, 'key': key, 'old': self.device.settings(namespace).get(key)})
        self.device.put_setting(namespace, key, value)

    def push(self, local, remote):
        self._record({'kind': 'push', 'path': remote})
        self.device.push(local, remote)

    # --- rollback ----------------------------------------------------------------------------------------------
    def rollback(self):
        """Restore the snapshot, then prove it. Returns normally only when everything is back; otherwise raises
        CouldNotVerify naming what did not restore."""
        manifest = self.require_verified_manifest()
        self.device.snapshot_load(self.snapshot)
        unrestored = []
        packages_now = sorted(self.device.packages())
        if digest_text('\n'.join(packages_now) + '\n') != manifest['package_list_sha256']:
            unrestored.append('package list differs: ' + ', '.join(sorted(set(packages_now) ^ set(self._read_lines('packages.txt')))[:8]))
        for namespace in SETTINGS_NAMESPACES:
            churn = OS_CHURN.get(namespace, {})
            with open(os.path.join(self.backup_dir, f'settings-{namespace}.txt'), encoding='utf-8') as handle:
                before = {k: v for k, v in _pairs(handle.read()).items() if k not in churn}
            now = {k: v for k, v in self.device.settings(namespace).items() if k not in churn}
            if now != before:
                names = sorted(k for k in set(now) | set(before) if now.get(k) != before.get(k))
                unrestored.append(f'settings {namespace} differ in: ' + ', '.join(names[:8]))
        if digest_text('\n'.join(self.device.accounts()) + '\n') != manifest['files']['accounts.txt']['sha256']:
            unrestored.append('accounts differ')
        pushed = [e['path'] for e in self.recovery if e['kind'] == 'push']
        unrestored += [f'pushed file {p} still exists' for p in self.device.existing_files(pushed)]
        for entry in self.recovery:
            if entry['kind'] == 'install' and not entry['was_installed'] and entry['package'] in packages_now:
                unrestored.append(f'installed package {entry["package"]} still present')
            if entry['kind'] == 'setting' and self.device.settings(entry['namespace']).get(entry['key']) != entry['old']:
                unrestored.append(f'setting {entry["namespace"]}/{entry["key"]} is not back at its old value')
        if unrestored:
            raise CouldNotVerify('rollback could not prove itself: ' + '; '.join(unrestored))
        return True


def run_session(device, run_dir, package, steps, snapshot=SNAPSHOT):
    """backup -> steps(session) -> rollback. steps returns (code, detail) like any rig script.

    The rollback runs whether the steps returned or raised. A rollback that does not prove itself turns the session
    into COULD NOT VERIFY, whatever the steps found; if the steps raised as well, both reasons are named.
    """
    session = Session(device, run_dir, package, snapshot)
    session.backup()
    try:
        result = steps(session)
    except BaseException as steps_error:
        try:
            session.rollback()
        except CouldNotVerify as rollback_error:
            raise CouldNotVerify(f'{rollback_error}; the steps had also failed: {steps_error}') from steps_error
        raise
    session.rollback()
    return result
