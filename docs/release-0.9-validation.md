# MoonCast 0.9 / code66 development candidate

This is not a release announcement. Public 0.8 assets/tags remain unchanged.
Package `com.mooncast.community` and the existing community test certificate remain fixed.
The public fixture provides upgrade continuity, not publisher authentication.

## Implemented candidate behavior

- Manual floating controls work outside MoonCast when the user enables overlay permission: drag to an edge, expand, pause/resume picture and captured audio, restore the phone, or stop. The ongoing notification is the fallback. Whole-screen Android capture may include the small bubble; `FLAG_SECURE` is deliberately avoided because it can produce a captured black rectangle.
- **Physical screen-off is currently gated unavailable in the candidate** pending target-device wake, privilege-loss and stop/crash restoration validation. This is a safety gate, not completion of the requested feature. The following test/lease design is implemented but not enabled for general use.
- The phone stays bright by default. There is no automatic panel-off on start or reconnect. Physical panel-off requires an available Root/Shizuku helper, overlay permission, an active session and a per-session device check. The first attempt is a 15-second safety test after a cancellable 3-second countdown. A transparent guard consumes the first return touch. Only the user seeing the phone restored can confirm the touch path; hardware input while the panel is off is not guaranteed by Android. Power-key/double-tap behavior is not claimed.
- After successful device confirmation, manually requested panel-off can continue while casting, with a renewable helper lease. Restoration cancels renewal and another manual action is needed to turn it off again. Lost ownership, disconnect and stop request restoration. Helper crash/vendor display behavior still requires target-device validation.
- One device-name setting is used across phone/TV advertising. Busy-session changes are saved for the next start, without replacing UUID, certificates or pairing identity. A Moonlight application card is a stream title, distinct from the host/device identity.
- GameStream respects the client's negotiated viewport/FPS; it does not force 4K. Capture size is recomputed from live outputs so a 4K→1080p/720p transition can reduce input work. The TV shows physical display mode separately from requested and actual decoded video. A 4K TV output mode does not prove a 4K stream.
- Receiver lifecycle/native-session/decoded-frame events are retained in a bounded private timeline, with previous-process exit reasons where Android exposes them. No content, URL, network address, device name or pairing secret is recorded by this timeline. Nothing is uploaded automatically.

## Evidence and remaining gates

- Initial Java/UI candidate `13aa031ec2e3606079401f4991f03ae002c3bba6`: [unified CI 39032492522](https://github.com/meta-tabchen/MoonCast/actions/runs/39032492522) passed host/wire regressions, both ARM builds, lint, signing and APK/native-byte checks. The manual UI/device job was skipped. Those APKs retain the prior receiver native runtime.
- Host tests cover 720p/1080p/4K output-limit transitions, pause-related pipeline policies, bounded timeline and existing display-power lease behavior. Synthetic decoder host checks are not Android MediaCodec or end-to-end sessions.
- The pinned receiver callback helper attaches native pthreads to Java without detaching them at thread exit. A mock-JNI ownership regression shows 64 owned attachments retained by the baseline versus 64 detached by the proposed patch, while 8 Java-owned threads remain untouched. This establishes the ownership defect in the archived source and the patch's host semantics, not the reported TV crash's root cause.
- The separate receiver candidate workflow rebuilds all three receiver ABIs with pinned NDK27/CMake3.22.1 and records source/archive/patch/output hashes. Its test uses a real sender in `:stream`, native receiver in `:receiver`, production MediaCodec and actual red/blue pixel readback, then disconnects and reconnects. It intentionally bypasses LAN discovery, phone capture and audible audio. Results are pending until recorded here.
- Physical phone/TV tests, continuous panel-off/touch return, vendor overlay capture, genuine Moonlight resolution reconnects and the user's failing MoonCast→MoonCast TV pair remain unverified. No unavailable real-TV test is counted as passing.
- The historical Mac `OriginalMediaServerTest` connection-reset failure is retained in earlier records. Passing cloud host tests do not erase it.

## Artifact provenance

Only APKs whose bundled native bytes match their associated rebuilt manifests may be called native-patched candidates. A plain unified build that consumes old pinned binaries is insufficient. Candidate source archives must identify the base Git commit and each generated native override; no source-only fix is presented as shipped.
