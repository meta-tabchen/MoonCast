> **Historical 0.5.0 record / 0.5.0 历史记录.** These results apply only to that recorded build, not the 0.5.1 community source or APKs. For current evidence see [community validation](community-validation.md). 下述结果不能作为社区版构建或实机验收结果。

# MoonCast 0.5.0 unified preview validation

This is an experimental build, not a claim of complete device compatibility.
See unified-artifacts.json for exact ARM64/ARMv7 APK hashes, sizes, identity and
input provenance. Both independently installable APKs contain both roles.

## Build and source checks: passed

- Java 17 / Gradle 8.9 / Android SDK 35; release lint: zero errors, five warnings.
- Optimized non-debuggable release APKs built for ARM64, ARMv7 and x86_64.
  Their classes.dex and resources.arsc are byte-identical across all three ABIs.
- Lint warnings are three intentional synchronous transaction-preference commits,
  API33 localeConfig compatibility and a trust-manager warning inside the inherited
  BouncyCastle 1.77 dependency. These are disclosed, not represented as a security audit.
- Exactly ten native libraries per selected ABI; all payload hashes match their
  original or deterministic-transformation manifests; every non-system ELF
  dependency resolves. Receiver crypto SONAME and JNI DT_NEEDED use libtvcryp.so.
- APK package, minimum/target SDK, MoonCast label, phone/Leanback launcher, version,
  no debug probe in release, signing certificate and ZIP alignment verified.
- Phone Go sender source/module archive/AAR verification passed. Receiver and
  Moonlight corresponding-source inventory verified 7,502 exact pinned inputs.
- Missing Sunshine OpenSSL 3.5.5 and Boost 1.86.0 source archives were obtained
  from official releases and verified against their official SHA256 sidecars.
  Local-input preparation tested; these sender native binaries were not rebuilt
  this round. Original provenance and full rebuild instructions remain supplied.

## Host and native regression checks: passed

- Unified automatic/manual role policy and owner-PID teardown: twelve role cases
  plus old-fail/new-pass teardown, idle accessibility and failed ACK gates.
- Production MyTV NSD callback tests: 112 checks, including third-party AirPlay,
  service name/type/address variants and separate AirPlay/APK capabilities.
- APK transfer protocol: 112 loopback limits, watchdog, cancellation, concurrency,
  names, hashes and parse checks; sender endpoint/manifest tests: 53 checks.
- Real previously built APK fixtures: sixteen document/name combinations; former
  display-name rejection reproduced. APK settings-return lifecycle checks passed.
- TV audio: 445,713 assertions; AirPlay parser/watchdog: 78; decoder transitions:
  eighteen scenarios / 998 checks. Actual native RTP queue sanitizer tests passed
  repeated FLUSH, marker, loss/resend and 1,000 stream switches.
- Phone unit/session/volume/HTTP/media tests, TV unit/framing tests, GameStream
  discovery (twenty checks) and controller/host lifecycle tests passed.

## Actual Android execution: passed on debug test target

API 29 x86_64 AOSP software emulator, without hardware acceleration. Test target
SHA256: 84bce536aaa9be8b9854d31d2683a9f48f47b7aa6e04458e6c03117ef8aa3cb8.
Native/TV test APK: 72f9a7d771e281266185fba78fa73135257522631f1b5599fa3929bdd3373401.

- Sender and receiver JNI initialize in distinct processes. Process maps prove
  app-owned sender OpenSSL and renamed receiver crypto are isolated. Android's
  own system libcrypto is correctly distinguished from app libraries.
- Synthetic slow document open reproduces a delayed listener after an early
  cleanup ACK; production file-process teardown prevents the listener and waits
  for the actual owning PID to exit.
- Phone launch, AirPlay tab/About, phone-to-TV and TV-to-phone controls work.
  Remembered phone role survives force-stop/cold start; theme survives app update.
- All four themes visibly differ, preview and persist correctly; real screenshots
  reviewed. Long selected-theme label and stop-button clipping were corrected.
- TV runtime suite: 57/57 checks passed. A generated local MP4 was served over HTTP
  to the actual DLNA/Media3 path. Decoded frames advanced from 0.667 s to 4.733 s
  behind the home overlay and 18.400 s behind settings, with the same player and
  surface. D-pad/Back, crop cancel/persistence and theme behavior passed.

## Validation boundaries

- No physical iPhone, Android phone or TV was connected. Apple app compatibility,
  physical cross-device discovery/casting, MediaProjection consent/capture,
  GameStream paired streaming, active-capture role switching, chooser rotation on a
  physical device, DRM apps and OEM hardware codec behavior are not
  established by these tests.
- Host audio output is unavailable. Acoustic quality, lip sync, HDMI sound and
  the original short-drama app's track-switch silence require physical testing.
- System APK installation was not executed and no unknown-source permission was
  granted by automation. Android retains signature/version/compatibility checks.
- Debug runtime evidence must not be described as execution of an ARM release.
  ARM APKs are built, signed and statically checked; only x86_64 runs here.

## Archive signature compatibility regression found during release QA

On API29, a valid installed APK copied to private receive storage failed with
"APK signing certificate could not be read". This was reproduced after confirming
the installed optimized APK hash; it was not counted as an installation race.
Android 10's [AOSP PackageManager implementation](https://android.googlesource.com/platform/frameworks/base/+/android-10.0.0_r1/core/java/android/content/pm/PackageManager.java)
collects archive certificates only when GET_SIGNATURES is present. The old code
requested only GET_SIGNING_CERTIFICATES on API28+. Both the sender and receiver now request both
bits on API28+, retains the legacy bit on API26/27, and still prefers current
SigningInfo signers. Empty/unreadable signatures remain rejected. This triggers
platform certificate verification rather than bypassing it. Device regression
instrumentation records both flag modes on the same actual APK fixture.

Final compatibility evidence: 50 host checks execute the production phone and TV
metadata methods against an explicit model of AOSP's certificate-collection flag
behavior across API26/27/28/29/30/35. Old modern-only flags return no signer; the
combined flags return a signer; unsigned results remain rejected. This is not a
substitute for actual platform cryptographic verification.

The optimized pre-fix x86_64 release was installed and its device base.apk SHA256
verified. The APK suite then reproduced the certificate issue. A separate TV rerun
was blocked by a system "System UI isn't responding" dialog, not an app assertion
about decoded playback. After the single allowed emulator recovery, the executor
session was replaced during final-fix validation. The final sender+receiver signature
fix and final replacement signing identity have therefore NOT been installed or
executed on Android in this run. Earlier 57/57 debug video/UI checks and JNI/process
checks remain valid for their exact earlier hashes only. No further emulator restart
was attempted and no failed/incomplete test was relabeled as a pass.
