#!/usr/bin/env bash
# USER-RUN ONE-TIME TOOL, NEVER CALLED BY GRADLE OR CI.
# Produces an intentionally public community TEST identity, not a production key.
# Review the warning and run this locally yourself. Uploading the result is your action.
set -euo pipefail
out="${1:-mooncast-public-community-fixture}"
if [ -e "$out" ]; then echo 'Output already exists. Refusing to replace an identity.' >&2; exit 1; fi
command -v keytool >/dev/null || { echo 'Install a JDK (17+) from an official vendor first; keytool was not found.' >&2; exit 1; }
cat <<'WARNING'
PUBLIC TEST SIGNATURE WARNING
This key and its password are intended to be public. Anyone can sign APKs with it.
It does NOT authenticate the official publisher. Never use it for a store/production app.
A new key cannot update old APKs signed with a lost key. Keep the final identity unchanged.
公开测试签名：任何人都可用它签 APK，不能认证官方发布者，严禁用于上架/生产版。
Type PUBLIC-TEST to create this one-time fixture / 输入 PUBLIC-TEST 才创建：
WARNING
read -r consent
[ "$consent" = PUBLIC-TEST ] || { echo 'Cancelled; nothing created.'; exit 1; }
mkdir -p "$out/signing/community"
keytool -genkeypair -keystore "$out/signing/community/mooncast-public-test.p12" \
 -storetype PKCS12 -storepass mooncast-public-test -keypass mooncast-public-test \
 -alias mooncast-community -keyalg RSA -keysize 3072 -validity 36500 \
 -dname 'CN=MoonCast Public Community Test, OU=PUBLIC TEST FIXTURE, O=MoonCast Open Source, C=CN' -noprompt
keytool -exportcert -rfc -keystore "$out/signing/community/mooncast-public-test.p12" \
 -storepass mooncast-public-test -alias mooncast-community \
 -file "$out/signing/community/certificate.pem"
keytool -list -keystore "$out/signing/community/mooncast-public-test.p12" -storepass mooncast-public-test
printf '\nCreated: %s/signing/community\n' "$out"
echo 'User action: upload these two files yourself to the signing/community directory of the approved GitHub branch.'
echo '请本人在已授权的 GitHub 分支 signing/community 目录上传这两个文件，然后告知完成。不会自动上传。'
echo 'Maintainers must inspect and pin this exact public certificate before enabling community builds. Do not regenerate it per build.'
