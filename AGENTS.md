# Working on MoonCast

Read README.md, docs/unified-build.md, docs/browser-receiver.md and docs/browser-validation.md before changing current behavior. The primary branch is main and the current app target is :unified; docs/building.md and docs/validation.md retain historical sender evidence.

- Native source and runtime binaries are pinned inputs. Preserve license headers and dependency provenance. A source-only native change does not affect default builds; keep the source, binaries, and manifests consistent and explain rebuild evidence.
- Main app code is under app/src/main/java/com/mooncast/host. The io/github/jqssun adapters preserve the upstream JNI ABI.
- Maintain English default and Chinese UI resources together. Keep resource names descriptive. Lower-level diagnostics may remain partially translated; do not claim complete localization without checking.
- Capture, audio, input, and Root permissions are separate choices. Input defaults off. Preserve stop/error cleanup, held key/pointer release, and volume recovery.
- Run :unified build/lint, scripts/run_unit_tests.py, scripts/check_localization.py, scripts/verify_unified_apk.py and scripts/verify_community_identity.py as applicable. Check the browser page and Android sender separately; preserve the required JNI Zero JniInit entry in shrunk APKs. Device-dependent changes require device evidence or an explicit untested note.
- Never present compilation/static verification as device interoperability, security, latency, or lossless-quality validation.
- Exclude local.properties, caches, private production signing keys, private credentials, phone serials, pairing identities, private logs and personal screenshots from commits and release archives. The sole public-test exception is the fixed community signing fixture initialized once by the owner workflow and recorded in signing/community; retain its pinned identity in Git, never regenerate it on builds or substitute a temporary key. Do not include its PKCS12 signing material in Release source archives. Public certificate fingerprints and identity manifests are allowed.
- Publish preview APKs with corresponding source and SHA256SUMS. Keep the fixed public community-test signing policy and experimental feature limits visible. Production must use a distinct package and confidential identity; it must never fall back to community signing. Do not overwrite historical release assets.

- Current unified source is on `main`. Use docs/unified-build.md for builds and docs/browser-validation.md for current browser evidence. Current app version is 0.6.0/code56. Keep versionName numeric (0.x.x), without release-stage or protocol suffixes; see docs/versioning.md. Historical browser preview.4 is code55 and preview.3 is code54; docs/community-validation.md records historical 0.5.1/code51 and unified-validation.md / unified-artifacts.json record historical 0.5.0.
- `-PmooncastUnsignedVerification=true` is an explicit unsigned verification build with application ID `com.mooncast.community.verification`, not an installable signed community release or an upgrade path. Normal community builds must fail when the fixed fixture or hash/certificate checks are missing or wrong.

- Browser SDK provenance is in native/webrtc/SOURCE_LOCK.json and docs/browser-release-source.md. The application source bundle supplies fixed external SDK source/DEPS coordinates and build mapping, not a full offline SDK dependency checkout; the prebuilt native SDK has not been independently rebuilt. Preserve original SDK notices and distinguish real Android capture evidence from synthetic browser-only fixtures.

Browser preview.4 QR and quality behavior is documented in [docs/browser-qr-quality.md](docs/browser-qr-quality.md). Target ceilings and device capability reports are not measured throughput or sustained quality. Final build identity and artifact verification must be recorded before describing a candidate as published.

Final preview.4 source/runtime: c0936675193c49e3bcd64bb0a76171059b9f9210. Its delta from d44a4c4 is icon-only; no additional device test was requested or performed. Pre-icon quality observations and code54 media remain separately scoped.
