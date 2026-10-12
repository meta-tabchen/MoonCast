# Current native provenance

The selected runtime was built entirely from source on 2026-10-11. It does not
link the historical prebuilt OpenSSL/Opus archives in the Moonlight repository.

## Immutable inputs

- Moonlight Android v12.2: b48494cb96bff23d8886c4775cc4f39a1075495d, GPLv3
- common-C: 874ac9548f1bd6f095ef2b435c42cdde460e7821, GPLv3
- ENet fork: aca87840b57f045a1f7f9299e4b1b9b8e2a5e2f1, MIT
- nanors: b1e3c22ca0cdc0bb83e3cd6ed1a2fc77869ed99a, MIT
- OpenSSL4.0.2: f089acdf4bc7ba94a79f4bf6eb7362c3e7d14aa9, Apache2.0
- Opus1.6.1: 22244de5a79bd1d6d623c32e72bf1954b56235be, BSD-style notices
  plus its retained patent-license references
- NDK29.0.14206865 cpu-features: all five supplied source/build/notice files
  pinned by SHA256; source headers contain BSD-style AOSP notices and NOTICE
  contains Apache2.0 text. Preserve both.

SOURCE_LOCK.json supplies source URLs and the official NDK ZIP SHA256.
SOURCE-INPUTS-SHA256SUMS inventories 7,462 exact source files, including nested
repositories and cpu-features. JAVA-SOURCES.json pins the selected Java source
archives/POMs separately. Original licenses and per-file notices are retained in
those sources and selected app assets. SDL-derived JNI helpers retain their notices.

## Recipe and actual output evidence

The independent scripts rebuild all six OpenSSL/Opus archives with NDK29 and
CMake3.31.10/OpenSSL Configure. Opus DRED/deep PLC/OSCE are disabled. They do not
run the original mobile-deps scripts (which had no separate top-level license)
or bootstrap unused neural model files. Those materials are excluded from the
release source bundle.

Each ABI then compiles the locked JNI/common-C/ENet/nanors source against its
new static archives and target-generated headers. A linker version script plus
--exclude-libs,ALL makes OpenSSL/Opus local; only 32 JNI exports remain. This avoids
interposition with separately loaded AirPlay crypto. ABI, required JNI exports,
relocations, DT_NEEDED, and 64-bit16KB alignment are verified.

The ignored native-build-manifest.json records each actual build's source-lock
digest, toolchain, static archive hashes and final runtime hashes. SOURCE_LOCK's
binary hashes are this local delivery's reference values only. A second relink
at another filesystem path produced different bytes, so CI source-builds and
verifies its own outputs rather than asserting cross-path bit identity.

## Scope and limits

Independent SDK35/AGP8.7/Gradle8.9 assemble, 34 module JVM tests, and bundled lint
passed. Whole-app and device verification belong to the integrating TV build.
This source build does not establish real host PIN/TLS, decoder/audio, latency,
or co-loaded-protocol interoperability on a device.

See scripts/SOURCE-BUILDING.md for exact preparation and packaging. The final TV
app must also distribute its own complete source, modifications, other included
dependencies, notices, and applicable build/installation instructions. This is
engineering provenance, not a legal compliance certification or release approval.
