"""Thin adb runner. The rest of the rig talks to a device only through this surface, so a fake can stand in."""
import os
import shutil
import subprocess
import time

from .verdict import CouldNotVerify


def find_adb(repo_root=None):
    """SP_AUDIT_ADB, ANDROID_HOME / ANDROID_SDK_ROOT, local.properties sdk.dir, PATH - in that order."""
    candidates = []
    if os.environ.get('SP_AUDIT_ADB'):
        if not os.path.isfile(os.environ['SP_AUDIT_ADB']):
            raise CouldNotVerify(f'SP_AUDIT_ADB names a file that is not there ({os.environ["SP_AUDIT_ADB"]})')
        return os.environ['SP_AUDIT_ADB']
    for variable in ('ANDROID_HOME', 'ANDROID_SDK_ROOT'):
        if os.environ.get(variable):
            candidates.append(os.path.join(os.environ[variable], 'platform-tools', 'adb.exe'))
            candidates.append(os.path.join(os.environ[variable], 'platform-tools', 'adb'))
    if repo_root:
        props = os.path.join(repo_root, 'local.properties')
        if os.path.isfile(props):
            with open(props, encoding='utf-8') as handle:
                for line in handle:
                    if line.startswith('sdk.dir='):
                        sdk = line.split('=', 1)[1].strip().replace('\\:', ':').replace('\\\\', '\\')
                        candidates.append(os.path.join(sdk, 'platform-tools', 'adb.exe'))
    found = shutil.which('adb')
    if found:
        candidates.append(found)
    for path in candidates:
        if os.path.isfile(path):
            return path
    raise CouldNotVerify('adb not found (set SP_AUDIT_ADB, ANDROID_HOME, or sdk.dir in local.properties)')


class Adb:
    """One device, addressed by serial. All text is decoded as UTF-8 with replacement."""

    def __init__(self, serial, exe=None, timeout=60):
        self.serial = serial
        self.exe = exe or find_adb()
        self.timeout = timeout

    def _base(self):
        return [self.exe, '-s', self.serial]

    def require_device(self):
        """The serial must be attached and ready, or nothing below it can mean anything."""
        state = self.run('get-state', timeout=15, check=False).strip()
        if state != 'device':
            raise CouldNotVerify(f'device {self.serial} is not attached and ready (adb get-state: {state or "no answer"})')
        return self

    def run(self, *args, timeout=None, check=True):
        try:
            done = subprocess.run(self._base() + list(args), capture_output=True, timeout=timeout or self.timeout)
        except subprocess.TimeoutExpired as error:
            raise CouldNotVerify(f'adb {" ".join(args[:3])} timed out after {error.timeout}s') from error
        out = done.stdout.decode('utf-8', errors='replace').replace('\r\n', '\n')
        if check and done.returncode != 0:
            err = done.stderr.decode('utf-8', errors='replace').strip()
            raise CouldNotVerify(f'adb {" ".join(args[:3])} exited {done.returncode}: {err or out.strip()}')
        return out

    def shell(self, command, timeout=None, check=False):
        """Run a shell command on the device. adb shell's own exit code is the command's (check=False keeps text)."""
        return self.run('shell', command, timeout=timeout, check=check)

    def exec_out_bytes(self, *args, timeout=None):
        done = subprocess.run(self._base() + ['exec-out'] + list(args), capture_output=True, timeout=timeout or self.timeout)
        if done.returncode != 0:
            raise CouldNotVerify(f'adb exec-out {" ".join(args[:3])} exited {done.returncode}')
        return done.stdout

    def exec_out(self, *args, timeout=None):
        """exec-out as text. Unlike `adb shell`, it has no pty, so a command that writes a document to
        /dev/stdout (uiautomator dump) delivers it intact - `/dev/tty` under `adb shell` delivers nothing."""
        return self.exec_out_bytes(*args, timeout=timeout).decode('utf-8', errors='replace').replace('\r\n', '\n')

    def popen(self, *args, **kwargs):
        return subprocess.Popen(self._base() + list(args), **kwargs)

    def device_time_ms(self):
        """Device wall clock in ms, read in one adb round trip (used to bracket host timestamps)."""
        start = time.monotonic()
        text = self.shell('date +%s%3N').strip()
        end = time.monotonic()
        try:
            return int(text), (end - start) * 1000.0
        except ValueError as error:
            raise CouldNotVerify(f'device clock unreadable: {text!r}') from error
