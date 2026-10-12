# Build the community app / 构建社区版

Current source: [`unified/community`](https://github.com/meta-tabchen/MoonCast/tree/unified/community). Use JDK 17, Gradle 8.9 / AGP 8.7.3, Android SDK 35 and Build Tools 35.0.0. Each single-ABI APK contains both phone and TV roles. Historical 0.5.0 build results are in [the historical record](unified-validation.md); current evidence is in [community validation](community-validation.md).

## Full Git checkout: fixed community signing

```sh
git clone --branch unified/community https://github.com/meta-tabchen/MoonCast.git
cd MoonCast
python3 scripts/restore_release_inputs.py
python3 scripts/prepare_airplay.py --verify-sources
python3 scripts/prepare_airplay.py --verify-aar app/libs/airplaylib.aar
python3 scripts/prepare_unified_native.py
gradle :unified:assembleRelease :unified:lintRelease -PmooncastAbi=arm64-v8a
python3 scripts/verify_community_identity.py unified/build/outputs/apk/release/unified-release.apk
```

`mooncastAbi` accepts `arm64-v8a`, `armeabi-v7a` or `x86_64`; omit it for a larger universal build. This does not promise every ABI is published as a release download.

The normal community build uses `com.mooncast.community`, versionCode 51 for the initial community release, and the fixed public test fixture initialized by the owner once in commit `cd39378fceeea1814df1b3e849d1588081aa7735`. Certificate SHA-256: `290e4372fff33c00848758b12275936900b2eccad053dc63c18622e1a1222bb2`. Git supplies the fixture, identity manifest and checksum records; the build verifies them. Missing material, changed bytes or an unexpected certificate must fail. Never regenerate a replacement on each build.

For subsequent releases add `--previous /path/to/previous-community.apk` to the identity verifier to check a matching identity and increasing versionCode. Old 0.5.0 is a different package with an unavailable disposable signing identity; it requires a one-time migration and cannot serve as a community in-place-update fixture.

The public test key provides repeatable update compatibility, not publisher authentication. Anyone can use it. Production builds are deliberately disabled until a distinct package and confidential identity are implemented; community signing must never be a production fallback. [Signing policy](../signing/community/README.md).

## Release source archive: unsigned verification

The Release corresponding-source archive intentionally **omits the MoonCast app-signing PKCS12 fixture**, including the public community test key. It contains source and pinned dependency/build inputs, not a promise of out-of-the-box signed release reproduction. For a source-only verification build:

```sh
python3 scripts/prepare_airplay.py --verify-sources
python3 scripts/prepare_airplay.py --verify-aar app/libs/airplaylib.aar
python3 scripts/prepare_unified_native.py
gradle :unified:assembleRelease :unified:lintRelease -PmooncastAbi=arm64-v8a -PmooncastUnsignedVerification=true
```

This explicit mode uses the distinct application ID `com.mooncast.community.verification` and leaves the APK unsigned. It is not a signed release download, cannot update the community app, and does not invoke a temporary signing-key fallback. For normal signed community builds, use the full Git checkout and its owner-initialized fixture; verify the fixed initialization commit and the repository's byte/certificate checksum records. Do not replace missing keys with newly generated material or include signing material in a source ZIP.

完整 Git checkout 使用用户一次性初始化的固定公开测试身份；缺失或校验不符必须失败。Release 对应源码包刻意不含 MoonCast 应用的 PKCS12 签名文件，可显式加 `-PmooncastUnsignedVerification=true` 编译独立包名的无签名验证产物。验证产物不是安装发布版，也不能覆盖社区版。每版重新生成身份、静默回退签名或把社区密钥用于生产均不允许。

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
release packager. Release source archives exclude the MoonCast app-signing PKCS12 fixture. Never publish private production keys, Android SDK caches, device identifiers or
private logs. The owner-initialized public community Git fixture is a narrow exception described above. Native runtime load tests are separate from real streaming acceptance.

For the bundled Sunshine dependency archives and isolated local-source preparation, see
[sender-source-inputs.md](sender-source-inputs.md). Create a full committed source archive
with `python3 scripts/package_unified_source.py --output /path/to/new-source.zip`.

## Restoring pinned legacy dependency inputs

The community branch keeps application source, recipes, manifests and licenses in Git. Large/new binary inputs and the pinned generated corresponding-source tree are supplied in the exact complete-source Release ZIP rather than duplicated in Git. `scripts/release-inputs.json` authenticates small manifests under `scripts/release-inputs/`; together they list every restored path, SHA256, size and mode. Do not replace missing libraries with arbitrary upstream builds.

`python3 scripts/restore_release_inputs.py` downloads the public `unified-v0.5.0-preview` complete-source asset from `meta-tabchen/MoonCast`, verifies its exact size and SHA256, and restores only listed missing paths. Existing matching files are reused; any modified existing target aborts instead of being overwritten. It does not extract application source or private keys over your checkout. Public upstream OpenSSL test fixtures remain part of the pinned corresponding source.

For offline use: `python3 scripts/restore_release_inputs.py --archive /path/to/MoonCast-0.5.0-unified-complete-source.zip`. Add `--verify-only` to validate without writing. An unpublished draft asset is not anonymously downloadable; CI waits up to five minutes for publication and otherwise fails clearly. Retry CI after the Release is public. A complete source ZIP already contains these inputs; its original source snapshot is preserved unchanged, while the publication branch adds these restoration instructions.

The restoration asset remains the historical 0.5.0 complete-source ZIP because these inputs are pinned dependencies. It does not make current application code or artifacts version 0.5.0. Normal community builds use the fixed signing identity described above; only explicit unsigned verification builds use the separate unsigned package. Historical 0.5.0 APKs retain their historical disposable-signature limitations.
