"""Synthetic subjects with known values, for the fixture mode of every rig script (S0025 section 7 item 6).

ScriptedAdb answers shell commands from a table, so a parser or an instrument is checked against a reading whose
true value is known. FakeDevice is an in-memory device for the session logic, with injectable faults.
"""
import copy

from .verdict import CouldNotVerify


class ScriptedAdb:
    """shell(command) -> text. `table` maps a command substring to text or to a callable(command) -> text; the first
    entry whose key occurs in the command wins. A command nothing answers raises, so an instrument that asks
    something the fixture does not know fails loudly instead of reading an empty string."""

    def __init__(self, table):
        self.table = list(table.items()) if isinstance(table, dict) else list(table)
        self.commands = []

    def shell(self, command, timeout=None, check=False):
        self.commands.append(command)
        for key, answer in self.table:
            if key in command:
                return answer(command) if callable(answer) else answer
        raise CouldNotVerify(f'fixture has no answer for: {command}')

    def run(self, *args, timeout=None, check=True):
        return self.shell(' '.join(args))


class FakeDevice:
    """Same ops as session.AdbDevice. faults: 'leak-push' (a pushed file survives the snapshot load),
    'setting-sticks' (a changed setting survives it), 'empty-appdata' (the app data archive comes back empty),
    'no-app' (the package is not installed), 'lose-snapshot' (the snapshot load does not restore anything),
    'os-churn' (the OS rewrites an allow-listed key on load: not a failure), 'real-churn' (a key outside the
    allow-list differs after load: a failure)."""

    def __init__(self, package='com.sza.streamsplayer', faults=()):
        self.package = package
        self.faults = set(faults)
        self.packages_ = {'android', 'com.android.systemui'} | (set() if 'no-app' in self.faults else {package})
        self.settings_ = {'global': {'a': '1', 'b': '2', 'zen_mode_config_etag': '111'}, 'system': {'c': '3'}, 'secure': {'d': '4'}}
        self.accounts_ = []
        self.files = set()
        self.snapshots = {}

    def packages(self):
        return set(self.packages_)

    def settings(self, namespace):
        return dict(self.settings_[namespace])

    def accounts(self):
        return list(self.accounts_)

    def existing_files(self, paths):
        return [p for p in paths if p in self.files]

    def archive_app_data(self, package, destination):
        with open(destination, 'wb') as handle:
            handle.write(b'' if 'empty-appdata' in self.faults else b'tar-bytes-internal')

    def archive_external_data(self, package, destination):
        with open(destination, 'wb') as handle:
            handle.write(b'tar-bytes-external')

    def install(self, apk):
        self.packages_.add('com.fixture.installed')

    def put_setting(self, namespace, key, value):
        self.settings_[namespace][key] = value

    def push(self, local, remote):
        self.files.add(remote)

    def snapshot_exists(self, name):
        return name in self.snapshots

    def snapshot_save(self, name):
        self.snapshots[name] = copy.deepcopy((self.packages_, self.settings_, self.accounts_, self.files))

    def snapshot_load(self, name):
        if name not in self.snapshots:
            raise CouldNotVerify(f'snapshot {name} does not exist')
        if 'lose-snapshot' in self.faults:
            return
        packages, settings, accounts, files = copy.deepcopy(self.snapshots[name])
        keep_files = set(self.files) if 'leak-push' in self.faults else set()
        keep_settings = copy.deepcopy(self.settings_) if 'setting-sticks' in self.faults else None
        self.packages_, self.settings_, self.accounts_, self.files = packages, settings, accounts, files | keep_files
        if keep_settings is not None:
            self.settings_ = keep_settings
        if 'os-churn' in self.faults:
            self.settings_['global']['zen_mode_config_etag'] = '-999'
        if 'real-churn' in self.faults:
            self.settings_['global']['b'] = 'changed-by-the-os'
