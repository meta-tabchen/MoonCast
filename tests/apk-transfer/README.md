# MoonCast single-APK LAN transfer core

Pure Java 8-language source shared between the existing phone and TV apps. No Android dependency, new app, third-party HTTP library, remote fetch, shell execution or install implementation.

## Integration contract

Copy `src/com/mooncast/transfer/*.java` into each application's Java source root (retain matching copies). Tests here run directly with `./run-tests.sh` and a JDK supplied through JAVA_HOME. The stripped local JDK lacks `ct.sym`, so tests use `-source 8 -target 8`; this is a language check, not proof of API compatibility. All production APIs are available on the apps' API-26 minimum.

TV, only after the user opens Receive APK:

1. Select the active private-LAN IPv4 address. Construct `ApkTransferServer(privateDirectory, bindAddress, peerPredicate, listener)`; predicate should restrict peers to that network. Call `start()` off the main thread. Display `Session.code` and five-minute expiry. Register `ApkTransfer.SERVICE_TYPE` (`_mooncast-apk._tcp.`) using NsdManager with `Session.port`. The OS assigns a dedicated unused port. TXT may contain protocol version `v=1`; never include code or capability. Do not advertise outside the explicit screen's session.
2. Listener callbacks run on background threads. Post UI changes to the main thread. `onProgress(received,total)` updates progress. `onReceived(file,sha256)` transfers completed-file ownership to the Activity. `onError(message)` displays failure, cancels discovery and offers a fresh session. Close server and unregister NSD on cancellation/background/expiry. Closing deletes partial data but intentionally preserves a completed APK.
3. Parse the received APK using Android PackageManager, including signing certificates. Reject unparsable archives, split packages, missing certificates or manifest/application data. Show verified app label, package ID, version, signer SHA-256 fingerprint(s), size and payload SHA-256. A container check is not signature verification or malware scanning. Never launch the received application automatically.
4. Offer explicit Install/Cancel. Only after Install, request Android's install-unknown-apps permission when necessary, then launch the system installer with a narrowly configured nonexported FileProvider, one file's read URI grant, and APK MIME type. Handle permission denial, cancellation and installer absence without automatic retries. Android verifies installability/signature compatibility and owns the final install confirmation. Retain the completed file while permission/installer flow needs it, then delete it. Clean stale private staging files on the next controller start; do not delete an APK currently offered to an installer.

Phone:

1. Discover only `_mooncast-apk._tcp.` services; NSD labels are untrusted. A resolved service alone is not authorization. User chooses the TV, and the app asks for the six-digit code shown there.
2. Choose an ordinary APK using ACTION_OPEN_DOCUMENT. `.apks`/`.xapk` are unsupported. Stream its content URI into a private staging file while enforcing 1 GiB maximum and disk reserve. Do not trust content-provider length/name alone. Hash the finalized spool with `ApkTransfer.hash(file)`; never log URI/code/token. Let the user see the selected file, TV and plain-LAN warning before sending.
3. On a background executor create `ApkTransferClient(address, port)`. `pair(code)` returns an in-memory single-session capability; call `send(file,originalName,sha256,token,progress)`. Close client on cancellation and delete spool on success/cancel/failure. An admitted upload consumes the capability even if its body transfer fails; require a new receive session. No persisted credentials or automatic retries.
4. “Received on TV” means only that transfer completed; it never means installed. If connection drops after the TV committed the file, tell the user to check TV rather than retrying automatically.

## Wire protocol v1

- HTTP/1.1 on an OS-assigned dedicated LAN port; only exact `POST /v1/pair` and `PUT /v1/apk` accepted. Connection always closes after one request.
- Pair body: exactly six ASCII digits with Content-Length 6. Correct code yields 64 lowercase hex characters (256 random bits), only once. Five failed code attempts permanently lock pairing for that receive session. Pairing and upload authorization are synchronized and comparison is constant time. Capability is bound to the pairing peer's address.
- PUT headers: Authorization `Bearer <capability>`, X-APK-Name URL-encoded display filename, X-APK-SHA256 64 hex characters, fixed Content-Length. One active upload/session. A capability is consumed once an authenticated upload passes metadata checks and is admitted for body transfer, including a later interruption, checksum failure, or storage failure. Requests rejected before admission do not consume it. The phone never retries automatically. No chunked encoding, Expect, duplicate headers, absolute URLs, redirects or remote fetch.
- 1 GiB maximum, 32 MiB retained free-space reserve, 16 KiB headers, 4096-byte line, 32 headers, 15-second idle server read timeout, 20-second client read/pair timeout, absolute five-minute client upload watchdog that closes blocked-write sockets, absolute five-minute session limit (monotonic elapsed time). Bounded two workers/two queued sockets; no unlimited threads or backlog. Expiry and close interrupt active sockets.
- Bytes land in a generated app-private .part path, never the sender filename. Verify exact declared bytes and SHA-256, ZIP central directory, unique bounded entry count and a plausible binary AndroidManifest.xml; then rename to generated .apk and hand off to Android parsing. A too-short body, disk error, mismatch, bad ZIP, cancellation or expiry deletes partial data. Any trailing bytes/pipelined request are ignored on the connection, never stored or processed.

## Trust and limitations

The TV deliberately opens a short receive window and shares its code only on-screen. This prevents unsolicited uploads from ordinary unpaired LAN clients and avoids persistent credentials, but **HTTP is unencrypted**: a hostile shared-Wi-Fi/router observer can see the code, APK and capability and may impersonate a peer or alter content plus claimed hash. SHA-256 detects corruption, not an authenticated sender under active network attack. IP matching is not cryptographic identity. Use only a trusted private LAN. Never describe the feature as encrypted or secure against a hostile LAN. mDNS names and file names are untrusted display data.

Only the Android system installer can establish installability/signature checks; the core does not declare an arbitrary ZIP safe. This implementation does not implement resumable transfers, split APK bundles, app extraction, silent/root installs or code execution. Device interoperability, install-permission flows, rotation/background behavior and NSD routing require Android device tests separately.

## Host verification

`./run-tests.sh`: happy-path pairing/upload, 256-bit capability shape, exact byte SHA, completed-file retention, extension/traversal/bundle rejection, unauthenticated/replayed/null tokens, five-attempt lockout, hash mismatch, truncation and cleanup, size limit, duplicate CL, TE+CL, absolute request target, oversized header, monotonic expired authorization, concurrent upload, close cancellation, blocked client-write cancellation and the absolute client watchdog. Generated ZIPs contain a minimal manifest header solely to exercise transfer boundaries; they are not installable Android APKs.
