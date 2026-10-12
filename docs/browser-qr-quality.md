# Browser QR and quality controls

This preview extends the already published browser mode. In **Browser**,
start sharing and accept Android's normal capture consent. The connection card
then displays a QR code automatically, with **Copy link** for the same complete,
temporary LAN URL. There is no extra QR button or expansion step. A QR-capable browser/device may open it; typing or copying remains the
fallback when scanning is unavailable. Both devices must reach the same trusted
LAN. MoonCast encodes the QR locally, without an external QR service or uploading
the private session path. Stop clears the QR, and a new sharing lifetime generates
a new link. Anyone who obtains an active link may attempt to connect; do not
publish a live code or link.

**Quality** saves the settings for the next sharing lifetime. Changing it during
sharing does not restart or modify the active capture. Stop and start normally
when ready to use the new limits; Android may request a fresh capture consent.
The default keeps the code-54 configuration that was validated on a real tablet.

| Preset | Maximum source long edge | Maximum frame rate | Video bitrate ceiling |
| --- | --- | --- | --- |
| Smooth | 720 pixels | 24 fps | 2 Mbps |
| Balanced (default) | 1280 pixels | 30 fps | 8 Mbps |
| Clear (device-aware) | Highest candidate up to 3840 that the hardware reports supporting | Prefer 30, then 24 or 15 fps for detail | Highest supported candidate up to 24 Mbps |

Advanced controls select long-edge limits from 640, 720, 960, 1280, 1920, 2560, 3200 and 3840 pixels;
15, 24, 30 or 60 fps; and 1, 2, 4, 6, 8, 12, 16, 24, 32 or 40 Mbps ceilings.
A combination is accepted only when Android reports compatible hardware encoding
for the actual source dimensions, frame rate and bitrate. The codec still must
initialize successfully; reported capability is not a sustained-performance test. The profile is captured
when starting and retained across capture resize/rotation and viewer reconnects.
Output never intentionally upscales the source, and codec dimensions are even.
WebRTC checks whether the encoder parameters are accepted rather than silently
ignoring rejected limits. Unsupported settings can fail a connection; choose
Balanced or Smooth and start again if the device cannot sustain Clear.

The bitrate is a ceiling, not a constant rate or a promised network throughput.
WebRTC adapts the actual video quality, bitrate and frame rate to the encoder,
content, CPU and network. Higher limits can increase heat and bandwidth. Clear
does not promise lossless video. The integration starts its video budget at up
to 2 Mbps; the existing 300 kbps minimum is unchanged. Actual output sizes and
rates must be measured on the sender/receiver combination, not inferred from
the preset label.

Receiver **Fit/Fill/Zoom** changes how the received image is displayed. It does
not increase captured detail or change the sender's encoding profile. AirPlay 2 also offers an advanced output-size/frame-rate/bitrate entry using
reported H.264 capabilities; the old presets and default remain. These changes
take effect on the next sharing lifetime. AirPlay 1 retains its existing
1280×720/30fps/4Mbps limits. This update does not change DLNA or GameStream
negotiation. A TV receiver cannot force the independent source sender to emit
a higher bitrate.

## Verification boundaries

`BrowserQualityTest` covers the verified default, profile bounds, source sizes,
portrait/landscape equivalence and no upscaling. The QR test decodes generated
synthetic links back to the exact input and checks margins/size/error behavior.
A software decode is not a physical camera scan. `browser_android_receiver.cjs`
records actual pixels, decoded frames and measured RTP video throughput; measured
receive throughput is not the sender's exact encoding bitrate or total LAN traffic.
New preset and camera-scanning device evidence must be recorded separately from
the code-54 real-device evidence. Do not reuse old screenshots as proof of this UI.

## Development-round measurements / 本轮开发观察

The release is `0.6.0-browser-preview.4`, versionCode 55. These observations
belong to pre-icon source snapshot `d44a4c4`. Final icon-only build identity is
recorded below; no additional final-build device run was performed.

Short observation windows recorded Clear reception at **3200×2136**, approximately
**25.2 decoded fps** and **2.6 Mbps RTP video**, against target ceilings of a
3840-pixel long edge / 30 fps / 24 Mbps. Smooth reception was **720×480**, about
**22 decoded fps** and **0.48 Mbps RTP video**, against 720 / 24 / 2 Mbps ceilings.
These are receiver observations, not guaranteed sustained rates, the sender's
exact encoder bitrate or total network traffic.

Clear passed five checks. Smooth's automated Stop observation timed out; that
failure remains recorded. An independent subsequent observation confirmed Stop
and closure of the old link. This does not turn the timed-out automated check
into a pass. Balanced, rotation and physical camera scanning were not tested in
this round. Software decoding of generated QR pixels is distinct from camera
scanning. Existing code-54 recordings remain labelled historical evidence.

本版本为 `0.6.0-browser-preview.4` / code55，图标变更前源码为
`d44a4c4`；最终图标构建身份见下文，但未追加真机验证，不能把观察值写成最终图标构建重新实测。短窗观察：清晰目标长边3840／30fps／24Mbps，实际接收3200×2136、
约25.2解码fps、2.6Mbps RTP视频；流畅目标720／24／2，实际720×480、约22fps、
0.48Mbps。这些不是持续性能、发送端精确编码码率或全部网络流量。

清晰5项检查通过；流畅自动停止观察超时，保留失败记录，随后独立检查确认停止和旧链接
关闭，不能把原自动检查改记为通过。本轮均衡、旋转、真实摄像头扫码未测试。二维码
软件像素解码与摄像头扫码是不同证据；已有code54录屏继续保留历史版本标注。


## Final icon build and static encoder-selection risk

Final source/runtime is `c0936675193c49e3bcd64bb0a76171059b9f9210`, version
`0.6.0-browser-preview.4` / code55. Its delta from `d44a4c4` is icon-only.
No additional device testing was performed on this final icon build at the user's
request. The measurements above remain pre-icon functional evidence.

Capability preflight can accept any encoder that reports support for a requested
combination, while WebRTC may actually choose another encoder with the same MIME
type. That selected encoder need not support the same advanced combination. This
is a statically identified compatibility risk, not a device-observed universal
failure. It does not negate the measured tablet Clear/Smooth results. On other
devices, inspect the actual receiver statistics; if connection or encoder setup
fails, choose Balanced/Smooth and restart sharing.

最终源码／运行提交为 `c0936675193c49e3bcd64bb0a76171059b9f9210`，相对
`d44a4c4` 仅改图标。按用户要求未追加最终图标构建真机测试，上文保留为图标修改前
的功能证据。静态兼容风险：预检可能接受某个报告支持目标的编码器，而WebRTC实际
选用同MIME的另一个编码器，其高级组合支持未必相同。这不是已观察的通用失败，
不否定平板清晰／流畅结果；其他设备应看实际统计，失败改均衡／流畅后重开。
