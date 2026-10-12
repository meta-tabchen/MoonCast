# Third-party source and runtime notices

This module is an independent MoonCast integration of Moonlight Android v12.2,
not an official Moonlight build. New MoonCast code is GPL-3.0-or-later; retain the
upstream GPLv3 license and per-file notices. Exact source pins and locally built
runtime hashes are in SOURCE_LOCK.json and NATIVE-PROVENANCE.md.

Native runtime incorporates Moonlight JNI/common-C (GPLv3), ENet and nanors (MIT),
Opus 1.6.1 (BSD-family and retained patent-license references), OpenSSL 4.0.2
(Apache-2.0), Android NDK cpu-features (BSD/AOSP notices), and SDL-derived helpers
(zlib-style notices). License texts are embedded under assets/moonlight and remain
in their full source trees. Opus neural features are disabled in the pinned inputs.

Java runtime coordinates, artifact hashes, source JAR hashes, and source POMs are
provided in JAVA-DEPENDENCIES.json and native/java-sources/SOURCES.json:

- Bouncy Castle bcprov-jdk18on 1.85.2: retained Bouncy Castle license (MIT-style)
- OkHttp JVM 4.12.0, Okio JVM 3.6.0: Apache-2.0
- Kotlin stdlib/common/JDK7/JDK8 1.9.10: Apache-2.0
- JetBrains annotations 13.0: Apache-2.0

The AAR contains the module's own classes and native shared libraries. Java Maven
runtime dependencies are resolved by the consuming app; a final APK/source bundle
must retain the complete exact dependency set and these notices. Do not substitute
an AAR alone for corresponding source. This is a technical attribution record,
not a legal opinion, security certification, or release approval.

Build-tool binaries (JDK, Gradle, Android SDK/NDK) are not included in the source
bundle. Their version requirements and the source/runtime build recipe are included.
Original dependency provenance is retained in NATIVE-PROVENANCE.md. The selected
runtime now uses fully source-built OpenSSL/Opus archives and independent build
scripts. It does not use or include unlicensed mobile-deps scripts or unused Opus
neural-model material. See native/dependency-rebuild/BUILD-RESULT.md and
native/BUILDING.md for the tested source-only build.
