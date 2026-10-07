# Working on MoonCast

Read README.md, docs/building.md, docs/architecture.md, and docs/validation.md before changing behavior.

- Native source and runtime binaries are pinned inputs. Preserve license headers and dependency provenance. A source-only native change does not affect default builds; keep the source, binaries, and manifests consistent and explain rebuild evidence.
- Main app code is under app/src/main/java/com/mooncast/host. The io/github/jqssun adapters preserve the upstream JNI ABI.
- Maintain English default and Chinese UI resources together. Keep resource names descriptive. Lower-level diagnostics may remain partially translated; do not claim complete localization without checking.
- Capture, audio, input, and Root permissions are separate choices. Input defaults off. Preserve stop/error cleanup, held key/pointer release, and volume recovery.
- Run appropriate app build/lint, scripts/run_unit_tests.py, and scripts/verify_apk.py checks. Device-dependent changes require device evidence or an explicit untested note.
- Never present compilation/static verification as device interoperability, security, latency, or lossless-quality validation.
- Exclude local.properties, caches, signing keys, certificates, phone serials, pairing identities, private logs and personal screenshots from commits and release archives.
- Publish preview APKs with corresponding source and SHA256SUMS. Keep debug-key signing and experimental feature limits visible. Do not overwrite historical release assets.
