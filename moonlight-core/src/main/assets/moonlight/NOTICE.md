MoonCast GameStream receiver core uses Moonlight Android v12.2 under GPLv3.
Copyright and license notices from upstream are retained in corresponding source.
This is an independent embedded integration, not an official Moonlight release.

Protocol source: moonlight-stream/moonlight-android b48494cb96bff23d8886c4775cc4f39a1075495d.
Native dependencies: moonlight-common-c (GPLv3), ENet (MIT), nanors (MIT),
OpenSSL 4.0.2 (Apache-2.0), Opus 1.6.1 (BSD), Android NDK cpu-features (BSD).
Java dependencies and sources: see JAVA-DEPENDENCIES.json in corresponding source.

Modifications: isolated SDK35 library; strict host certificate pin, no HTTP
fallback for paired hosts, no redirects or verbose secret-bearing URL logs;
cancellable one-shot native lifecycle; LAN-only Desktop view/audio facade.
No system/root privilege, remote input UI, commercial AirPlay SDK, or external
Moonlight application launch is used by this core.

The complete source distribution for the exact APK includes these sources,
patches, build scripts, dependency sources, licenses, and pinned runtime hashes.
Build/static tests do not establish host interoperability or physical TV behavior.
