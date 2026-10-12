# MoonCast trusted-home APK protocol v2

GPL-3.0 project-authored Java core, shared byte-for-byte with TV. Protocol v2 replaces
v1 code/capability pairing and is deliberately incompatible with old APK receivers.
No credentials are generated or retained. AirPlay and GameStream pairing are unaffected.

## Wire contract

- User explicitly opens Receive APK on TV; five-minute, single-admitted-upload window.
- Bind the active private LAN IPv4 on fixed TCP port **48152**. Binding conflicts fail
  visibly, never silently select a random port. Peer predicate restricts the subnet.
- `GET /info` returns protocol 2, apk true, pairing false, stable non-secret receiver UUID,
  port 48152, maxBytes 1073741824, state ready/receiving/unavailable. No file data.
- `PUT /v1/apk` uses fixed Content-Length, URL-encoded X-APK-Name, X-APK-SHA256.
  No pairing endpoint or Authorization header is needed. Success body is `received`.
- DNS-SD `_mooncast-apk._tcp.` advertises port 48152 and TXT v=2, apk=1,
  pairing=none, id=receiver UUID while the receive screen is active.
- HTTP/1.1 only, no redirects, chunking, Expect, duplicate headers, remote fetch, shell
  execution, or installer implementation in the core.

## Bounds and trust

1 GiB payload, 32 MiB free-space reserve, one admitted attempt (also consumed on interrupted
body), two workers/two queued sockets, 60 requests/minute per receiving window, 16 KiB headers,
4096-byte line, 32 headers, 15-second server idle timeout, 20-second client read timeout,
five-minute monotonic session/upload watchdog. Closing interrupts sockets and deletes partials.
Generated app-private staging paths ignore sender filenames. Exact length/hash, bounded ZIP
central directory/entries/resource inflation and binary manifest structure are checked before
Android PackageManager checks standalone manifest/package/signers.

This is **unencrypted, unauthenticated trusted-LAN HTTP**. Anyone on the LAN may upload while
receiving is enabled, observe bytes, impersonate a TV, or modify payload and claimed hash.
SHA-256 detects accidental corruption, not authenticated origin. DNS-SD names/IDs and IP
association are hints, not cryptographic identity. Do not enable on guest/public/untrusted Wi-Fi.
The TV user separately reviews the received package, taps Install, and confirms the Android
system installer; unknown-source permission is handled on TV. No silent installation or
execution. Phone receipt acknowledgment never means installation succeeded.

## Tests

`python scripts/run_apk_transfer_tests.py` verifies production/shared source hashes and runs
real localhost sockets against generated non-installable fixtures: pair-free transfer,
fixed-port conflicts, info, lifecycle/expiry/cleanup, limits, concurrency, malformed framing,
checksum mismatch, ZIP boundaries and blocked-write cancellation. Device discovery, SAF,
OEM installer UI and actual phone-to-TV interoperability require separate Android evidence.
