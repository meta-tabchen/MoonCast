#!/usr/bin/env python3
"""Test release-signing guards without creating, reading, or using signing keys.

Pass --gradle ./gradlew to also execute the real configuration resolver in an
isolated Gradle project. The fixture is ordinary text, never a keystore.
"""
import argparse
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

import package_tv_release as package

ROOT = Path(__file__).resolve().parents[1]
FINGERPRINT = 'ab' * 32


def report(fingerprint=FINGERPRINT, subject='CN=Test fixture, O=No real certificate', count=1):
    return (f'Verifies\nNumber of signers: {count}\n'
            f'Signer #1 certificate DN: {subject}\n'
            f'Signer #1 certificate SHA-256 digest: {fingerprint}\n')


class SigningGuardTests(unittest.TestCase):
    def test_fingerprint_normalization(self):
        self.assertEqual(package.normalized_fingerprint(':'.join(['AB'] * 32)), FINGERPRINT)
        for invalid in ('', 'ab', 'g' * 64, 'ab' * 33, 'a b' * 32):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                package.normalized_fingerprint(invalid)

    def test_pinned_external_signer(self):
        self.assertEqual(package.check_signer_report(report(), FINGERPRINT),
                         {'mode': 'external-keystore', 'certificateSha256': FINGERPRINT})

    def test_debug_identity_rejected_even_with_matching_pin(self):
        for subject in ('CN=Android Debug,O=Android,C=US', 'CN=android debug, O=Android'):
            with self.subTest(subject=subject), self.assertRaisesRegex(ValueError, 'Android Debug'):
                package.check_signer_report(report(subject=subject), FINGERPRINT)

    def test_wrong_pin_rejected(self):
        with self.assertRaisesRegex(ValueError, 'does not match'):
            package.check_signer_report(report(), 'cd' * 32)

    def test_missing_ambiguous_or_additional_signers_rejected(self):
        samples = [report(count=2), report() + report(), '',
                   report().replace('Number of signers: 1\n', ''),
                   report().replace('Signer #1 certificate DN:', 'Other subject:'),
                   report().replace('Signer #1', 'Signer #2')]
        for sample in samples:
            with self.subTest(sample=sample), self.assertRaises(ValueError):
                package.check_signer_report(sample, FINGERPRINT)

    def test_real_verification_tool_is_required(self):
        with tempfile.TemporaryDirectory(prefix='mooncast-signing-test-') as directory:
            with self.assertRaisesRegex(ValueError, 'Build Tools'):
                package.verify_release_signing(Path('not-opened.apk'), FINGERPRINT, Path(directory))

    @mock.patch.object(package.subprocess, 'run')
    @mock.patch.object(Path, 'is_file', return_value=True)
    def test_signature_verification_failure_cannot_be_packaged(self, exists, run):
        run.return_value = subprocess.CompletedProcess([], 1, report(), 'invalid signature')
        with self.assertRaisesRegex(ValueError, 'signature verification failed'):
            package.verify_release_signing(Path('fixture.apk'), FINGERPRINT, Path('/test-sdk'))
        self.assertEqual(run.call_args.args[0][1:4], ['verify', '--verbose', '--print-certs'])
        self.assertNotIn('sign', run.call_args.args[0])

    @mock.patch.object(package.subprocess, 'run')
    @mock.patch.object(Path, 'is_file', return_value=True)
    def test_successful_tool_report_must_still_match_pin(self, exists, run):
        run.return_value = subprocess.CompletedProcess([], 0, report(), '')
        self.assertEqual(package.verify_release_signing(Path('fixture.apk'), FINGERPRINT, Path('/test-sdk')),
                         {'mode': 'external-keystore', 'certificateSha256': FINGERPRINT})
        with self.assertRaisesRegex(ValueError, 'does not match'):
            package.verify_release_signing(Path('fixture.apk'), 'cd' * 32, Path('/test-sdk'))

    def test_packaging_requires_explicit_opt_in_before_writing(self):
        with tempfile.TemporaryDirectory(prefix='mooncast-signing-test-') as directory:
            output = Path(directory) / 'output'
            result = subprocess.run([sys.executable, str(ROOT / 'scripts/package_tv_release.py'),
                                     '--apk', str(Path(directory) / 'absent.apk'), '--output', str(output)],
                                    capture_output=True, text=True, check=False)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('Release packaging is opt-in', result.stderr)
            self.assertFalse(output.exists())

    def test_sources_exclude_signing_material_and_retain_licenses(self):
        for name in ('release.jks', 'keys/RELEASE.P12', 'private.pfx', 'private.key', 'cert.pem',
                     'certificate.crt', 'certificate.cer', 'certificate.der',
                     'signing.jceks', '.env.release', 'local.properties', 'host-key.pem'):
            with self.subTest(name=name):
                self.assertFalse(package.safe_source_name(name))
        for name in ('LICENSE', 'THIRD_PARTY_NOTICES.md', 'native/airplay/LICENSE',
                     'gradle/tv-signing.gradle', 'docs/tv-signing.md'):
            with self.subTest(name=name):
                self.assertTrue(package.safe_source_name(name))


def run_gradle_resolver_tests(gradle):
    with tempfile.TemporaryDirectory(prefix='mooncast-signing-gradle-') as directory:
        project = Path(directory)
        (project / 'settings.gradle').write_text("rootProject.name = 'signing-validation-only'\n")
        # This file only tests path validation. It is deliberately not key material.
        fixture = project / 'ordinary-text-fixture.txt'
        fixture.write_text('Not a keystore. No key or certificate was generated.\n')
        helper = (ROOT / 'gradle/tv-signing.gradle').as_posix().replace("'", "\\'")
        (project / 'build.gradle').write_text("apply from: '" + helper + "'\n" + r'''
tasks.register('testSigningResolver') {
    doLast {
        def names = [storeFile: 'MOONCAST_TV_KEYSTORE_PATH', storePassword: 'MOONCAST_TV_STORE_PASSWORD',
                     keyAlias: 'MOONCAST_TV_KEY_ALIAS', keyPassword: 'MOONCAST_TV_KEY_PASSWORD']
        def properties = [storeFile: 'mooncastTvKeystorePath', storePassword: 'mooncastTvStorePassword',
                          keyAlias: 'mooncastTvKeyAlias', keyPassword: 'mooncastTvKeyPassword']
        def values = [storeFile: file('ordinary-text-fixture.txt').absolutePath,
                      storePassword: ' fixture password ', keyAlias: 'fixture-only', keyPassword: ' fixture key password ']
        def env = names.collectEntries { key, name -> [(name): values[key]] }
        def props = properties.collectEntries { key, name -> [(name): values[key]] }
        def resolve = { Map p, Map e -> resolveTvReleaseSigning({ p[it] }, { e[it] }, { file(it) }) }
        def expectFailure = { Map p, Map e, String expected ->
            try { resolve(p, e); throw new AssertionError('Expected signing configuration rejection') }
            catch (GradleException failure) {
                assert failure.message.contains(expected)
                assert !failure.message.contains('fixture password')
                assert !failure.message.contains('fixture key password')
            }
        }
        assert resolve([:], [:]) == null
        assert resolve([:], env).storeFile == file('ordinary-text-fixture.txt')
        assert resolve(props, [:]).keyPassword == values.keyPassword
        assert resolve(props, env + [MOONCAST_TV_KEY_ALIAS: 'environment-wins']).keyAlias == 'environment-wins'
        names.each { key, name ->
            expectFailure([:], env.findAll { k, v -> k != name }, name)
            expectFailure(props, env + [(name): ''], name)
        }
        expectFailure([:], [MOONCAST_TV_KEY_ALIAS: ''], 'Incomplete external TV signing')
        expectFailure([:], env + [MOONCAST_TV_KEYSTORE_PATH: 'missing-fixture.txt'], 'existing external keystore file')
        expectFailure([:], env + [MOONCAST_TV_KEYSTORE_PATH: '.'], 'existing external keystore file')
        println 'TV external signing resolver: 15 credential-free cases passed.'
    }
}
''')
        env = {key: value for key, value in os.environ.items()
               if not key.startswith(('MOONCAST_TV_', 'ORG_GRADLE_PROJECT_mooncastTv'))}
        subprocess.run([str(gradle), '--offline', '--no-daemon', '--console=plain',
                        '--project-dir', str(project), '--gradle-user-home', str(project / 'gradle-home'),
                        'testSigningResolver'], env=env, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gradle', type=Path, help='Also run the real Gradle resolver without Android or keys')
    args = parser.parse_args()
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(SigningGuardTests))
    if not result.wasSuccessful():
        return 1
    if args.gradle:
        run_gradle_resolver_tests(args.gradle.resolve())
    else:
        print('Gradle resolver cases not run; pass --gradle ./gradlew to include them.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
