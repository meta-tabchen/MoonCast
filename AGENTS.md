# Working on MoonCast

Read README.md, docs/building.md, docs/architecture.md, and docs/validation.md before changing behavior.

- Native source and runtime binaries are pinned inputs. Preserve license headers and dependency provenance. A source-only native change does not affect default builds; keep the source, binaries, and manifests consistent and explain rebuild evidence.
- Main app code is under app/src/main/java/com/mooncast/host. The io/github/jqssun adapters preserve the upstream JNI ABI.
- Maintain English default and Chinese UI resources together. Keep resource names descriptive. Lower-level diagnostics may remain partially translated; do not claim complete localization without checking.
- Capture, audio, input, and Root permissions are separate choices. Input defaults off. Preserve stop/error cleanup, held key/pointer release, and volume recovery.
- Run appropriate app build/lint, scripts/run_unit_tests.py, and scripts/verify_apk.py checks. Device-dependent changes require device evidence or an explicit untested note.
- Never present compilation/static verification as device interoperability, security, latency, or lossless-quality validation.
- Exclude local.properties, caches, private production signing keys, private credentials, phone serials, pairing identities, private logs and personal screenshots from commits and release archives. The sole public-test exception is the fixed community signing fixture initialized once by the owner workflow and recorded in signing/community; retain its pinned identity in Git, never regenerate it on builds or substitute a temporary key. Do not include its PKCS12 signing material in Release source archives. Public certificate fingerprints and identity manifests are allowed.
- Publish preview APKs with corresponding source and SHA256SUMS. Keep the fixed public community-test signing policy and experimental feature limits visible. Production must use a distinct package and confidential identity; it must never fall back to community signing. Do not overwrite historical release assets.

- Current unified source is on `unified/community`. Use docs/unified-build.md and docs/community-validation.md for current builds and evidence; unified-validation.md and unified-artifacts.json are historical 0.5.0 records.
- `-PmooncastUnsignedVerification=true` is an explicit unsigned verification build with application ID `com.mooncast.community.verification`, not an installable signed community release or an upgrade path. Normal community builds must fail when the fixed fixture or hash/certificate checks are missing or wrong.
