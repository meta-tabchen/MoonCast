# Third-party code

MoonCast is distributed under GPLv3. The native core is reused, not represented as newly authored code.

- Mirror (jqssun/android-display-mirror), GPLv3, commit `d7eea1b9e44eea38850bffa32ed0623506a2aa9a`.
- Sunshine (LizardByte/Sunshine), GPLv3, upstream version `v2025.122.141614`, as adapted by Mirror. Original source headers are preserved.
- Moonlight common C, GPLv3; ENet, MIT; nanors, MIT; Opus, BSD; Simple-Web-Server, MIT; libwordexp, BSD-2-Clause-FreeBSD; openssl-cmake, MIT. Their corresponding source and licenses are included under `native/sunshine/dependencies` (libwordexp's notice is in its source headers).
- Boost 1.86.0, Boost Software License; OpenSSL 3.5.5, Apache 2.0. The upstream CMake build downloads these source archives.
- Bouncy Castle 1.77, MIT, obtained via Maven. Used only to generate the local host certificate. GameStream TLS and client certificate validation run in the Sunshine/OpenSSL core.
- Android Gradle Plugin and Gradle are build tooling; the Gradle wrapper is reused from the upstream project.

Native release bootstrap provenance and per-library hashes are recorded in `native/prebuilt-manifest.json`. The accompanying source corresponds to that pinned Mirror release. The official upstream APK's Go/AirPlay library, DisplayLink library, Java code and resources are excluded.

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
