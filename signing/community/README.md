# Public community test identity / 公开社区测试身份

This is an intentionally public test fixture, including its PKCS12 test signing material and the public password `mooncast-public-test`. It provides repeatable community APK update compatibility, **not publisher authentication**. Anyone can sign an APK with this identity. Download only from the trusted project Release page. It grants no server, GitHub, production, system UID or device permissions.

这是有意公开的社区测试签名材料，密码同样公开。它用于让社区版保持同包名、同签名覆盖安装，**不证明发布者身份**。任何人都能使用此身份签名，请仅从可信的项目 Release 下载。它不是生产密钥，不赋予任何服务器、GitHub 或系统权限。

- Fixed application ID: `com.mooncast.community`
- Certificate SHA256: see `certificate-sha256.txt`, created once by the owner-run initialization workflow
- First community versionCode: 51; subsequent releases must increase it.
- The old 0.5.0 preview used a destroyed disposable key. This package installs alongside it; settings/pairing remain separate. This is the one-time migration, not a per-release package change.
- 旧 0.5.0 预览签名无法恢复，本社区包与旧版并存，需要一次迁移。以后社区包名与本证书固定不变，不每版重新生成。

Never regenerate this fixture on build. Missing or modified bytes fail the build. `production` builds are deliberately disabled until a separate package and confidential signing implementation exists; community signing is never a fallback. The generation command below documents provenance only; it is **not** executed by Gradle/CI, and running it again would produce a different identity:

    keytool -genkeypair -storetype PKCS12 -alias mooncast-community \
      -keystore mooncast-public-test.p12 -storepass mooncast-public-test \
      -keypass mooncast-public-test -keyalg RSA -keysize 3072 -validity 36500 \
      -dname 'CN=MoonCast Public Community Test, OU=PUBLIC TEST FIXTURE, O=MoonCast Open Source, C=CN'

The fixture is created once specifically for this public test use by the owner manually running the initialization workflow. It is distributed under the project GPL-3.0-or-later license. No private production signing material belongs in this directory.
