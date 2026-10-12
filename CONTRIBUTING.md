# Contributing

Thanks for helping test and improve MoonCast. English and Chinese reports are welcome.

## Before coding

Use the **main** branch for the complete phone/TV/browser implementation. Start with the [unified build/signing guide](docs/unified-build.md), [TV setup](docs/unified-guide.md), [browser guide](docs/browser-receiver.md) and [current browser evidence](docs/browser-validation.md). The current application target is **`:unified`**, not `:app`. Follow the guide for pinned native inputs, fixed community signing or an explicitly unsigned verification build; never generate a replacement signing identity as a fallback.

Current application metadata is `0.7`, versionCode 57. Earlier code55/code54 records remain historical; numeric 0.6.0 was a draft, not a public release. The [0.5.1/code51 record](docs/community-validation.md) and older sender documents are historical, not current-version acceptance. Read [browser source scope](docs/browser-release-source.md): the bundle includes application source and pinned external SDK source/DEPS/build mappings, not a full offline WebRTC checkout or proof of an independent SDK rebuild.

当前完整手机／电视／浏览器实现以 **main** 为主分支，构建目标是 **`:unified`**。当前应用版本为0.7/code57；code55与code54保留为历史，数字0.6.0未公开发布；0.5.1/code51 与以下发送端记录为历史。应用源码包提供 WebRTC 固定外部源码、DEPS 和构建映射，不等于完整离线 SDK 源树或已独立重建原生 SDK。不要在缺少固定社区签名时生成临时身份。

### Historical sender-only maintenance

Read the [build guide](docs/building.md), [architecture](docs/architecture.md), and [current limits](docs/compatibility.md). Discuss large features in an issue first. Keep changes focused and retain upstream licenses/provenance. Do not update vendored native code and binary manifests independently.

Use JDK 17 and Android SDK 35. Run:

```sh
./gradlew :app:assembleDebug :app:lintDebug
python scripts/run_unit_tests.py
python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
```

Use `gradlew.bat` on Windows. Add behavior tests when changing crop stabilization, input mapping/session cleanup, or volume recovery. Device-only changes need an actual device test report; static checks alone are not runtime evidence.

## Pull requests

Describe the user-visible problem and resulting behavior, verification performed, and remaining limits. For visual changes, include an original screenshot or test-pattern image with private information removed. For native changes, include source provenance and corresponding-source build details. Do not infer Root, Moonlight, TV-browser, rotation or audible-speaker acceptance from a successful build or browser-only fixture; report the exact run and device scope.

## Translation

On the current `main` implementation, check English `values/*.xml` and Chinese `values-zh/*.xml` in `app`, `tv`, `unified`, and `locale-core`; run `python3 scripts/check_localization.py` and the relevant behavior checks. The historical sender-only resources are under `app/src/main/res/`. Preserve resource names and escapes; test long labels and small screens. Documentation starts at `README.md` / `README.zh-CN.md`. Lower-level native/Root diagnostic strings are not all localized yet.

## Reports

Use the bug/compatibility/feature forms. Include the protocol/action, each device’s sender/receiver role, model, OS/API/ABI (unknown is acceptable), exact versions and relevant settings, steps, and expected/actual behavior. Moonlight version applies only to GameStream reports. Never upload keys, certificates, pairing identities, phone serial numbers, unredacted screens, or private logs. Report vulnerabilities privately through [SECURITY.md](SECURITY.md).

By submitting a contribution, you agree to distribute it under the project's GPLv3 license with applicable retained third-party notices. No CLA is required. Follow the [code of conduct](CODE_OF_CONDUCT.md).

Browser preview.4 QR and quality behavior is documented in [docs/browser-qr-quality.md](docs/browser-qr-quality.md). Target ceilings and device capability reports are not measured throughput or sustained quality. Final build identity and artifact verification must be recorded before describing a candidate as published.

Historical preview.4 source/runtime: c0936675193c49e3bcd64bb0a76171059b9f9210. Its delta from d44a4c4 is icon-only; no additional device test was requested or performed. Pre-icon quality observations and code54 media remain separately scoped.

For 0.7 checks and limits see [docs/release-0.7-validation.md](docs/release-0.7-validation.md). Follow the project engineering pitfalls and release procedure; no device pass is implied by a host or DEX check.
