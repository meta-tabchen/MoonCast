# Version naming / 版本命名

## Exact numeric choice

Accept **major.minor** or **major.minor.patch**, using the user's exact requested numeric spelling. The current requested version is **0.7**, not 0.7.0. Do not normalize it to three components, append a release-stage/protocol suffix, or put a build hash in versionName.

- App versionName: `0.7`.
- Git tag: `v0.7`.
- Release title: `MoonCast 0.7`.
- APKs: `MoonCast-0.7-arm64-v8a.apk`, `MoonCast-0.7-armeabi-v7a.apk`.
- Source/notices: `MoonCast-0.7-source.zip`, `MoonCast-0.7-notices.zip`.
- Checksums: `SHA256SUMS`.

Android versionCode is a separate strictly increasing integer. Select it from verified prior artifacts and confirm it in each final APK. About reads the installed package version/code. Build/source commits and SHA-256 values belong in provenance/manifests, not version-name or artifact-name suffixes. The internal diagnostic app retains its own numeric version and separate identity.

版本允许两段或三段数字，严格使用用户给出的值。本次为 **0.7 / v0.7**，不能改成0.7.0，不添加browser、community、preview或提交哈希后缀。versionCode独立递增，以实际历史包和最终APK为准，本文不猜测未确认的值。

## Identity and classification

Keep `com.mooncast.community` and the existing fixed public test certificate unchanged. A display-name/version change does not permit a new key or package. The public test signer provides update continuity, not maintainer authenticity. Preserve signing guards, permission boundaries, source disclosures and experimental-feature limits.

Use the requested GitHub Latest classification; it is not a stability or universal-compatibility guarantee. Numeric naming alone does not expand test coverage. Version 0.7 is under development until final artifact and release evidence is recorded.

固定包名与现有公测签名不变，不新建密钥。Latest仅是发布分类，不证明实验功能全面稳定；0.7仍需最终构建与发布证据，不能先记为通过。

## Historical records stay historical

Keep existing tags, asset names, links, validation records and media captions unchanged. The historical numeric 0.6.0 build used code56 and changed naming metadata from code55; its publication status must be read from the Release, not inferred from that source version. Code55/browser-preview.4 and code54/browser-preview.3 retain their original tested names. An old tag or GIF must not be renamed into 0.7 evidence.

See [release procedure](release-process.md), [build/signing](unified-build.md), [engineering pitfalls](engineering-pitfalls.md) and the protocol-specific validation records.
