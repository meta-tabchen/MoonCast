#!/usr/bin/env python3
"""Build provenance guards; synthetic source and no credentials or publication."""
import importlib.util
from pathlib import Path
import tempfile
import io
import zipfile
import subprocess
import unittest
from unittest.mock import patch
import package_tv_release as package
ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('prepare', ROOT / 'moonlight-core/scripts/prepare_moonlight_core.py')
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)

class ProvenanceTests(unittest.TestCase):
    def test_source_and_compiler_overrides_removed(self):
        hostile = {key:'/wrong' for key in ('UPSTREAM_JNI','DEPS','SOURCE_ROOT','SOURCE_MANIFEST','OPUS_SOURCE_DIR','OPENSSL_SOURCE_DIR','CC','CFLAGS','CPPFLAGS','LDFLAGS','LD_PRELOAD','BASH_ENV','MAKEFLAGS','PYTHONPATH')}
        hostile.update(PATH='/usr/bin', HOME='/tmp', JOBS='2')
        env = prepare.canonical_build_environment(Path('/fixed/native'), Path('/fixed/ndk'), Path('/fixed/cmake'), hostile)
        self.assertEqual(env['UPSTREAM_JNI'], '/fixed/native/upstream/app/src/main/jni')
        self.assertEqual(env['DEPS'], '/fixed/native/dependency-rebuild/artifacts')
        for key in ('SOURCE_ROOT','SOURCE_MANIFEST','OPUS_SOURCE_DIR','OPENSSL_SOURCE_DIR'):
            self.assertTrue(env[key].startswith('/fixed/native/native-sources'))
        for key in ('CC','CFLAGS','CPPFLAGS','LDFLAGS','LD_PRELOAD','BASH_ENV','MAKEFLAGS','PYTHONPATH'):
            self.assertNotIn(key, env)
        self.assertEqual(env['JOBS'], '2')
    def test_bad_parallelism_rejected(self):
        for jobs in ('0','65','-1','1; echo x'):
            with self.assertRaises(ValueError):
                prepare.canonical_build_environment('/fixed','/ndk','/cmake',{'JOBS':jobs})
    def test_export_ignored_inputs_and_exact_blob_bytes_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def git(*args):
                return subprocess.check_output(['git', *args], cwd=root, stderr=subprocess.DEVNULL)
            git('init', '-q')
            (root/'.gitattributes').write_text('update_version export-ignore\ngradlew.bat text eol=crlf\n')
            (root/'update_version').write_text('public build recipe\n')
            (root/'gradlew.bat').write_bytes(b'public wrapper\n')
            git('add', '.')
            git('-c','user.name=Test','-c','user.email=test@example.invalid','commit','-qm','fixture')
            with patch.object(package, 'ROOT', root):
                with zipfile.ZipFile(io.BytesIO(package.committed_source_archive())) as archive:
                    self.assertEqual(archive.read('update_version'), git('show', 'HEAD:update_version'))
                    self.assertEqual(archive.read('gradlew.bat'), git('show', 'HEAD:gradlew.bat'))

    def test_untracked_source_rejected_but_build_output_ignored(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def git(*args):
                return subprocess.check_output(['git',*args],cwd=root,stderr=subprocess.DEVNULL)
            git('init','-q')
            (root/'.gitignore').write_text('build/\n')
            git('add','.gitignore')
            git('-c','user.name=Test','-c','user.email=test@example.invalid','commit','-qm','fixture')
            (root/'build').mkdir();(root/'build/out.apk').write_bytes(b'fixture')
            with patch.object(package,'git',git):
                package.require_committed_sources()
                (root/'Added.java').write_text('// uncommitted source')
                with self.assertRaises(ValueError): package.require_committed_sources()
                git('add','Added.java')
                with self.assertRaises(ValueError): package.require_committed_sources()

if __name__ == '__main__': unittest.main()
