# Browser preview source and notices

Keep this document, `native/webrtc/SOURCE_LOCK.json`, and the seven
`app/src/main/assets/browser-webrtc-*` notice files alongside the browser APK
download. The notices are also packaged in the APK and checked byte for byte by
`scripts/verify_unified_apk.py`.

The supplied source is **MoonCast application source and build instructions,
with version-pinned references to externally hosted WebRTC SDK source and
dependency definitions. It excludes the full SDK dependency checkout; the
prebuilt native SDK has not been independently rebuilt.** Application build
instructions are in [unified-build.md](unified-build.md). SDK checkout and example
build instructions are in [native/webrtc/README.md](../native/webrtc/README.md).

The unmodified SDK is `io.github.webrtc-sdk:android:144.7559.15`, AAR SHA-256
`fc7c8d027eb33860ccbb7847988f7f942d44a92d1f575348bba6c0250cde4071`.
Its fixed external source coordinates are:

| Input | Fixed source |
| --- | --- |
| Android wrapper | [0c62f604c97087664e412ec04e0ec79721a263b1](https://github.com/webrtc-sdk/android/tree/0c62f604c97087664e412ec04e0ec79721a263b1), [official v144.7559.15 release](https://github.com/webrtc-sdk/android/releases/tag/v144.7559.15) |
| WebRTC fork | [30d5e63ae91da483e06577b5c35ee91cc5e5c3db](https://github.com/webrtc-sdk/webrtc/tree/30d5e63ae91da483e06577b5c35ee91cc5e5c3db) |
| Dependency definitions | [that commit's DEPS](https://github.com/webrtc-sdk/webrtc/blob/30d5e63ae91da483e06577b5c35ee91cc5e5c3db/DEPS) |
| Chromium | [dd64a1bc0f36e9b9c690a8a00f1328ad68805e27](https://chromium.googlesource.com/chromium/src/+/dd64a1bc0f36e9b9c690a8a00f1328ad68805e27) |
| Chromium third_party, including JNI Zero | [a3b630c291f1cf3b711687b1c4be322d3015512e](https://chromium.googlesource.com/chromium/src/third_party/+/a3b630c291f1cf3b711687b1c4be322d3015512e) |
| Build definitions | [be1a8f6dcd7df7e46320192c5e2f364e50d79bbf](https://github.com/webrtc-sdk/build/tree/be1a8f6dcd7df7e46320192c5e2f364e50d79bbf) |
| BoringSSL | [b94d71f87ff943a617d77f3ff029f9a01a1ec6bc](https://boringssl.googlesource.com/boringssl/+/b94d71f87ff943a617d77f3ff029f9a01a1ec6bc) |

The AAR itself supplies no license/NOTICE assets. MoonCast preserves WebRTC's
BSD license, PATENTS, the fork NOTICE, the wrapper MIT license and the wrapper's
original third-party collection. That collection's old BoringSSL attribution
differs from the pinned revision's Apache-2.0 license. The precise BoringSSL and
JNI Zero license files are therefore included separately with their source URLs
and SHA-256 values in `SOURCE_LOCK.json`. The collection's 30 headings are not a
verified linked-component SBOM. The wrapper MIT license does not replace native
dependency notices. No SDK binary or source modifications were made by MoonCast.

GPLv3 section 6(d) permits clear directions to equivalent corresponding-source
access on another server; it does not require a single enormous offline archive
or byte-identical rebuilding. A distributor remains responsible for appropriate
source coverage and continued access. These fixed coordinates and build guidance
record the known source mapping; they do not claim independently verified native
reproducibility or a complete generated GN link graph.
