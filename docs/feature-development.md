# Feature development and evidence

Features are developed, checked, and committed separately. MuMu evidence covers generated
test content on an emulator, not iPad/TV interoperability or phone thermal performance.

| Stage | Scope | State |
| --- | --- | --- |
| 1 | Smart cinema: stable border acquisition, lock, rescan, rotation reset | Implemented |
| 2 | Named receiver profiles for framing/audio/input preferences | Planned |
| 3 | Live sending/rendering and thermal dashboard; scenario presets | Planned |
| 4 | Single-app capture, independent app display; Shizuku control backend | Planned |
| 5 | Original-file cinema with a compatible receiver workflow | Planned |
| 6 | Multiple simultaneous receivers and individual disconnect | Planned |
| 7 | Experimental privileged display-off casting with recovery | Planned |

## Stage 1

Cinema samples landscape borders every 250 ms. Eight consistent non-full-frame samples
acquire a region; full-screen UI and dark frames do not lock it. Once acquired, the
region stays fixed until rescan, capture resize, or mode change. This deliberately does
not claim to discover a third-party player's internal video surface. The four original
framing modes remain unchanged.

Checks: portable geometry/acquisition tests, debug APK build, Android lint, and pinned
native payload verification pass. MuMu Android 15 GPU instrumentation passes actual
SurfaceTexture/EGL output, fit/fill, paused-frame detection, centered 16:9, portrait
resize, and controls covering the original bars for longer than the previous filter's
expansion delay. Actual video players and Moonlight receivers still need device tests.
