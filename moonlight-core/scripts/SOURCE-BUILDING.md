# Full-source MoonCast GameStream core

## Supported preparation

Use Linux x86_64, Android NDK 29.0.14206865, CMake 3.31.10, GNU make,
Perl, Python 3, Git, and a POSIX shell. NDK and CMake are caller-supplied official
toolchains; these scripts do not install them or change system settings.

From the module directory:

    python3 scripts/prepare_moonlight_core.py --build --ndk /path/to/android-ndk-r29 --cmake /path/to/cmake
    python3 scripts/verify_moonlight_core.py

Preparation restores six immutable Git revisions, exact Maven source JARs/POMs,
and all five NDK cpu-features source/notice files. It refuses different or dirty
checkouts and verifies delivered Git-free sources using SOURCE-INPUTS-SHA256SUMS.
Both the installed NDK cpu-features inputs and the redistributed copy are checked.
For source-only restoration omit --build but supply --ndk. A complete extracted
source bundle already contains cpu-features, so source-only validation needs no NDK.

The script builds OpenSSL 4.0.2 and Opus 1.6.1 from source for all three ABIs,
then builds Moonlight JNI/common-C/ENet/nanors against those local archives.
It does not execute mobile-deps recipes, use their prebuilt archives, or download
neural-model blobs. Opus DRED, deep PLC, and OSCE are disabled. Dependencies target
API21; JNI targets API26. All compilation uses NDK29, unlike the mixed NDK versions
used for the historical upstream archives.

Actual outputs are copied to src/main/jniLibs and recorded in the ignored
native-build-manifest.json. Optional --native-manifest changes that record's
output location; normal Gradle packaging expects it at the default module path.
The record binds the source-lock digest, toolchain, static archives, and actual
three-ABI runtime hashes. SOURCE_LOCK.json's binary hashes are local reference
values, not a cross-filesystem reproducibility promise. Cross-path relinking was
tested and did not produce identical hashes. CI must verify the APK against its
own prepared manifest and include that manifest with the release source bundle.

## Gradle build

Java build: SDK35/build-tools35.0.0, AGP8.7.3, Gradle8.9, JDK17 or JDK21.
OkHttp JVM4.12.0 avoids upstream OkHttp5.5 Android's SDK37 requirement. Keep the
original AGP8.7 lint; no global lint override is required.

After preparation, normal :moonlight-core:assembleDebug works without Python or
readelf: its JVM preBuild guard checks all three runtime files against the actual
manifest and source-lock digest. An unprepared checkout fails rather than silently
packaging a Java-only receiver. Explicit Gradle source mode invokes the same prepare
script and still validates the fresh manifest:

    ./gradlew :moonlight-core:assembleDebug -PmoonlightNativeFromSource -PmoonlightNdkPath=/path/to/android-ndk-r29 -PmoonlightCmakePath=/path/to/cmake

Do not commit generated .so files or the actual build manifest. CI source-builds
before Gradle and packages its same-run outputs. The source input archive has a
native/ root; extract it at the module root.

## Runtime isolation and validation limits

The link version script exports only 32 MoonBridge JNI functions. Static OpenSSL
and Opus symbols are local (--exclude-libs,ALL plus local:*), avoiding dynamic
interposition with the TV AirPlay receiver's separately loaded libcrypto.so.
Verification rejects public crypto/Opus symbols, their dynamic relocations, and
shared libcrypto/libssl/libopus dependencies. 64-bit LOAD alignment is at least16KB;
armeabi-v7a is4KB. This is static link evidence, not a device coexistence test.

The tested source rebuild and SDK35 assemble/unit-tests/lint do not establish real
host PIN/TLS interoperability, TV codec behavior, audio/latency, or device playback.

## Corresponding source and notices

native/upstream contains full pinned Moonlight source with recursive common-C,
ENet and nanors sources, excluding Git metadata and unused .a/.apk artifacts.
native/native-sources contains exact OpenSSL, Opus, and NDK cpu-features sources;
native/java-sources contains exact selected Maven source JARs and POMs. Original
per-file notices and licenses are retained. Module src/, SOURCE_PATCHES.diff,
SOURCE_LOCK.json, inventories, and scripts supply local modifications and recipes.

A redistributed TV APK also needs the complete MoonCast application source,
integration changes, other included protocol/dependency sources, and appropriate
build/installation instructions. This module alone is not the complete TV app's
corresponding source. Keep MoonCast's application ID; do not present it as an
official Moonlight build.
