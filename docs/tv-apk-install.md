# Temporary APK receiving on TV

This preview adds a visible **Install phone APK / 从手机安装 APK** home action. Opening it does not stop existing playback. It starts one five-minute receiving session on a fixed LAN TCP port **48152** and advertises `_mooncast-apk._tcp.` only for that session. It never changes AirPlay, DLNA or GameStream ports.

There is no APK pairing code, token, or persistent phone binding. While the user explicitly opens this receive screen, any allowed same-subnet device may upload one ordinary APK. The five-minute receive window is consumed once an upload passes metadata checks and is admitted for body transfer, including a later interruption, checksum failure, or storage failure. Requests rejected before admission do not consume it. The phone never retries automatically. This is unencrypted HTTP on a trusted LAN, not protection against a hostile Wi-Fi/router. APK hashes detect transfer corruption; displayed signer fingerprints are informational and do not establish publisher trust or malware safety.

After receipt, the TV closes the network receiver, re-hashes the private file and asks Android PackageManager for package, label, version and signing certificate metadata. The review screen requires a remote-button choice for this file. A separate Android unknown-install-source settings page is opened only after that choice and explanation. Returning always revalidates and returns to review; it never automatically starts installation. Denial and missing ROM settings/installer are reported. Opening Android settings or installer follows the existing foreground-only media lifecycle and stops playback, as disclosed before the action.

The system installer receives only a content URI with a temporary read grant from a non-exported FileProvider rooted at `cache/apk-receive/`. No general cache, external storage, file URI, caller-supplied path, root install, shell command, downloaded URL or APK execution is exposed. Android owns final install consent, compatibility and signature checks. Result cancellation/failure does not claim success.

Network/background cancellation closes sockets, removes advertisement and cleans temporary files. A completed APK is retained for ten minutes during review/settings. An explicitly launched system installer may still be reading it, so that handoff is exempt from the review timeout and Activity recreation cleanup. A private, file-specific 24-hour recovery lease protects that handed-off file after process death; stale unleased or expired directories are cleaned on the next controller start. Normal installer return or user cancellation revokes the grant and removes the file. No installer is re-launched after recreation. A new receive session gets a new private directory. Only a non-secret receiver UUID is persisted for discovery; no phone binding or credential is created. Port conflicts fail visibly and never choose a random fallback port.

## Home LAN wire protocol (v2)

- `_mooncast-apk._tcp.` is advertised only while receiving is enabled, at port 48152. TXT fields: `v=2`, `apk=1`, `pairing=none`, `id=<stable app-generated receiver UUID>`.
- `GET /info` (HTTP/1.1, optional `Content-Length: 0`) responds with JSON: `{"protocol":2,"apk":true,"pairing":false,"receiverId":"UUID","port":48152,"maxBytes":1073741824,"state":"ready"}`. State becomes `receiving` during an admitted upload or `unavailable` once consumed. The UI closes the server after completed upload or failure. The ID is a discovery hint, not identity authentication.
- `PUT /v1/apk` requires fixed `Content-Length` (1 byte to 1 GiB), UTF-8 form-URL-encoded `X-APK-Name` ending `.apk`, and 64-hex `X-APK-SHA256`. No `Authorization` or pairing request is needed; `/v1/pair` returns 404. Success is HTTP 200 with body `received`, meaning received for local review, not installed.
- HTTP chunking, Expect, duplicate headers and proxy-form URLs are rejected. Headers are bounded to 16 KiB/32 entries, sockets have 15-second read timeouts, the session has a five-minute absolute limit, two request workers plus two queued requests are permitted, and requests are capped at 60 per minute per receive window. One upload is admitted; additional uploads get 409.
- Capability is independent from AirPlay support. Phone discovery may merge AirPlay and APK advertisements only by the resolved LAN address, never matching display names. An AirPlay-only receiver does not imply APK support. The existing AirPlay/DLNA/GameStream listener ports, pairing and behaviors are unchanged.
- This deliberately trades APK pairing for ease on a trusted home network. It is not suitable for public/hostile Wi-Fi. The visible review and Android installer remain mandatory, with no silent installation.

## Validation

- Run `tests/apk-transfer/run-tests.sh` with a JDK. It verifies production/test source-copy equality and runs the shared protocol checks under a 64 MiB heap.
- `:tv:compileDebugJavaWithJavac` and `:tv:compileDebugAndroidTestJavaWithJavac` compile the Android implementation and device suite.
- Device suite: `am instrument -w -e suite apk-receive -e ui true com.mooncast.tv.test/com.mooncast.tv.TvUiInstrumentation`. It parses the installed APK, checks certificate/hash, rejects corruption/wrong hash/bundle/path escape and verifies the FileProvider scope. With `-e ui true`, it also exercises actual same-device socket upload, TV metadata review, cancellation and background cleanup. It never opens an installer or changes install permission. This loopback harness is not two-device discovery/interoperability evidence.
- Physical TV OEM installation compatibility, phone-to-TV mDNS, remote navigation and final Android permission/install UI need separate device evidence. Compilation alone is not that evidence.

## Official implementation references

- [PackageManager: archive metadata, signing information and canRequestPackageInstalls](https://developer.android.com/reference/android/content/pm/PackageManager)
- [AndroidX FileProvider: content URIs and temporary grants](https://developer.android.com/reference/androidx/core/content/FileProvider)
- [Android security: narrowly scoped FileProvider directories](https://developer.android.com/privacy-and-security/risks/file-providers)
- [Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_UNKNOWN_APP_SOURCES)

AndroidX Core's existing 1.8.0 dependency is now explicit for FileProvider; this does not introduce a new native component. AndroidX is Apache-2.0, compatible for inclusion in this GPLv3 project ([Apache's GPL compatibility statement](https://www.apache.org/licenses/GPL-compatibility.html)).

## 2026-10-11 verification record

The initial implementation passed an Android 13 cloud-emulator suite covering real APK metadata/certificates, private FileProvider scope, corruption rejection and actual same-device socket transfer into the review UI, followed by cancel/new-session/background cleanup. No Android installer or install-source approval was invoked. The emulator later exited; a Quickstep ANR obscured the captured screenshot. Final hardening and additional explanation-dialog checks were compiled/linted and host-tested, but could not be rerun on the exited emulator. Physical TV installation/discovery and clean visual acceptance remain unverified.

The final source includes bounded ZIP directory, resource-table and standalone-manifest policy checks; these are structural/metadata checks, not a security scan. Portable legacy runners use source/target 17 in this stripped local JDK because its missing ct.sym blocks the exact --release 17 invocation. CI/full JDK 17 remains needed for that exact API check.

## Home LAN simplification verification (2026-10-11)

The v2 host suite passes 112 protocol/container checks plus the standalone-manifest policy suite. It covers fixed-port conflict, code-free upload, capability responses, one-attempt lifecycle, malformed headers, rate limit, interrupted-upload cleanup, concurrent upload rejection, expiration and client cancellation. Android device tests are updated to the no-pairing contract. Current physical phone-to-TV mDNS, remote navigation and OEM installation remain unverified; do not treat compilation as interoperability.

The ARMv7 `com.mooncast.tv.preview` 0.4.2 candidate (versionCode 6) passed `assembleCoexist`, debug Android-test compilation, static native/provenance/package verification, APK signature verification and ZIP alignment. `lintDebug`: 0 errors, 36 warnings. It uses the same pre-existing local Android Debug identity as the earlier coexist preview, not a production signing key. All 7,502 generated source/runtime inventory entries validate. Eight existing portable app/TV/AirPlay/GameStream runners pass under the documented source/target-17 fallback; exact `--release 17` remains blocked by this local JDK missing `ct.sym`.

## 0.4.4 standalone metadata correction

Some complete APKs declare `com.android.vending.splits.required=false`. The old policy rejected the metadata name regardless of its value. The new policy accepts an explicit false boolean/string value and treats `com.android.vending.splits` as an optional inventory, not proof that splits are required. Actual split declarations, `uses-split`, application/root required flags, true/missing/unresolved Play required values continue to be rejected. Android PackageManager and the system installer still own signature and compatibility decisions. There is no package-name blacklist, signing-key equality check against MoonCast, or self-update prohibition.

`tests/apk-transfer/fixtures/optional-splits.xml` is a generated minimal regression manifest. Build it with `aapt2 link -I <sdk>/platforms/android-35/android.jar --manifest tests/apk-transfer/fixtures/optional-splits.xml -o /tmp/optional-false-unsigned.apk`; the resulting APK is unsigned, contains no executable app code, and is only a structural policy fixture. Against the preceding revision the policy rejects it; the corrected policy accepts it. No installation was attempted. Run `tests/apk-transfer/run-tests.sh /absolute/path/optional-false-unsigned.apk` to include this fixture in the portable suite.

## Settings return and remote action

The source-permission Activity result is advisory. Permission and file validation refresh on the next actual foreground return after pausing for Settings. This avoids an early OEM result clearing file-retention state before `onStop`, and also handles Settings implementations that omit the result. A file-specific ten-minute review lease and validated saved-state filenames/hash preserve review across Activity recreation; lease expiry or cache eviction produces a visible receive-again explanation. Nothing grants permission or installs automatically. The user must still press the primary action after permission and file revalidation. Denial leaves an enabled action to open Settings again.

The action separately labels waiting for upload, validation, source permission, ready installation and installer handoff. The home action is Receive APK / 接收 APK. A disabled install action before a file is received is expected, even if Android source permission was granted earlier.

`python scripts/run_apk_settings_lifecycle_tests.py` executes the actual production lifecycle method bodies in a generated JVM fixture with fake Android/validation collaborators. It verifies early/missing/late Settings results, preservation across stop and recreation, enabled review only after validation, no automatic installation, expiry, path rejection, missing-file explanation and ordinary-background cleanup. Passing revision `45be522` reproduces the earlier retention failure. This is a deterministic host regression, not physical OEM Settings or visual acceptance evidence.
