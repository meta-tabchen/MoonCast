# Building the unified preview

Use JDK17, Gradle8.9/AGP8.7.3, SDK35 and Build Tools35.0.0. Local module dependencies
retain the phone and TV Java resource namespaces. Shared APK transfer code is built
once in transfer-core. Legacy app/tv modules remain available for historical checks.

    python3 scripts/restore_release_inputs.py
    python3 scripts/prepare_airplay.py --verify-sources
    python3 scripts/prepare_airplay.py --verify-aar app/libs/airplaylib.aar
    python3 scripts/prepare_unified_native.py
    gradle :unified:assembleRelease :unified:lintRelease -PmooncastAbi=arm64-v8a

mooncastAbi accepts arm64-v8a, armeabi-v7a or x86_64; omit for a larger universal APK.
Each single-ABI APK contains both roles. Release and debug unified APKs are unsigned;
use an authorized test or release identity outside the checkout. No key is shipped.

## Native isolation and corresponding source

Phone libraries remain pinned originals; Go sender source and lock files moved to
native/airplay-sender to distinguish the TV receiver in native/airplay. Sender source
and AAR bytes are unchanged; path-only helper edits are re-pinned in the migrated lock.

Receiver sources include the two audio transition patches and three rebuilt ABIs.
First use scripts/build_airplay_patched.py with NDK27/CMake3.22.1, as documented in
tv-airplay-audio-transitions.md. Then prepare_unified_native.py deterministically
changes only the exact ELF DT_NEEDED/DT_SONAME string libcrypto.so to the equal-length
libtvcryp.so and gives that library a distinct filename. It never modifies executable
code, symbols, source archives or the original standalone binaries. The generated
manifest pins source and output hashes. No pickFirst or random OpenSSL substitution
is used. Receiver JNI stays in :receiver; phone native hosting stays in :stream/:host.
The phone does not require a second libc++ runtime; receiver libc++ is pinned once.

Include all original dependency sources, patches, migration locks, generated receiver
binaries/manifest, helper scripts and licenses in the corresponding-source archive.
Standalone packaging scripts target their historical products and are not the unified
release packager. Never publish test keys, Android SDK caches, device identifiers or
private logs. Native runtime load tests are separate from real streaming acceptance.

For the bundled Sunshine dependency archives and isolated local-source preparation, see
[sender-source-inputs.md](sender-source-inputs.md). Create a full committed source archive
with `python3 scripts/package_unified_source.py --output /path/to/new-source.zip`.

## Restoring a Git checkout

The publication branch keeps application source, recipes, manifests and licenses in Git. Large/new binary inputs and the pinned generated corresponding-source tree are supplied in the exact complete-source Release ZIP rather than duplicated in Git. `scripts/release-inputs.json` authenticates small manifests under `scripts/release-inputs/`; together they list every restored path, SHA256, size and mode. Do not replace missing libraries with arbitrary upstream builds.

`python3 scripts/restore_release_inputs.py` downloads the public `unified-v0.5.0-preview` complete-source asset from `meta-tabchen/MoonCast`, verifies its exact size and SHA256, and restores only listed missing paths. Existing matching files are reused; any modified existing target aborts instead of being overwritten. It does not extract application source or private keys over your checkout. Public upstream OpenSSL test fixtures remain part of the pinned corresponding source.

For offline use: `python3 scripts/restore_release_inputs.py --archive /path/to/MoonCast-0.5.0-unified-complete-source.zip`. Add `--verify-only` to validate without writing. An unpublished draft asset is not anonymously downloadable; CI waits up to five minutes for publication and otherwise fails clearly. Retry CI after the Release is public. A complete source ZIP already contains these inputs; its original source snapshot is preserved unchanged, while the publication branch adds these restoration instructions.

The GitHub workflow restores these inputs before verification. Its output is unsigned and intended only for CI inspection, not direct installation. The downloaded preview APK uses the disclosed disposable test certificate; no private signing key is published and future signature continuity is not promised.
