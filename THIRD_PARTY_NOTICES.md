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
