## Local-only MoonCast 0.7.3 follow-up

The independent patch0003 adds validated legacy HTTP mirroring at the selected
numeric LAN receiver's port7100: GET /stream.xml, optional HTTP FairPlay,
binary POST /stream and continuous video on the same socket, with AVCC config,
4-byte NAL lengths, 128-byte headers, 32.32 timestamps and peer-filtered UDP7010
clock responses. Digest challenges are honored; required encryption and crypto
failures never fall back to plaintext. Only explicitly unsupported FairPlay
endpoints allow the documented optional-parameter stream format. Probe failure
keeps the modern path. This follow-up is local only; no uploads or publication.

The original snapshot and historical0.7.2 notes below are retained. Current
native hashes and toolchain are in prebuilt-manifest.json. The local build uses
/tmp/mooncast-airplay-073/source. Real TV acceptance remains untested.

## MoonCast 0.7.2 native rebuild

The upstream snapshot below remains v0.0.33. MoonCast patch 0002 makes only the
`/info` 404/405/501 responses optional, preserves all authentication and transport
errors, and keeps FairPlay negotiation when capabilities are unknown. No HTTP
protocol rewrite or empty SETUP shortcut is introduced. See
[the follow-up](../../docs/airplay-0.7.2.md).

The 0.7.2 AAR is rebuilt with Go 1.25.6 darwin/amd64, the pinned Go mobile
module, official NDK r29 (29.0.14206865), JDK 17.0.20.1, platform 35/API 26.
The official Darwin archive SHA256 is
`ce5e4b100ec5fe5be4eb3edcb2c02528824ff9cda3860f5304619be6c3da34d3`.
The build helper uses `/tmp/mooncast-airplay-072/source`; `prebuilt-manifest.json`
records the actual new output/source hashes. The new AAR is committed with its
sources and is excluded from the historical 0.5.0 dependency restoration list,
so restoration cannot replace it with the old sender.

The rest of this document describes the original baseline and historical rebuild;
its Linux hash comparison is not a claim about the 0.7.2 Darwin output.

# AirPlay sender native runtime (v0.0.33)

`app/libs/airplaylib.aar` is built from source, not extracted from an APK. It contains
Go mobile Java bindings and `libgojni.so` for arm64-v8a, armeabi-v7a, and x86_64,
with minimum Android API 26 and 16 KiB ELF load alignment. The original GameStream
native runtimes remain separate.

## Exact provenance

- Android wrappers/build recipe: [jqssun/android-display-mirror v0.0.33](https://github.com/jqssun/android-display-mirror/tree/6836ad7efe3e27b8db76422fdbc9b81b4973c16a), commit `6836ad7efe3e27b8db76422fdbc9b81b4973c16a`.
- Complete doubletake source: [omarroth/doubletake](https://github.com/omarroth/doubletake/tree/568a06ed32afa9fafdf229a798db0786f4d020f2), upstream submodule commit `568a06ed32afa9fafdf229a798db0786f4d020f2`.
- Go mobile: `v0.0.0-20260611195102-4dd8f1dbf5d2`, the upstream build recipe's exact version.
- `source/` retains the original sources; `patches/` records the MoonCast lifecycle
  and privacy changes. `scripts/prepare_airplay.py` applies upstream's Android
  adaptations with exact match-count checks, then the local patch.
- `module-proxy/` contains complete source ZIPs (including tests, build files,
  notices and platform variants) for all 36 modules in the frozen module graph.
  These are ordinary Go module-proxy archives, not opaque binary dependencies.
  Historical module-graph metadata is included for offline resolution as well.
  Their versions, Go `h1:` sums, archive SHA-256 and license locations are in
  `modules-lock.json`. They were acquired through `proxy.golang.org` and verified
  by Go against `sum.golang.org`. Extracted notices are additionally in `licenses/`.
- `SOURCE_LOCK.json` inventories every source, patch, test, dependency archive,
  and rebuild helper. `prebuilt-manifest.json` records compiled artifact hashes.

The source lock intentionally keeps the v0.0.33 sender independently pinned from
MoonCast's other native components. It must not be labeled v0.0.34.

## Offline rebuild

Install the toolchains separately, then provide their existing paths. The helper
never downloads or installs tools, accepts SDK licenses, creates signing keys, or
contacts receivers. It uses only the vendored file-based module proxy.

```sh
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_NDK_HOME=/path/to/android-ndk-r29
export GO_BINARY=/path/to/go1.25.10/bin/go
scripts/build_airplay.sh
python3 scripts/prepare_airplay.py
```

A non-default output path can be supplied as the sole argument. `AIRPLAY_GO_CACHE`
may point to a shared Go build cache; the default uses a temporary directory that
is removed after the build. Required tools include Python 3.11+, GNU patch, Go,
JDK, and the existing Android SDK/NDK. The tested host is Linux x86_64.

Pinned/tested tools:

- Go 1.25.10 linux/amd64. [Official archive](https://go.dev/dl/go1.25.10.linux-amd64.tar.gz),
  SHA-256 `42d4f7a32316aa66591eca7e89867256057a4264451aca10570a715b3637ba70`.
  The archive includes the compiler and standard-library source. Go's license
  and patent notice are also included in this directory's `licenses/`.
- NDK r29, revision 29.0.14206865. [Official archive](https://dl.google.com/android/repository/android-ndk-r29-linux.zip),
  SHA-256 `4abbbcdc842f3d4879206e9695d52709603e52dd68d3c1fff04b3b5e7a308ecf`.
  This is an explicit deviation from upstream's NDK r27; it reuses the already
  installed official toolchain and produces 16 KiB-aligned ELF segments.
- Android SDK platform 35 revision 2; Java compiler OpenJDK 21.0.12.1.

Toolchains are external build prerequisites and are not duplicated inside the
repository. Their archive URLs/checksums and tested versions are documented;
all application/library dependency sources are vendored. Changing toolchain
versions may change bytes and requires updating/reviewing the binary manifest.
The helper verifies the rebuilt checksum before replacing the bundled AAR.
Its canonical `/tmp/mooncast-airplay-v0.0.33/source` directory prevents Go module
replacement metadata from embedding developer paths or changing between builds.
Concurrent builds fail safely if that directory already exists; after an interrupted
build, inspect and remove only that generated directory before retrying.

## Local changes beyond the upstream build script

1. Disable verbose protocol debug logging, which upstream enables despite logging
   transport key material. The first video-frame log no longer prints frame bytes.
2. Create cancellation synchronously before the Connect worker is scheduled.
   Cancel closes each concrete control socket, including one currently blocked
   in a handshake. A generation check prevents a canceled attempt from publishing
   streams or notifying success. Error/PIN callbacks are suppressed after cancel.
3. Detach resources atomically on disconnect, then close outside the session lock.
   Both video and audio pipe writers unblock. An old audio forwarder cannot clear
   or close a replacement session's pipe.
4. Freeze dependency resolution. The original unversioned `go get` is replaced by
   the vendored exact module graph. Bind only the three supported Android ABIs.
5. Normalize AAR and nested JAR ordering, timestamps and permissions; strip the Go
   build ID, trim source paths, and fix the remaining module replacement path to
  the canonical build directory. ELF runtime bytes are retained without APK stripping.

## Binding contract

Java classes are in `airplaylib`; `go.Seq`/`go.Universe` are bundled. No additional
Maven dependency is required for the bindings. `Airplaylib.newSession(handler)`
returns `Session`. Relevant methods are:

- `connect(String host, long port, String pin, long width, long height, long fps)`
- `sendFrame(byte[] annexB, boolean keyframe)` and `sendAudio(byte[] pcm)`
- `streamWidth()`, `streamHeight()` (both `long`), `hasAudio()`, `disconnect()`
- `setAirPlay1FrameSize(long width, long height)` and `discover(long durationMs)`
- `Airplaylib.setAirPlay1Mode(boolean)` and
  `Airplaylib.setCredentialsPath(String)` (throws `Exception`)

Audio is interleaved 44.1 kHz stereo S16LE PCM. AirPlay 1 in this upstream version
negotiates no audio. Receiver-negotiated dimensions are available after connection.
Both send calls can backpressure and must stay off Android UI/render callback
threads. EventHandler callbacks arrive from Go threads and need Android thread
marshalling. AirPlay mode and credential store remain process-global: allow only
one active AirPlay session per process and terminate/reset its service
process on stop/error. Persistent pairing, if enabled by the application, belongs
only in app-private storage; never package it as source or logs.

## Validation and limits

Focused synthetic tests cover clamp/framing, idle disconnect, blocked audio/video
writers, cancellation of a blocked loopback `/info` request, immediate disconnect
before a connect goroutine can start, and rejection of a superseded generation.
They do not perform pairing, create persistent credentials, capture a real screen,
or contact a physical receiver. Race-detector results, rebuild comparison and ELF
checks are recorded in `prebuilt-manifest.json`. Two offline source builds with
fresh module caches and a shared compilation cache produced byte-identical AARs
using the recorded toolchains (2026-10-11 UTC).

No device interoperability, Apple TV pairing, receiver audio/video, long-running
streaming, security audit, or latency/quality claim follows from these checks.
The underlying upstream protocol code still needs physical receiver testing.
