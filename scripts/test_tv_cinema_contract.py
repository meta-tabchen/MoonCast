#!/usr/bin/env python3
"""Source guardrails, not device playback evidence. Runtime checks live in TvUiTestDriver."""
from pathlib import Path
import re
root = Path(__file__).resolve().parents[1]
home = (root / 'tv/src/main/java/com/mooncast/tv/TvHomeView.java').read_text()
activity = (root / 'tv/src/main/java/com/mooncast/tv/TvActivity.java').read_text()
assert 'setBackgroundColor(Color.TRANSPARENT)' in home
assert 'if(!playbackActive){\n            if(background!=null)' in home
assert 'c.drawColor(0x28050a14)' in home
for name in ['showPanel', 'hidePanel']:
    body = re.search(r'private void ' + name + r'\(\)\{([^\n]+)', activity).group(1)
    assert not any(x in body for x in ['stop(', 'pause(', 'release(', 'setSurface', 'Texture.setVisibility', 'blackout.setVisibility']), body
assert 'public void onEndCast(){stop();}' in activity
assert '(playbackActive?hide:receiving?framing:toggle).requestFocus()' in home
assert 'hide.setNextFocusDownId(endCast.getId())' in home
assert 'endCast.setNextFocusUpId(hide.getId())' in home
assert 'if(hasPlayback()){hidePanel();return;}' in activity
print('TV cinema source contracts passed; actual decoder/audio continuity still requires device instrumentation.')
