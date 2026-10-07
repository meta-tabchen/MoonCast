# Feature development and evidence

Features are developed, checked, and committed separately. MuMu evidence covers generated
test content on an emulator, not iPad/TV interoperability or phone thermal performance.

| Stage | Scope | State |
| --- | --- | --- |
| 1 | Smart cinema: stable border acquisition, lock, rescan, rotation reset | Implemented |
| 2 | Receiver profiles for framing/audio/input preferences | Implemented |
| 3 | Live sending/rendering and thermal dashboard; scenario presets | Implemented |
| 4 | Single-app capture, independent app display; Shizuku control backend | Implemented |
| 5 | Original-file cinema with a compatible receiver workflow | Implemented |
| 6 | Multiple simultaneous receivers and individual disconnect | Implemented (experimental) |
| 7 | Experimental privileged display-off casting with recovery | Implemented (experimental) |

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

## Stage 2

General, iPad, TV and computer profiles separately persist framing, playback audio,
local mute, codec preference, host label, and opt-in input preferences. Profile selection
is manual and disabled while the host runs; the native callback does not identify an
ordinary Moonlight client. Resolution/FPS/bitrate still belong to Moonlight. Root capture
is not remembered. Legacy preferences migrate once to General.

Build/lint and pinned APK checks pass. MuMu Android 15 instrumentation with an isolated
preference namespace passes migration, per-profile isolation and restart persistence.
The main activity also launches successfully on that emulator.

## Stage 3

The dashboard reports Android UID outbound Mbps, submitted capture FPS, CPU/driver
submission time, battery temperature and OS thermal status. These are not receiver
decode FPS, encoded-video-only bitrate, GPU execution time or end-to-end latency.
Unsupported counters/temperature show a dash. Root capture runs outside this UID and
is explicitly excluded from the dashboard. Movie, Game and Power saver presets change
framing/codec preference and cap capture submissions at 60/120/30 per second; they do
not silently grant input/audio permission or override Moonlight client configuration.

Portable counter tests pass, including pauses, reset/unsupported counters and Mbps
units. Build/lint and APK verification pass. The single registered MuMu test runner
passes the full GPU suite, including an actual capture submission limit, and the
profile suite. Thermal accuracy and power savings require real hardware measurement.

## Stage 4

Android 14+ single-app mode uses the system consent picker and capture resize callbacks.
Input is disabled because the system does not expose a reliable mapping back to the
app's position on the physical screen. Independent-display mode requires an authorized
Shizuku service and creates one app display at the receiver resolution. It supports
privileged touch/key routing and a launcher chooser. It currently sends video only.
Apps may refuse secondary displays or protected capture; this is experimental.

MuMu Android 15 passes actual independent-display creation, generated app launch,
rendered pixels, Shizuku touch/key/cancel delivery to that display, and display release.
Its shell lacks trusted-display permission, so the compatibility fallback was exercised.
MuMu Android 12 passes Root input touch/key/cancel delivery and broker cleanup after
granting MoonCast permission in MuMu's existing Superuser settings. SDK and Manager are
separate: the test installed official Shizuku Manager 13.6.0; it is not redistributed.
Build/lint, portable tests and APK payload checks pass. Single-app consent with an actual
Moonlight client and arbitrary third-party app compatibility remain device checks.

## Stage 5

Original-file cinema selects a single video, image or audio document with the system
file picker, then offers a tokenized LAN HTTP link with a minimal browser player.
The selected file's bytes are streamed without transcoding. GET/HEAD and single byte
ranges support compatible browser seeking. Unknown-length providers stream to EOF
without range support. Moonlight remains the screen receiver; it does not play this
file link. Receiver browser/container/codec support and decoded color/HDR rendering
are separate from original-byte transmission. This does not extract DRM video.

The independent file-sharing service has its own stop notification. Replacing the
file closes the old server and changes its token. Stopping closes client sockets and
revokes the link, including while the UI remains bound. Anyone holding an active link
can read the selected file; use a trusted LAN. No broad storage permission is requested.

Portable live-socket tests pass original bytes, seek/suffix, HEAD, token/path rejection,
invalid ranges, HTML escaping, unknown length and shutdown. MuMu Android 15 passes
generated SAF media, foreground service startup, exact HTTP bytes/ranges and immediate
stop revocation. Build/lint and APK checks pass. Browser playback on actual iPad/TV
and provider-specific seeking remain manual compatibility checks.

## Stage 6

Watch party permits up to three receivers sharing one capture texture and common framing.
Each output has its own size and encoder Surface. The receiver list can disconnect one
session through a separately compiled, pinned JNI bridge to the existing native stop
export. Callbacks register/unregister live handles before forwarding to Java, and the
bridge rejects inactive handles. EGL outputs detach before the native encoder releases
its Surface. All receivers must request the same audio packet duration. Input is disabled
in this mode because native key callbacks do not identify their receiver.

MuMu Android 15 passes single-capture GPU fan-out at different sizes, independent output
removal/addition and continuing survivor frames, plus native bridge loading and inactive
handle rejection. The complete existing GPU suite still passes. A real Moonlight-core
loopback harness is included as an optional debug-only fixture. TLS launch reaches the
host, but MuMu has no hardware AVC encoder and its software OMX encoder rejects the
native configuration (-61). Therefore multi-session encode/transport/decode, individual
native disconnect, shared audio and real receiver interoperability remain device checks.
No emulator encoding performance is claimed. Host startup also fixes Shizuku binder
handoff to non-provider processes. The JNI bridge builds in CI for all three ABIs and
its binaries/source/build recipe are hash-pinned separately from the upstream core.

## Stage 7

An off-by-default, per-session option requests physical panel power-off after frames
have been submitted. Ordinary capture uses an authorized Shizuku helper; Root capture
uses its existing UID-0 daemon. These are separate from enabling remote input. The OS
stays interactive: no lock/sleep key or secure settings are changed. Hidden display APIs
follow the compatibility approach documented by scrcpy; one unambiguous physical panel
is required when an internal token cannot be obtained. Unsupported devices refuse it.

The helper uses a 30-second lease, renewed every five seconds. Stop, owner death/EOF,
last receiver departure, manual Restore local display and capture errors attempt
restoration. Failed restoration remains pending for watchdog retries. Root additionally
requires a fresh host heartbeat; it does not renew forever when its owner is stalled.
Forcibly killing the privileged helper itself or a system/driver failure can defeat
software restoration; the physical power button remains the recovery path. No promise
is made for foldable/multiple-panel ROMs or actual battery savings.

Portable tests cover bounded renewals, expiry, idempotent cleanup, failed-off recovery
and restoration retries. MuMu Android 15 Shizuku instrumentation passes the actual power
RPC, Android remaining interactive, manual restore, 30-second watchdog expiry and helper
cleanup. MuMu Android 12 Root passes a bounded API/expiry probe. These are emulator API
checks; physical panel state, power consumption, continued phone/Moonlight playback and
OEM-specific behavior require real-device tests. Build/lint and pinned APK checks pass.
