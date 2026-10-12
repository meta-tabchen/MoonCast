# Browser QR and quality controls

This local preview extends the already published browser mode. In **Browser**,
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
