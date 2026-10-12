# Third-party code

MoonCast is distributed under GPLv3. The native core is reused, not represented as newly authored code.

- Mirror (jqssun/android-display-mirror), GPLv3, commit `d7eea1b9e44eea38850bffa32ed0623506a2aa9a`.
- Sunshine (LizardByte/Sunshine), GPLv3, upstream version `v2025.122.141614`, as adapted by Mirror. Original source headers are preserved.
- Moonlight common C, GPLv3; ENet, MIT; nanors, MIT; Opus, BSD; Simple-Web-Server, MIT; libwordexp, BSD-2-Clause-FreeBSD; openssl-cmake, MIT. Their corresponding source and licenses are included under `native/sunshine/dependencies` (libwordexp's notice is in its source headers).
- Boost 1.86.0, Boost Software License; OpenSSL 3.5.5, Apache 2.0. The upstream CMake build downloads these source archives.
- Bouncy Castle 1.77, MIT, obtained via Maven. Used only to generate the local host certificate. GameStream TLS and client certificate validation run in the Sunshine/OpenSSL core.
- Android Gradle Plugin and Gradle are build tooling; the Gradle wrapper is reused from the upstream project.

Native release bootstrap provenance and per-library hashes are recorded in `native/prebuilt-manifest.json`. The accompanying source corresponds to that pinned Mirror release. That historical native bootstrap excludes the upstream application UI and DisplayLink library. The current unified phone sender separately includes the pinned Go/AirPlay source and AAR described in the AirPlay sender section below.

Sources: https://github.com/jqssun/android-display-mirror, https://github.com/LizardByte/Sunshine, https://github.com/moonlight-stream/moonlight-common-c, https://www.bouncycastle.org/licence.html.

## Shizuku API

The Android client uses `dev.rikka.shizuku:api` and `provider` version 13.1.5, under the MIT license. See https://github.com/RikkaApps/Shizuku-API. Shizuku Manager is a separately installed application; its APK is not redistributed by MoonCast.

Optional loopback tests download Moonlight Android 12.2's GPLv3 client core from its
official release, with fixed APK/library SHA-256 values. Corresponding source is at
https://github.com/moonlight-stream/moonlight-android/tree/v12.2. This test dependency
is excluded from Git, corresponding release archives, and release APKs. Debug-only
Java adapters run it in two isolated processes; they are not the Moonlight UI.

MIT License

Copyright (c) 2021 RikkaW

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Physical display-power compatibility research

MoonCast's independently written privileged reflection adapter uses Android's hidden
physical-display power APIs. Compatibility research references scrcpy's
[SurfaceControl](https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/wrappers/SurfaceControl.java)
and [DisplayControl](https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/wrappers/DisplayControl.java)
wrappers, maintained by Genymobile under Apache-2.0. No scrcpy binary is bundled.

## AndroidX FileProvider (TV APK receive feature)

The TV module explicitly uses AndroidX Core 1.8.0 (already present in its dependency graph), including FileProvider, under Apache License 2.0. No upstream source has been copied into the feature. See https://developer.android.com/jetpack/androidx/releases/core and https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt. Apache-2.0 components may be included in GPLv3 works; preserve their bundled license notices.

## AirPlay sender


The phone sender incorporates Mirror v0.0.33 (commit `6836ad7efe3e27b8db76422fdbc9b81b4973c16a`)
Go bindings and Android compatibility patches for doubletake commit
`568a06ed32afa9fafdf229a798db0786f4d020f2`. Mirror is GPL-3.0-only; doubletake
retains its GPL/LGPL notices and individual dependency licenses. Original source,
Go dependency sources, license texts, explicit MoonCast safety patches, build
recipe, toolchain pins and AAR hashes are under `native/airplay-sender/`.
The MediaProjection/GLES/MediaCodec/audio ownership adapter is MoonCast code.
No claim of Apple certification, DRM support, lossless screen encoding or
physical receiver interoperability follows from a source build.

## Unified packaging

Receiver OpenSSL is packaged as libtvcryp.so with a matching DT_NEEDED/SONAME change; its license and source remain the pinned OpenSSL input. See scripts/prepare_unified_native.py and native/unified-receiver-manifest.json. No third-party code license is changed by combining application roles.

The publication Git checkout restores pinned binary inputs and complete generated native-source trees from the matching complete-source Release asset. Original license texts and public upstream test fixtures are preserved in that asset; see `scripts/release-inputs.json` and `docs/unified-build.md`.
