# Android screen → browser receiver (preview)

The new **Browser** tab sends an Android screen to a browser over WebRTC. The first
target is desktop Chrome on Mac. It is a separate MoonCast protocol mode: a web page
does not become an AirPlay, DLNA or GameStream receiver. iPhone mirroring and TV
browser compatibility are not established by this feature.

## Connect

1. Install this branch's community APK and choose the phone role. Open **Browser**.
2. Keep Android and the computer on the same trusted Wi-Fi or Ethernet LAN. Choose
   the whole screen, or Android 14+ single-app capture. Turn on playback audio if
   wanted; it is off by default and separate from microphone or remote input.
3. Select **Start sharing** and approve Android's capture dialog. Copy or type the
   complete temporary `http://<phone-local-ip>:<port>/<session-token>/` link into Chrome.
4. Select **Connect** in the page. Video starts muted. Select **Enable sound** to
   allow audible playback. If the browser pauses video, select **Play video**.
5. Use Fit/Fill, zoom, drag to pan, Reset and Fullscreen. Arrow keys pan; `+`/`-`
   zoom; `0` resets; `F` enters/exits fullscreen. English and 简体中文 are available.
6. Disconnect in the page to release the viewer. Stop on Android or its foreground
   notification to release capture, close the listener and revoke the link.

No account, receiver discovery, PIN or cloud signaling is used. Only one browser
may receive a session. Reloading the same tab and selecting Connect replaces that
tab's peer. A vanished tab expires after 20 seconds without a heartbeat; browser
reconnection backs off and pauses after six attempts. A new Android sharing session
has a different link. If Wi-Fi/IP changes, stop and restart sharing and use the new
link. Guest Wi-Fi/client isolation or blocked local UDP may prevent connection.

## Media and permissions

Android MediaProjection still owns consent and protected-content behavior. The
shared MoonCast `VideoPipeline` samples the capture surface, then renders into a
WebRTC `SurfaceTextureHelper`; WebRTC negotiates a hardware H.264 or VP8 encoder,
packetizes media and sends DTLS-SRTP over LAN ICE. The target is 30 fps with the
longest side at most 1280 pixels and an 8 Mbit/s upper bound, subject to hardware
and network adaptation. This is lossy encoding and has measurable latency.

Android 10+ playback capture uses only media/game/unknown usages that Android and
the source application permit. A bounded 40 ms PCM queue feeds 48 kHz stereo to
the WebRTC audio device callback for Opus. WebRTC's microphone recorder is disabled
before any audio track is created. Capture failure keeps video running; an app
may intentionally produce silence. DRM, capture prevention, notification audio,
call audio and other restricted content are not bypassed. Phone volume is not
changed. Browser mode does not enable Root, Shizuku, remote control or screen-off.

On Android 14+ the capture-content resize callback updates the output dimensions
without obtaining a second projection grant. Older Android versions may retain
the initial encoded aspect ratio after rotation; stop and restart in the desired
orientation if framing is unsuitable. Automatic black-bar detection is not part
of this browser preview; Fit/Fill and manual framing operate on the received frame.

## Browser and network security

The page is receive-only: it does not request the browser's camera, microphone or
screen. `getUserMedia`/`getDisplayMedia` secure-context requirements must not be
used to reject all LAN HTTP receiving. A browser may impose its own local-network,
autoplay or fullscreen policy; the page provides explicit connection and playback
gestures. No certificate installation, browser security override, router change,
firewall expansion or public service is required or configured.

The HTML and SDP signaling use unencrypted HTTP on a private IPv4 listener. Treat
the random 192-bit URL as access to the active shared screen, and keep it within a
trusted LAN. Media is encrypted by WebRTC's negotiated DTLS-SRTP; HTTP signaling
does not authenticate peers against an active LAN attacker. The application does
not create persistent HTTPS certificates or credentials. Standard ephemeral DTLS
peer keys are internal to WebRTC. No STUN/TURN servers are configured. Remote
Internet use, HTTPS hosting, NAT traversal and multiple viewers are out of scope.

The listener serves three packaged assets and bounded JSON endpoints only. It
checks numeric-IP/localhost Host, same-origin Origin and cross-site fetch metadata,
limits request headers/body, bounds workers and timeouts, sets no-store and a CSP,
and closes active sockets on Stop. The link is not logged in native diagnostics.

## Development and evidence

Use the existing [community build instructions](unified-build.md). This branch
uses versionCode **53**, `0.6.0-browser-preview.2`, and the unchanged fixed community
test identity. Both `app` and `phone-ui` resolve the same pinned open-source AAR;
`verifyBrowserWebRtc` checks its SHA-256 before build. Existing protocol binaries,
their source locks and signing fixtures are unchanged.

Run the portable Java suite, localization checks, unified build/lint, identity
verification and native payload verification. The browser fixture and acceptance
evidence are described in [browser validation](browser-validation.md). A synthetic
browser sender verifies only the webpage's WebRTC path and controls. It does not
validate Android capture, hardware encode, allowed playback audio, rotation or
physical-device interoperability.

See [WebRTC provenance](../native/webrtc/README.md) and
[third-party notices](../THIRD_PARTY_NOTICES.md). No closed-source casting SDK is used.

## 中文使用说明

手机角色选择“浏览器”，按需打开播放音频，点击开始并同意 Android 录屏。
在同一可信局域网的 Mac Chrome 中打开手机显示的完整临时链接，点击“连接”，
再点击“开启声音”。可全屏、适应/填充、缩放与拖动平移；停止手机端共享会撤销链接。

无需登录或重复搜索接收设备。此模式只支持 Android 原生发送到网页，不表示网页支持
AirPlay/DLNA/GameStream 或 iPhone 镜像，电视浏览器需要另测。受保护内容可能黑屏或无声；
不能绕过 DRM、录屏限制或音频捕获策略。HTTP 网页/信令只用于可信局域网，
不会要求安装证书、忽略证书警告或关闭浏览器安全策略。网络变化后需重新开始共享。

构建、单元测试和合成轨道网页播放均不能代替 Android 真机端到端验收；准确结果见验证记录。
