# Co-installable TV preview

This explicit experiment is separate from the installed TV receiver:

- Application ID: `com.mooncast.tv.preview`
- Launcher label: **MoonCast TV Preview / MoonCast TV 共存预览**
- Launcher/JNI classes retain `com.mooncast.tv` and the original native bridge namespaces.
- Version: `0.4.0-preview-coexist` (code 4), Android 8.0/API 26+, ARM64/ARMv7/x86_64.

The preview uses an **already-existing local Android debug/test signing identity**,
only after explicit build opt-in. It never creates a new signing key. This key is
not a promised permanent release identity: a future preview may require removing
and reinstalling this preview app. The old app is not replaced and its data is
not migrated or deleted. The ordinary release variant retains its separate
external-signing gate and has no debug-key fallback.

**Stop the old TV receiver before opening this one. Run only one receiver app at
a time**, since AirPlay and DLNA may compete for their network ports. Likewise,
stop the old phone sender before using the co-installable phone preview.

## Actual support

AirPlay mirroring/audio, DLNA network-media playback and the integrated view-only
GameStream client are implemented. Android phone AirPlay requires the companion
phone sender's **AirPlay** tab; GameStream uses its **Moonlight / Sunshine** tab
and TV-side host selection/pairing. This does not add the TV to every Android
system Cast menu. Miracast, Google Cast and Lelink reception are not implemented.
The separate `miracast-lab` module is diagnostics only and is not this receiver.

Only use a trusted home LAN; AirPlay PIN is disabled as requested. Remote crop,
proportional zoom and the app-wide framing preference remain available.

## Build and verification

After restoring the pinned dependencies as in the receiver guide, use an existing
local test keystore at `$ANDROID_USER_HOME/debug.keystore` (or the Android default
user-home location):

```sh
./gradlew :tv:assembleCoexist :tv:lintCoexist -PmooncastTvCoexistPreview=true
python scripts/verify_tv_apk.py tv/build/outputs/apk/coexist/tv-coexist.apk --coexist-preview
```

Without that opt-in, the coexist variant is unsigned. With opt-in but no existing
keystore, configuration fails before Android's debug-key generation task can run.
No private signing material belongs in Git, source archives or delivered files.

The co-installable APK passed build/lint, v2 APK signature and ZIP alignment
verification, manifest/application-ID checks, and 15 native library byte comparisons
against the tested original-namespace build. AirPlay and GameStream portable
lifecycle regressions were rerun. Earlier actual Android renderer tests validate
the shared implementation, not a physical phone-to-TV session or a fresh device
installation of this package ID. Physical interoperability/latency remain unverified.

## Smaller standalone ABI packages

For a complete single-architecture APK, add `-PmooncastTvAbi=arm64-v8a` or
`-PmooncastTvAbi=armeabi-v7a` to the same build command; verify with the matching
`--abi` argument. These are individually installable APKs, not split-install parts.
Install only the version supported by the television's Android OS. An ARM64 CPU
can still run a 32-bit Android OS, which needs `armeabi-v7a`. Protocols, signing
identity, namespace and native-library bytes remain unchanged. The default build
still contains all three supported architectures.
