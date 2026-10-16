#!/usr/bin/env python3
"""Exercise the prepared sender capability and SETUP wire shapes on loopback, never a TV."""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='mooncast-airplay-wire-') as work:
    work = Path(work)
    source = work/'source'
    subprocess.run([sys.executable, root/'scripts/prepare_airplay.py', '--prepare', source], check=True)
    shutil.copy2(root/'tests/AirPlaySenderProtocolTest.go', source/'internal/airplay/mooncast_sender_test.go')
    env = dict(os.environ, GOPROXY=(root/'native/airplay-sender/module-proxy').as_uri(),
               GOSUMDB='off', GOTOOLCHAIN='local', GOCACHE=str(work/'cache'), GOMODCACHE=str(work/'modules'))
    subprocess.run(['go', 'test', './internal/airplay', '-run', 'TestMoonCastNegotiatedSetupWire|TestOptionalInfo|TestInfoAuthentication|TestInfoTransport|TestUnknownInfo',
                    '-count=1', '-timeout=30s', '-v'], cwd=source, env=env, check=True)
    subprocess.run(['go', 'test', './airplaylib', '-count=1', '-timeout=30s'], cwd=source, env=env, check=True)
