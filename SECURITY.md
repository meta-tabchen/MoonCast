# Security

MoonCast is an early preview intended for a trusted local network. It has not received a security audit. Only the latest preview is maintained on a best-effort basis; there is no security response SLA.

## Report privately

Use this repository's **Security → Report a vulnerability** page when available: [private report](https://github.com/meta-tabchen/MoonCast/security/advisories/new). If GitHub does not offer that option, use the maintainer's [profile contact options](https://github.com/meta-tabchen) to arrange a private channel before sharing sensitive details. Do not post exploit instructions or private phone data in public issues.

Include the affected version, Android/ROM, prerequisites, impact, and minimal reproduction. Redact credentials, app-private files, and network identifiers.

## Permission model

Ordinary capture uses explicit MediaProjection consent. Playback capture requires Android permission and a source app that allows it. Input defaults off; Accessibility must be enabled by the user, and Root requires `su` authorization. The Root broker checks local peer UIDs and uses a random socket name. These mechanisms explain the implementation, not an assurance of security.

Pairing certificates, private keys, and client identities stay in private app storage and are excluded from backup. Do not expose host ports directly to the Internet or include these files in reports. Source and release packaging must exclude local credentials, private production signing keys and app pairing identities. The narrowly scoped public community-test fixture is described below; Release source archives still exclude its PKCS12 signing material.


## Public community signing / 公开社区测试签名

The owner initialized the fixed community test identity once in commit `cd39378fceeea1814df1b3e849d1588081aa7735` on `unified/community`. Its deliberately public Git fixture is solely for repeatable community update compatibility, not publisher authentication. Its package is `com.mooncast.community`; certificate SHA-256 is `290e4372fff33c00848758b12275936900b2eccad053dc63c18622e1a1222bb2`. Anyone with this public identity can sign an APK. Use a trusted project download source and compare published artifact hashes; matching this certificate alone does not prove an APK is maintained by this project.

The exception covers only that owner-initialized public test fixture. It does not permit publishing production secrets, service credentials, pairing identities or arbitrary signing keys. Builds must never regenerate or silently replace the fixture. Missing inputs, changed bytes or mismatched certificates fail normal community builds. Future production/store signing must use a separate package and confidential identity, with no community fallback.

Release corresponding-source archives omit the MoonCast app-signing PKCS12 fixture. A full Git checkout supplies the fixed fixture and its recorded checksums. Source-only verification can explicitly use `-PmooncastUnsignedVerification=true`, which produces an unsigned, separate `com.mooncast.community.verification` identity. That artifact is not a signed release and cannot update the community app. See [build instructions](docs/unified-build.md) and [signing policy](signing/community/README.md).

社区测试身份由用户一次性初始化，固定于 Git，用于社区版覆盖升级，不证明维护者身份。只有这一指定公开测试材料属于例外；生产密钥、服务凭据和配对身份仍禁止公开。正常构建缺少材料或校验不一致必须失败，不能临时生成替代密钥。Release 对应源码包不含 MoonCast 应用的 PKCS12 签名文件；可显式构建独立包名的无签名验证产物。未来商店版必须使用独立包名与私有身份。
