> **Historical 0.5.0 record / 0.5.0 历史记录.** These results apply only to that recorded build, not the 0.5.1 community source or APKs. For current evidence see [community validation](community-validation.md). 下述结果不能作为社区版构建或实机验收结果。

# Unified MoonCast 0.5 architecture and acceptance matrix

## Input boundary

- Phone: delivered 0.4.6/code16 source, SHA256 902bf050099e138d379d7b25d17fa99029e2ce5754af2725d56455472dda683e.
- TV: delivered audiofix 0.4.5/code9, local commit c559a07830727880cc7f0c683b39637fdcf40221.
- New independent application ID; legacy installed apps and signatures are not migrated automatically.

## Design constraints

The phone and TV UI retain separate Android resource namespaces. A single application
owns the launcher and chooses its default role from Android TV/Leanback features and
UI mode, with an explicit remembered override. Roles are mutually exclusive: switching
must release capture, audio, sessions, discovery, transfers and receiver ports first.
MediaProjection always needs Android's user consent. APK installation remains explicit
on the receiving device; no installer permission or pairing/security policy is bypassed.

Phone native hosting already runs in isolated :host/:stream processes. Receiver code
runs in :receiver. Different OpenSSL versions must also use distinct packaged
library names/DT_NEEDED/SONAME; no pickFirst collision workaround is acceptable.
Original sources, patched binary hashes and a portable transformation/build recipe must
be supplied. JNI Java namespaces stay unchanged.

## Required verification before delivery

| Area | Checks | Evidence required |
| --- | --- | --- |
| Build | API26 minimum, SDK35, target35, chosen standalone ABIs, no native duplicate paths | Gradle + APK manifest/ELF inspection |
| Role | phone/TV defaults, remembered override, invalid setting fallback, repeated switch, back/relaunch | host tests plus Android runtime |
| Cleanup | active sender to receiver, receiver to sender, no capture resurrection, resources/ports release | lifecycle tests + runtime process checks |
| Native | correct libraries loaded by each process, JNI methods, distinct crypto dependency graph | ELF checks + runtime load test |
| Discovery | normal third-party AirPlay and MoonCast, APK receiver off, NSD name/type/address variants, restart | existing injected callback tests + runtime where possible |
| Transfer | content URI/ClipData selection, unknown size, optional-split false, path/hash/size limits, explicit install review, settings return | fixture and lifecycle tests |
| Themes | all four selections, corrupt preference fallback, persistence, remote focus, standby-only rendering | tests + actual Android pixels |
| Playback | continuing audio/video under translucent settings, return to playback, rotation/resize/focus | instrumentation/runtime; physical interoperability separately disclosed |
| Audio | FLUSH, empty markers, repeated switches, genuine packet loss and resend, unchanged PCM timing | existing sanitizer native tests |
| Delivery | signature, zipalign, package/label/version, source completeness/secret exclusion and licenses | verified artifact records |

A compile or host-test pass does not establish real iPhone/TV, DRM, OEM encoder,
HDMI output or network interoperability. The final report must label every unrun case.

Executed results and remaining limits: [unified-validation.md](unified-validation.md).
