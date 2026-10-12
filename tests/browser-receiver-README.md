# Browser receiver integration fixture

This test launches a separate headless Chrome, creates a synthetic canvas video and
440 Hz oscillator audio track, and connects that sender to the production receiver
page through a real `RTCPeerConnection`. Its HTTP signaling fixture defaults to
`127.0.0.1`, uses host ICE candidates, and does not access a microphone, camera,
desktop, Android device, or external signaling service. The synthetic label remains
visible in the generated video.

Requirements: Node.js 18+, Chrome, and an existing installation of `puppeteer-core`.
Optional `ffmpeg` produces a small WebM and GIF of the actual receiver page. No npm
manifest or dependency is added to the Android product.

```sh
PUPPETEER_MODULE=/path/to/node_modules/puppeteer-core \
CHROME_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' \
BROWSER_EVIDENCE_DIR=/tmp/mooncast-browser-evidence \
node tests/browser_receiver_smoke.cjs
```

The runner uses `require('puppeteer-core')` unless a module path is specified, and
looks in conventional Chrome locations on macOS/Linux. The test does not add `--no-sandbox`, disable browser
certificate checks, override autoplay policy, or use the user's Chrome profile.

To validate an ordinary, insecure LAN HTTP origin, set `BROWSER_BIND_ADDRESS` to an
IPv4 address already assigned to this test computer. The runner rejects addresses
that are not its own. It never scans the LAN or changes firewall rules. This exposes
the synthetic fixture on that interface while the test runs, so use a trusted LAN.

By default both peers retain Chrome's normal mDNS candidates. Some environments
cannot establish the Chrome-to-Chrome connection with these candidates. The explicit
`BROWSER_NATIVE_CANDIDATE_ADDRESS` option changes **only the synthetic sender's SDP
answer**: mDNS candidate hostnames become the supplied own-interface address, with
the original ports unchanged. Use the actual interface to which those candidates
belong. The production receiver, its offer, and browser privacy settings are unchanged.
This fixture mode models a native sender's numeric host candidates; it does not
validate Android WebRTC, Android's mDNS support, or real LAN device interoperability.
The report records the mode and number of rewritten candidates. Retain failed
default-mode evidence separately instead of presenting this mode as an unqualified
pass.

```sh
PUPPETEER_MODULE=/path/to/node_modules/puppeteer-core \
BROWSER_BIND_ADDRESS=<this-computer-private-IPv4> \
BROWSER_NATIVE_CANDIDATE_ADDRESS=<same-interface-IPv4> \
BROWSER_EVIDENCE_DIR=/tmp/mooncast-native-answer-fixture \
node tests/browser_receiver_smoke.cjs
```

The JSON report records the Chrome/Node/Puppeteer versions, working-tree base commit
and dirty flag, receiver asset SHA256 hashes, ICE/RTP/media evidence, individual
checks, and limitations. The asset hashes identify the tested code; a base commit
alone does not identify uncommitted receiver changes.
Screenshots cover 375, 768, and 1440 CSS pixel widths. The short screencast shows
actual receiver controls and synthetic moving video; it has no audio track.

Verified scope includes video pixels/advancing decoded frames, incoming nonzero
audio energy, explicit sound unmute and audio-capability downgrade, Fit/Fill,
zoom/pan/reset, fullscreen, languages, lost-answer recovery, competing-viewer
rejection, reload takeover, reconnection after sender/heartbeat failure, bounded
retries, and explicit stop cleanup. Incoming audio and an unmuted video element do not establish
physical speaker audibility. A successful run is **not Android-to-browser end-to-end
validation**, nor evidence for LAN policy, physical-device capture, protected media,
hardware encoding, TV browsers, latency, or audio/video synchronization.

Both fixture files are project-authored GPL-3.0-only test code. Puppeteer is an
external Apache-2.0 test dependency ([source](https://github.com/puppeteer/puppeteer));
Chrome and ffmpeg are separately installed local tools, not distributed with the
app. Test media is generated locally and contains no third-party media.
