> Current source target: **0.9 / code66**. The Go sender AAR remains hash-locked; the receiver and Sunshine runtime require the verified native rebuild/override below. [Validation and limits](release-0.9-validation.md).

# Build the community app / 构建社区版

Current source: [`main`](https://github.com/meta-tabchen/MoonCast/tree/main). Use JDK 17, Gradle 8.9 / AGP 8.7.3, Android SDK 35 and Build Tools 35.0.0. Each single-ABI APK contains both phone and TV roles. Historical 0.5.0 build results are in [the historical record](unified-validation.md); current browser evidence is in [browser validation](browser-validation.md). The [0.5.1/code51 record](community-validation.md) remains historical. Current application metadata is `0.9`, versionCode 66. Earlier code55/code54 records remain historical; numeric 0.6.0 was a draft, not a public release.

## Full Git checkout: fixed community signing

```sh
git clone --branch main https://github.com/meta-tabchen/MoonCast.git
cd MoonCast
python3 scripts/restore_release_inputs.py
# Do the 0.9 native rebuild (or strict CI-output reuse) described below FIRST.
# A source-only C/C++ edit does not change the restored legacy .so bytes.
python3 scripts/prepare_airplay.py --verify-sources
python3 scripts/prepare_airplay.py --verify-aar app/libs/airplaylib.aar
python3 scripts/prepare_unified_native.py
gradle :unified:assembleRelease :unified:lintRelease -PmooncastAbi=arm64-v8a
python3 scripts/verify_community_identity.py unified/build/outputs/apk/release/unified-release.apk
```

`mooncastAbi` accepts `arm64-v8a`, `armeabi-v7a` or `x86_64`; omit it for a larger universal build. This does not promise every ABI is published as a release download.

The normal community build uses `com.mooncast.community`, versionCode 66 for 0.9 (historical browser preview.4 used 55, preview.3 used 54 and 0.5.1 used 51), and the fixed public test fixture initialized by the owner once in commit `cd39378fceeea1814df1b3e849d1588081aa7735`. Certificate SHA-256: `290e4372fff33c00848758b12275936900b2eccad053dc63c18622e1a1222bb2`. Git supplies the fixture, identity manifest and checksum records; the build verifies them. Missing material, changed bytes or an unexpected certificate must fail. Never regenerate a replacement on each build.

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

## Browser SDK source and build scope

The browser integration resolves the unchanged open-source `io.github.webrtc-sdk:android:144.7559.15` AAR. `verifyBrowserWebRtc` checks its pinned hash. Read [browser-release-source.md](browser-release-source.md), [the SDK source lock](../native/webrtc/SOURCE_LOCK.json) and [SDK checkout/build guidance](../native/webrtc/README.md) before distributing or rebuilding.

The application bundle supplies MoonCast source/build instructions plus version-pinned external WebRTC source, DEPS and fetch/build mappings. It **does not include the complete offline SDK dependency checkout**, and the prebuilt native SDK **has not been independently rebuilt**. Downloading/building the SDK dependency graph requires its documented external sources. Do not interpret the legacy 0.5.0 bootstrap below as supplying this new SDK's complete source tree.

For browser preview.3, runtime build `7a843a1` and source/documentation snapshot `1b03a08` are distinct: the latter adds documentation, tests and provenance metadata without an APK rebuild. Subsequent main integration/documentation commits do not change those published artifact hashes. Verify exact release assets and notices separately from a fresh main build.

浏览器功能使用固定未修改 WebRTC AAR。应用源码包提供外部固定源码／DEPS 与获取、构建映射，不含完整离线 SDK 依赖树，原生 SDK 尚未独立重建。旧 0.5.0 引导源包只恢复原有固定输入，不代表含有新 SDK 的全部源码。

## Native isolation and corresponding source

The Go sender AAR remains pinned; its source/locks live under native/airplay-sender.
The 0.9 receiver JNI-ownership and Sunshine JNI/capture changes are rebuilt native
outputs, not the legacy binaries restored by a plain Git checkout.

Receiver sources retain both audio transition patches plus the adapter ownership
patch. For a fresh Linux native rebuild, first install the official locked NDK/SDK
CMake toolchains under your existing SDK license, then use new empty work folders:

```sh
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
PATH="$ANDROID_HOME/cmake/3.22.1/bin:$PATH" python3 scripts/build_airplay_receiver_candidate.py --source . --work /tmp/mooncast-receiver-new
python3 scripts/install_receiver_candidate.py --work /tmp/mooncast-receiver-new
PATH="$ANDROID_HOME/cmake/3.31.1/bin:$PATH" python3 scripts/build_sunshine_candidate.py --work /tmp/mooncast-sunshine-new
python3 scripts/prepare_unified_native.py
```

These scripts verify locked source archives/toolchains, rebuild all three ABIs,
and install only after their checks succeed; they never remove a nonempty work
folder. The unified Actions route instead reuses the exact native artifact12102154262
from run39035789360, ZIP SHA-256
`d3817623086ad1d419ce996cf25d1143a239e9f6eef79634a781b97e69ace1cd`,
through `reuse_native_candidate.py --bundle <downloaded-artifact-directory>`.
Reuse checks every native source/archive/patch/recipe/output and retains the
original build provenance; changed inputs require the rebuild above. The Release
source ZIP already includes its verified generated native overrides and manifests.
Do not overwrite those with old restored files or call a baseline-only build native-patched.

`prepare_unified_native.py` then deterministically
changes only the exact ELF DT_NEEDED/DT_SONAME string libcrypto.so to the equal-length
libtvcryp.so and gives that library a distinct filename. It never modifies executable
code, symbols, source archives or the original standalone binaries. The generated
manifest pins source and output hashes. No pickFirst or random OpenSSL substitution
is used. Receiver JNI stays in :receiver; phone native hosting stays in :stream/:host.
The phone does not require a second libc++ runtime; receiver libc++ is pinned once.

Preserve existing native dependency sources, patches, migration locks, generated receiver
binaries/manifests, helper scripts and licenses. For the newly added WebRTC SDK,
preserve the external source directions and scope described above; do not claim the
application archive contains a complete offline SDK dependency tree.
Standalone packaging scripts target their historical products and are not the unified
release packager. Release source archives exclude the MoonCast app-signing PKCS12 fixture. Never publish private production keys, Android SDK caches, device identifiers or
private logs. The owner-initialized public community Git fixture is a narrow exception described above. Native runtime load tests are separate from real streaming acceptance.

For the bundled Sunshine dependency archives and isolated local-source preparation, see
[sender-source-inputs.md](sender-source-inputs.md). Create the application-source archive
with `python3 scripts/package_unified_source.py --output /path/to/new-source.zip`.

## Restoring pinned legacy dependency inputs

The main branch keeps application source, recipes, manifests and licenses in Git. Large/new binary inputs and the pinned generated corresponding-source tree are supplied in the exact complete-source Release ZIP rather than duplicated in Git. `scripts/release-inputs.json` authenticates small manifests under `scripts/release-inputs/`; together they list every restored path, SHA256, size and mode. Do not replace missing libraries with arbitrary upstream builds.

`python3 scripts/restore_release_inputs.py` downloads the public `unified-v0.5.0-preview` complete-source asset from `meta-tabchen/MoonCast`, verifies its exact size and SHA256, and restores only listed missing paths. Existing matching files are reused; any modified existing target aborts instead of being overwritten. It does not extract application source or private keys over your checkout. Public upstream OpenSSL test fixtures remain part of the pinned corresponding source.

For offline use: `python3 scripts/restore_release_inputs.py --archive /path/to/MoonCast-0.5.0-unified-complete-source.zip`. Add `--verify-only` to validate without writing. An unpublished draft asset is not anonymously downloadable; CI waits up to five minutes for publication and otherwise fails clearly. Retry CI after the Release is public. A complete source ZIP already contains these inputs; its original source snapshot is preserved unchanged, while the publication branch adds these restoration instructions.

The restoration asset remains the historical 0.5.0 complete-source ZIP because these inputs are pinned dependencies. It does not make current application code or artifacts version 0.5.0. Normal community builds use the fixed signing identity described above; only explicit unsigned verification builds use the separate unsigned package. Historical 0.5.0 APKs retain their historical disposable-signature limitations.

Browser preview.4 QR and quality behavior is documented in [browser-qr-quality.md](browser-qr-quality.md). Target ceilings and device capability reports are not measured throughput or sustained quality. Final build identity and artifact verification must be recorded before describing a candidate as published.

Historical preview.4 source/runtime: c0936675193c49e3bcd64bb0a76171059b9f9210. Its delta from d44a4c4 is icon-only; no additional device test was requested or performed. Pre-icon quality observations and code54 media remain separately scoped.

For 0.7 checks and limits see [release-0.7-validation.md](release-0.7-validation.md). Follow the project engineering pitfalls and release procedure; no device pass is implied by a host or DEX check.
