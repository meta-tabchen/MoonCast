# Build and verification

## Android app with pinned native runtimes

Requirements: JDK 17, Android SDK Platform 35, Build Tools 35.0.0, Python 3, and network access for initial Gradle/Maven downloads. The checked-in Gradle wrapper uses Gradle 8.9 and AGP 8.7.3.

```sh
git clone https://github.com/meta-tabchen/MoonCast.git
cd MoonCast
# Set ANDROID_HOME, or create an uncommitted local.properties with sdk.dir.
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug
python scripts/run_unit_tests.py
python scripts/verify_apk.py app/build/outputs/apk/release/app-release.apk
```

Windows: `gradlew.bat` instead of `./gradlew`. For Windows environments with long Unix-domain socket paths in newer JDK 17 updates, set `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<short-existing-temp-directory>` if Gradle reports socket path length errors.

Outputs: `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release.apk`. **The release variant currently uses the local debug signing configuration.** Do not call it production-signed. For production distribution, configure a private release keystore outside the repository; never commit signing files, passwords, or generated phone pairing identities.

The default build uses nine pinned Sunshine/OpenSSL `.so` libraries plus three separately compiled session-bridge `.so` libraries across arm64-v8a, armeabi-v7a, and x86_64. Their source and fixed dependency sources are vendored under `native/sunshine/`. Provenance and checksums are in `native/prebuilt-manifest.json` and `native/SOURCE_LOCK.json`. These are intentional runtime inputs, not build caches. The session bridge source, build recipe, CI run and binary hashes are pinned in `native/session-bridge-manifest.json`; `scripts/build_session_bridge.py` builds it with NDK 27.0.12077973 on Linux.

To restore pinned runtimes if absent:

```sh
python scripts/prepare_native.py /path/to/mirror-v0.0.34.apk
```

Without an APK argument the helper downloads the fixed upstream release and checks its SHA-256. It extracts only the intended runtimes, not the whole upstream app.

## Native-source build (not validated for this preview)

Recommended environment: Linux, Android NDK `27.0.12077973`, CMake `3.31.1`, Python, Perl, GNU make, and patch.

```sh
./gradlew :app:assembleDebug -PnativeFromSource
```

This selects the vendored C/C++ source instead of `jniLibs`. CMake downloads/builds Boost 1.86.0 and OpenSSL 3.5.5. A successful default APK build is not evidence that the native-source build passes; see [validation](validation.md).

## What the checks mean

- `run_unit_tests.py`: pure Java geometry/crop stabilization/input-coordinate, media-volume recovery, statistics, live HTTP byte-range/shutdown and display-power lease logic. Temporary classes are removed when the process finishes.
- `verify_apk.py`: pinned ELF bytes, dependencies, JNI exports, ABI coverage, and absence of excluded upstream payloads. It does not test capture or Moonlight interoperability.
- `lintDebug`: Android static checks. Warnings remain and are reported honestly.
- `apksigner verify --verbose <apk>` and `zipalign -c -v 4 <apk>`: signature/alignment checks using Android Build Tools.
- Device test entry `app/src/androidTest/.../RenderingInstrumentation.java`: GPU rendering tests, including paused-frame sampling. These need an actual Android device; they are not run by desktop-only CI.

## Release procedure

1. Update version code/name and changelog. Run app build, lint, unit checks, and APK verification.
2. Keep runtime behavior tests separate from static results. Record actual phone and receiver combinations.
3. Package corresponding source with `python scripts/package_source.py`; omit caches and machine identities.
4. Tag the reviewed commit. Attach APK, corresponding source archive, and `SHA256SUMS` to a GitHub prerelease while preview signing/experimental status remains.
5. Download assets from the published release and compare SHA-256. CI debug builds use ephemeral runner keys; their signatures differ from locally delivered previews.

## Repository layout

| Path | Purpose |
| --- | --- |
| `app/` | Android app, JNI ABI adapters, instrumentation |
| `native/sunshine/` | Pinned Android native core and fixed source dependencies |
| `native/*json` | Native source lock and binary provenance |
| `scripts/`, `tests/` | Build/delivery helpers and pure Java checks |
| `docs/` | English guides, Chinese usage, comparison, architecture, validation |
| `.github/` | CI and issue/PR templates |

## Device suites and optional native client fixture

Build/install `:app:assembleDebugAndroidTest`, then run:

```sh
adb shell am instrument -w -e suite multi-output com.mooncast.host.test/com.mooncast.host.RenderingInstrumentation
```

Other suite names: `gpu`, `profiles`, `app-display`, `root-input`, `file-cinema`, `panel-power`.
App-display/panel-power require an authorized, running Shizuku service; root-input requires
an explicitly approved Root broker. These suites create generated content, not personal media.
Panel-power invokes a physical panel API and tests a 30-second lease; use a dedicated test
phone/emulator with a recovery route. It does not measure panel state or power consumption.

The optional `native-stream` suite needs Surface-input hardware AVC, Shizuku, and an x86_64
device with the official Moonlight 12.2 client-core fixture:

```sh
python scripts/prepare_stream_test_client.py /path/to/official-moonlight-v12.2.apk
```

The helper checks fixed APK/library hashes and writes an ignored debug-only library. It
never enters release APKs or source archives. The two loopback clients use separate processes
and actual MediaCodec decoders. MuMu cannot run this suite because its hardware AVC encoder
is absent and software OMX rejects the host configuration; GPU fan-out is tested separately.
