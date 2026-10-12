# TV signing and opt-in publication

This applies only to `com.mooncast.tv`. It does not change the phone sender's
signing or any existing v0.3 release asset. This change supplies configuration and
guards only: no release key, secret, GitHub environment, or secret upload is
created by it. A stable signing identity is **not provisioned yet**.

## Safe default

- `:tv:assembleDebug` and UI-test builds keep Android's normal local debug setup.
  Debug APKs are for development; they cannot pass the release packager.
- With no external signing inputs, `:tv:assembleRelease` produces
  `tv/build/outputs/apk/release/tv-release-unsigned.apk`. It is suitable for payload,
  lint and alignment checks, but cannot be installed or published as a release.
- Providing any signing input requires all four nonblank inputs below. A missing
  input or missing keystore file fails configuration with input names, without
  printing values. There is no fallback to `signingConfigs.debug`.
- Push and pull-request CI only verify. `receiver/gamestream-integration` is
  verification-only, including manually dispatched runs.
- Publication is limited to `meta-tabchen/MoonCast`, branch `receiver/android-tv`,
  and a manual dispatch explicitly setting `publish_release=true` (default false).
  Missing external signing inputs fail closed. No current key is assumed.

## User-managed local signing

The owner must first select and securely retain their own existing release
keystore outside the checkout. Key creation, backup, export, secret upload, and
persistent-access setup are separate owner actions; these scripts do none of them.
Do not paste keys or passwords into chat, commits, command-line arguments, logs,
release assets, or a repository-local properties file.

Supply all four through a trusted local environment or secret manager. Optional
Gradle property names are also supported, for an owner-controlled configuration
outside the repository. Environment values take precedence, including explicitly
blank values (which fail rather than silently using a property).

| Environment variable | Gradle property | Meaning |
| --- | --- | --- |
| `MOONCAST_TV_KEYSTORE_PATH` | `mooncastTvKeystorePath` | Existing external keystore path; prefer an absolute path |
| `MOONCAST_TV_STORE_PASSWORD` | `mooncastTvStorePassword` | Keystore password |
| `MOONCAST_TV_KEY_ALIAS` | `mooncastTvKeyAlias` | Selected key alias |
| `MOONCAST_TV_KEY_PASSWORD` | `mooncastTvKeyPassword` | Selected key password, explicitly supplied even when equal to the store password |

With the variables already supplied securely:

```sh
./gradlew :tv:requireTvReleaseSigning :tv:assembleRelease :tv:lintRelease \
  --no-daemon --no-configuration-cache
```

A configured signed output is `tv/build/outputs/apk/release/tv-release.apk`.
The `requireTvReleaseSigning` task checks that the external inputs are present;
Gradle's signing task checks whether they actually open the chosen key. It does
not certify that the key is suitable for publication. Do not enable Gradle
configuration caching or build scans when working with signing credentials.

## Packaging requires an independently chosen public certificate pin

The owner records the intended signing certificate's SHA-256 fingerprint from a
trusted public certificate or previously approved signed APK. The fingerprint is
public, not a password. Do not choose the pin by blindly trusting the new build
that the pin is meant to verify. Android's [app-signing guide](https://developer.android.com/studio/publish/app-signing)
explains certificate identity and why retaining the signing key matters for updates.

After the normal build, lint, behavior, native payload and alignment checks,
and from a clean committed checkout:

```sh
python3 scripts/package_tv_release.py --release-package \
  --expected-signing-cert-sha256 "$MOONCAST_TV_SIGNING_CERT_SHA256" \
  --apk tv/build/outputs/apk/release/tv-release.apk \
  --output tv/build/release-assets
```

The packager independently runs Build Tools 35.0.0 `apksigner verify`, requires
exactly one signer matching the supplied SHA-256, and rejects Android Debug
certificate identities even if their fingerprint matches. A subject-name check
alone cannot establish who controls a key: the independently selected pin is the
identity guard. Unsigned APKs, invalid signatures, missing/malformed pins,
mismatched pins and omitted `--release-package` all fail before release assets
are written.

Only after those checks is the APK named `externally-signed-preview`. Its actual
verified certificate fingerprint goes in `BUILD_INFO.json` and release notes.
Corresponding native source, provenance, licenses and checksums remain required.
The packager creates no Git tag and does not upload or publish anything.

## Future GitHub provisioning, performed by the owner

No GitHub secrets or environment are configured by this change. If the owner
later elects to use GitHub signing, the checked-in workflow expects these
**repository secrets**:

- `MOONCAST_TV_KEYSTORE_BASE64`: the externally provided keystore, encoded as
  single-line base64. Base64 is transport encoding, not encryption.
- `MOONCAST_TV_STORE_PASSWORD`
- `MOONCAST_TV_KEY_ALIAS`
- `MOONCAST_TV_KEY_PASSWORD`

It also expects repository variable `MOONCAST_TV_SIGNING_CERT_SHA256`, containing
the independently chosen public fingerprint. The owner must decide whether the
repository's workflow access and protections are sufficient before uploading any
credential. Follow GitHub's [secret-management instructions](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).

A safer protected-release setup can be added later by the owner: manually create
a `tv-release` environment, restrict deployment to `receiver/android-tv`, require
reviewers where supported, provision its secrets/variable, and only then add an
`environment: tv-release` reference to the packaging job. The workflow currently
has **no environment reference**, so it cannot implicitly create one. Environment
protections and availability depend on repository plan/settings; see GitHub's
[deployment and environment documentation](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments).
Environment-only secrets will not be visible until that authorized workflow
change is made. Do not add secrets to make an unreviewed branch build pass.

The pipeline keeps authority separated:

1. `verify`: runs without release credentials; builds debug/test APKs and an
   unsigned release, then checks the release payload/alignment and debug signature.
   Its retained unsigned artifact is a build input, not an installable release.
2. `package`: only on the allowed manual dispatch. Downloads that exact run's
   unsigned APK, decodes the owner's provided keystore into a restricted temporary
   directory, and signs it with `apksigner`. Passwords use environment references,
   not command-line values. The temporary copy is removed on shell exit. A later
   step, without signing secrets, verifies the signed payload/alignment and runs
   the pin-enforcing packager. This job has no repository write permission.
3. `publish`: receives only verified release artifacts, has no signing secrets,
   and creates a new immutable prerelease. It re-downloads and verifies hashes.

Runner cancellation or infrastructure failure can defeat a shell cleanup trap;
use trusted ephemeral runners and manage the original key's custody independently.
Never archive runner temp directories or upload signing logs/caches.

## Upgrade and verification limits

A retained certificate supports future APK updates only when the package identity
and version-code requirements also match. This does not restore an old ephemeral
CI key or migrate its identity. Installing over older debug-signed previews may
still need a one-time uninstall, which removes preferences and receiver identity.
Do not claim that path has been device-tested merely because signature checks pass.

Credential-free guard tests are available without Android or key material:

```sh
python3 scripts/run_tv_signing_tests.py --gradle ./gradlew
```

These tests use fake text paths and synthetic public-report strings. They exercise
configuration validation and refusal paths; they do not generate a key, prove
real-keystore signing, publish a release, or establish real-device upgrade support.
