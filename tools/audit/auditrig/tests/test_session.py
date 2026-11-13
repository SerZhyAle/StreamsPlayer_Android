import os
import unittest

from auditrig import session
from auditrig.fakes import FakeDevice
from auditrig.tests.helpers import PACKAGE, read_json, tempdir
from auditrig.verdict import CouldNotVerify, PASS, FAIL

PUSHED = '/sdcard/Download/sp-audit/fixture.m3u'


def steps_that_change_everything(current):
    current.install('fixture.apk', 'com.fixture.installed')
    current.put_setting('global', 'a', '9')
    current.put_setting('system', 'new_key', '1')
    current.push('fixture.m3u', PUSHED)
    return PASS, 'scene within budget'


class SessionTests(unittest.TestCase):
    def test_a_clean_session_changes_everything_and_proves_it_all_came_back(self):
        device = FakeDevice()
        with tempdir() as d:
            result = session.run_session(device, d, PACKAGE, steps_that_change_everything)
            self.assertEqual(result, (PASS, 'scene within budget'))
            recovery = read_json(os.path.join(d, 'recovery-manifest.json'))
            self.assertEqual([e['kind'] for e in recovery], ['install', 'setting', 'setting', 'push'])
        self.assertNotIn('com.fixture.installed', device.packages())
        self.assertEqual(device.settings('global')['a'], '1')
        self.assertNotIn('new_key', device.settings('system'))
        self.assertEqual(device.existing_files([PUSHED]), [])

    def test_a_key_set_twice_is_proved_back_at_its_value_before_the_session(self):
        def rotate_and_back(current):
            current.put_setting('global', 'a', '5')
            current.put_setting('global', 'a', '7')
            return PASS, 'rotated'
        with tempdir() as d:
            self.assertEqual(session.run_session(FakeDevice(), d, PACKAGE, rotate_and_back), (PASS, 'rotated'))
            recovery = read_json(os.path.join(d, 'recovery-manifest.json'))
            self.assertEqual([(e['key'], e['old']) for e in recovery], [('a', '1')])

    def test_the_steps_verdict_survives_a_clean_rollback(self):
        with tempdir() as d:
            result = session.run_session(FakeDevice(), d, PACKAGE, lambda current: (FAIL, 'a defect'))
            self.assertEqual(result, (FAIL, 'a defect'))

    def test_a_pushed_file_that_survives_the_restore_fails_the_session_and_is_named(self):
        with tempdir() as d:
            with self.assertRaises(CouldNotVerify) as caught:
                session.run_session(FakeDevice(faults=['leak-push']), d, PACKAGE, steps_that_change_everything)
        self.assertIn(PUSHED, str(caught.exception))
        self.assertIn('rollback could not prove itself', str(caught.exception))

    def test_a_setting_that_survives_the_restore_is_named(self):
        with tempdir() as d:
            with self.assertRaises(CouldNotVerify) as caught:
                session.run_session(FakeDevice(faults=['setting-sticks']), d, PACKAGE, steps_that_change_everything)
        self.assertIn('settings global differ in: a', str(caught.exception))
        self.assertIn('setting global/a is not back', str(caught.exception))

    def test_a_key_the_os_rewrites_on_restore_does_not_fail_the_rollback(self):
        with tempdir() as d:
            result = session.run_session(FakeDevice(faults=['os-churn']), d, PACKAGE, lambda current: (PASS, 'ok'))
        self.assertEqual(result, (PASS, 'ok'))

    def test_a_key_outside_the_allow_list_that_differs_is_named(self):
        with tempdir() as d:
            with self.assertRaises(CouldNotVerify) as caught:
                session.run_session(FakeDevice(faults=['real-churn']), d, PACKAGE, lambda current: (PASS, 'ok'))
        self.assertIn('settings global differ in: b', str(caught.exception))

    def test_a_key_the_rig_changed_is_never_excused_by_the_allow_list(self):
        def change_the_churn_key(current):
            current.put_setting('global', 'zen_mode_config_etag', '5')
            return PASS, 'ok'
        with tempdir() as d:
            with self.assertRaises(CouldNotVerify) as caught:
                session.run_session(FakeDevice(faults=['setting-sticks']), d, PACKAGE, change_the_churn_key)
        self.assertIn('setting global/zen_mode_config_etag is not back', str(caught.exception))

    def test_a_snapshot_that_restores_nothing_leaves_the_package_and_is_named(self):
        with tempdir() as d:
            with self.assertRaises(CouldNotVerify) as caught:
                session.run_session(FakeDevice(faults=['lose-snapshot']), d, PACKAGE, steps_that_change_everything)
        self.assertIn('com.fixture.installed', str(caught.exception))

    def test_an_empty_app_data_archive_is_not_a_backup_and_nothing_runs(self):
        ran = []
        with tempdir() as d:
            with self.assertRaises(CouldNotVerify) as caught:
                session.run_session(FakeDevice(faults=['empty-appdata']), d, PACKAGE, lambda current: ran.append(1) or (PASS, ''))
        self.assertIn('appdata-internal.tar is empty', str(caught.exception))
        self.assertEqual(ran, [])

    def test_a_package_that_is_not_installed_cannot_be_backed_up(self):
        with tempdir() as d:
            with self.assertRaises(CouldNotVerify) as caught:
                session.run_session(FakeDevice(faults=['no-app']), d, PACKAGE, lambda current: (PASS, ''))
        self.assertIn('not in the backed-up package list', str(caught.exception))

    def test_without_a_manifest_the_session_refuses_to_start(self):
        with tempdir() as d:
            with self.assertRaises(CouldNotVerify) as caught:
                session.Session(FakeDevice(), d, PACKAGE).require_verified_manifest()
        self.assertIn('refuses to start', str(caught.exception))

    def test_a_backup_file_changed_after_the_manifest_was_written_is_caught(self):
        device = FakeDevice()
        with tempdir() as d:
            current = session.Session(device, d, PACKAGE)
            current.backup()
            with open(os.path.join(d, 'backup', 'settings-global.txt'), 'a', encoding='utf-8') as handle:
                handle.write('tampered=1\n')
            with self.assertRaises(CouldNotVerify) as caught:
                current.require_verified_manifest()
        self.assertIn('settings-global.txt does not match', str(caught.exception))

    def test_a_manifest_entry_that_was_deleted_is_caught(self):
        with tempdir() as d:
            current = session.Session(FakeDevice(), d, PACKAGE)
            current.backup()
            os.remove(os.path.join(d, 'backup', 'appdata-external.tar'))
            with self.assertRaises(CouldNotVerify) as caught:
                current.require_verified_manifest()
        self.assertIn('appdata-external.tar is missing', str(caught.exception))

    def test_steps_that_raise_still_roll_back_and_the_error_propagates(self):
        device = FakeDevice()

        def exploding(current):
            current.push('x', PUSHED)
            raise RuntimeError('the scene driver died')
        with tempdir() as d:
            with self.assertRaises(RuntimeError):
                session.run_session(device, d, PACKAGE, exploding)
        self.assertEqual(device.existing_files([PUSHED]), [])

    def test_steps_that_raise_and_a_rollback_that_fails_name_both(self):
        def exploding(current):
            current.push('x', PUSHED)
            raise RuntimeError('the scene driver died')
        with tempdir() as d:
            with self.assertRaises(CouldNotVerify) as caught:
                session.run_session(FakeDevice(faults=['leak-push']), d, PACKAGE, exploding)
        self.assertIn(PUSHED, str(caught.exception))
        self.assertIn('the scene driver died', str(caught.exception))


if __name__ == '__main__':
    unittest.main()
