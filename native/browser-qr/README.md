# Offline browser session QR dependency

`BrowserQrCode` uses **com.google.zxing:core:3.5.3**, fetched from
[Maven Central](https://repo.maven.apache.org/maven2/com/google/zxing/core/3.5.3/).
Its 607,650-byte binary JAR SHA-256 is
`8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82`.
`SOURCE_LOCK.json` records all source, metadata and attribution hashes;
`gradle/browser-qr.gradle` checks these inputs before app/phone-ui builds and
strictly constrains the dependency version. No new Android permission, hosted
QR API, network request or session-link logging is needed.

Upstream [tag zxing-3.5.3](https://github.com/zxing/zxing/releases/tag/zxing-3.5.3)
resolves to commit
[`f0f1a94439db0300ff0c4618f4e0a41aa630aeac`](https://github.com/zxing/zxing/tree/f0f1a94439db0300ff0c4618f4e0a41aa630aeac).
The core POM and its parent declare no runtime dependencies; JUnit is test-only.
The binary and source JARs contain no LICENSE/NOTICE entries, so the exact
upstream root files are additionally packaged in APK assets:

- `browser-qr-ZXING-LICENSE.txt`: [original LICENSE](https://github.com/zxing/zxing/blob/f0f1a94439db0300ff0c4618f4e0a41aa630aeac/LICENSE).
- `browser-qr-ZXING-NOTICE.txt`: [original NOTICE](https://github.com/zxing/zxing/blob/f0f1a94439db0300ff0c4618f4e0a41aa630aeac/NOTICE).

Core is Apache-2.0 licensed. The full project LICENSE additionally includes a
jai-imageio appendix and NOTICE includes Barcode4J/JCommander attribution;
preserving those original files does not claim these are runtime dependencies
of this core JAR. Upstream source files and notices are unmodified.

## Source and offline validation

`core-3.5.3-sources.jar` (507,643 bytes; SHA-256
`f983454400d73652ad5236413ba7eba48072e042982a84b10e1468506f4b28ae`)
is intentionally bundled with the core and parent Maven POMs. Its 238 Java files
match the pinned commit's `core/src/main/java` files byte-for-byte; that directory
has no non-Java production resources. This supplies all core production sources,
not the entire upstream repository or its tests. The included test runner can
compile these sources offline with JDK 17 targeting Java 8, without Maven plugins
or runtime dependencies. It does not claim byte-for-byte reproduction of the
published JAR's archive metadata.

```sh
python3 scripts/run_browser_qr_tests.py --jar /path/to/core-3.5.3.jar
```

The runner first verifies the exact JAR and bundled-input hashes, then tests the
integration against the published binary and the locally recompiled sources.
It uses only synthetic LAN links and a minimal Bitmap pixel-storage test double.
It checks complete query/token and Unicode roundtrips with ZXing's decoder,
opaque black/white pixels, four-module quiet zones, integer scaling, exact output
dimensions, error-correction level and explicit rejection bounds. These are
pixel-level tests, not a real camera scan or Android UI test.

The source archive and notices pass `package_tv_release.safe_source_name`;
once committed, `package_unified_source.py` includes them as Git blobs. They are
not build-cache inputs and contain no keys. Verify their presence/hashes in the
actual release source ZIP; no archive was published as part of this change.
This component's source coverage does not resolve the separate WebRTC native
corresponding-source review.

## Rendering contract

`BrowserQrCode.encode(link, pixels)` returns exactly `pixels × pixels` with no
interpolation, medium (M) error correction, UTF-8 encoding and at least four
white modules around the symbol. It accepts 128–1024 pixels and valid UTF-8
payloads up to 2048 bytes. At least two pixels per module are required; a dense
payload at too small a requested size throws. Invalid size/text and encoding
failures throw `IllegalArgumentException` without the link or token in the
message/cause, allowing the UI to retain its copy-link fallback. The text is
neither trimmed nor rewritten before encoding.
