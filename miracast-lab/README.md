# MoonCast Miracast Lab — diagnostic experiment only

**This module is not a Miracast receiver implementation.** It cannot make a phone
find this app in its system Miracast picker and cannot receive video or audio.
Its separate package (`com.mooncast.miracastlab`) coexists with MoonCast TV without
replacing that app or its identity. No TCL interoperability has been demonstrated.

## What is actually implemented

- An English/Chinese, remote-friendly Android TV diagnostic screen (Android 8/API 26+).
- Real package-manager Wi-Fi Direct feature reporting, service availability,
  current permission status and the device's WFD permission protection level.
- Current Wi-Fi enabled state, location-service enabled state, visible network
  interface **names only**, and H.264 decoder capability metadata. No video is
  decoded during these checks; the codec flags are not a performance test.
- After an explicit button, explanation, and runtime permission grant: initialize
  a Wi-Fi Direct channel and read current state, connection/group information,
  and local WFD metadata where public APIs exist. These are queries, not discovery,
  group creation, connection, or advertising. Actual exceptions and unanswered
  six-second callbacks appear in the report. Closing/leaving cancels pending reads.
- `requestP2pState` and `requestDeviceInfo` need API 29; WFD metadata reads need
  API 30. On API 26, this experiment skips the channel queries because the public
  `Channel.close` API starts at 27. Basic checks and settings still work, without
  asking for an unused runtime permission on API 26.
- An explicit, confirmed system Cast-settings button and a Wi-Fi-settings button.
  Only resolvable, exported, accessible **system** activities are launched, using
  public Settings actions. An absent handler is reported rather than guessed.
- Report copy and user-selected document export. Reports are held in memory;
  this app does not automatically save or upload them. The report omits device
  serials, SSIDs, peer names, MAC addresses, IP addresses and group passphrases.
  Build/manufacturer/model/API, interface names and system component names remain.
  The app has no INTERNET permission, analytics, server, or cloud dependency.

The Android system Cast screen can be a **sender** interface. Successfully opening
it does not establish a receiving capability. Some TCL models have an OEM receiving
application; its availability and behavior must be checked on the actual TV.
No published TCL app-level receiving API was verified in this investigation.

## Why adding ordinary P2P or RTSP code would not finish Miracast

A discovered Miracast receiver needs appropriate Wi-Fi Display information in the
Wi-Fi Direct discovery path, followed by negotiation and a media receiving stack.
Android's public [`WifiP2pManager.setWfdInfo`](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager#setWfdInfo(android.net.wifi.p2p.WifiP2pManager.Channel,%20android.net.wifi.p2p.WifiP2pWfdInfo,%20android.net.wifi.p2p.WifiP2pManager.ActionListener))
was added in API 34 and requires `CONFIGURE_WIFI_DISPLAY`.
[AOSP declares that permission as signature-level](https://android.googlesource.com/platform/frameworks/base/+/c1a90f3a96af/core/res/AndroidManifest.xml#4866).
An ordinary sideloaded application's runtime-permission dialog cannot grant a
signature permission. Below API 34 the operation is not public SDK API.

That is the first unverified/blocked prerequisite for an ordinary third-party
APK on this TV. A standalone RTSP server or P2P group would not establish that
phones can discover it as a Miracast receiver. The lab therefore does not emit
WFD advertising, start a pretend waiting-for-cast screen, or infer sink support
from the presence of Wi-Fi Direct or an H.264 decoder. Even if a vendor firmware
legitimately grants the WFD prerequisite, a real receiving stack remains necessary.

No hidden API reflection, root, signature-permission modification, OEM certificate,
HDCP bypass, or privileged credential is used or requested.

Additional official references:

- [Wi-Fi Direct connection and permission guide](https://developer.android.com/develop/connectivity/wifi/wifi-direct)
- [Public WFD role metadata](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pWfdInfo)
- [Cast settings action and absent-activity caveat](https://developer.android.com/reference/android/provider/Settings#ACTION_CAST_SETTINGS)
- [Wi-Fi Direct Channel cleanup](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager.Channel#close())

## Local build and boundaries

From the repository root, with JDK 17+ and Android SDK 35:

```sh
./gradlew :miracast-lab:assembleRelease :miracast-lab:lintRelease
python3 miracast-lab/verify_boundaries.py
```

The release variant deliberately has **no signing configuration**. Its unsigned
APK is a compile artifact, not an installable user delivery. No key is created or
borrowed by these instructions. Any separately authorized signing/delivery needs
to identify the experimental signing identity and does not create a production
MoonCast TV signing identity. Do not publish private device reports with source.

Compilation, lint, and source-boundary checks are not Android-device tests and
are not evidence of working Miracast.

## Manual device checklist (not yet executed on TCL)

1. Install an explicitly authorized, signed experiment APK. Verify MoonCast TV
   remains separately installed and unchanged. Launch the lab using the remote.
2. Check the initial report without granting any permission. The app must not
   claim to be discoverable or ready to receive. Photograph the model/API,
   Wi-Fi Direct/service and permission-protection results.
3. Select the Wi-Fi Direct check, cancel the explanation, then try again and
   deny the OS permission. Basic report/settings remain usable and denial is
   shown; there must be no connection or advertisement.
4. Grant the relevant permission when you choose. On Android 12 select precise
   location if required by the framework; on Android 13+ use Nearby devices.
   Capture each actual callback result or exact error. A timeout is inconclusive.
5. Repeat the check, press Back/Home during callbacks, reopen, and refresh.
   Old callbacks must not overwrite a later run. No group or scan is started.
6. Open system Cast after its explanation. If unavailable, expect the absent
   handler message. If it opens, manually determine whether it sends or receives;
   never classify a sender picker as Miracast receiving support.
7. Try Wi-Fi settings, return, then explicitly refresh. Existing network settings
   must not be silently changed by the lab.
8. Copy the report; inspect its lack of addresses and peer names. Use Save report
   only if the TV supplies a document picker. Canceling the picker writes nothing;
   unavailable storage/permission is shown as an error. Otherwise take photos.
