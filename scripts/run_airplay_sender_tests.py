#!/usr/bin/env python3
"""Host regressions for sender selection/diagnostics. No device acceptance implied."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
java = Path(os.environ['JAVA_HOME']) / 'bin'
src = root / 'app/src/main/java/com/mooncast/host'
with tempfile.TemporaryDirectory(prefix='mooncast-airplay-policy-') as output:
    subprocess.run([java/'javac', '--release', '17', '-d', output,
                    src/'AirPlayDiagnostics.java', src/'HostFailureStore.java',
                    root/'tests/AirPlayDiagnosticsTest.java'], check=True)
    subprocess.run([java/'java', '-cp', output, 'com.mooncast.host.AirPlayDiagnosticsTest'], check=True)

service = (src/'AirPlayHostService.java').read_text()
# This wiring check prevents accidentally restoring upstream's bare /video path.
assert 'Airplaylib.setAirPlay1Mode(false);' in service
assert 'Airplaylib.setAirPlay1Mode(legacy);' not in service
assert 'AirPlayDiagnostics.failure(reason, protocolStage)' in service
assert 'public void onError(String message) { deliver(() -> failProtocol(message)); }' in service
assert 'HostFailureStore.failure(getFilesDir(), statusToken' in service
assert 'oldMode==CastSessionCoordinator.AIRPLAY' in (src/'MainActivity.java').read_text()
print('PASS sender uses negotiated transport, sanitized failure callback and retained result')
