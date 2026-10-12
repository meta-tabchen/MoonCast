# v0.3 actual APK UI validation / 真实界面验证

Recorded on **2026-10-11** using a disposable official AOSP Android 13/API 33 x86_64 emulator,
1280 × 720, 213 dpi, software CPU and SwiftShader. These are screenshots of installed Android
views, not design mockups. This is not a physical Android TV or sender-interoperability result.

## 中文结论

- 界面冻结版本在实际 Android 上完成 **30/30 条中文离线断言**：打开即请求接收、PIN 配置关闭、首次 Auto、
  遥控焦点与设置弹窗、返回不停止接收请求、裁剪取消、两条播放器管线共享全局选择及跨 Activity 重开保留。
- 「返回播放」及连续 Back、编辑取消、嵌套设置退出、会话结束后退出确认，也通过实际按键与页面断言。
  **这些播放导航用例由测试 APK 注入会话状态，没有收到 AirPlay、没有 DLNA 播放、没有视频解码。**
- 前一构建的中英文各 30 条离线断言均通过；最终仅再去掉自定义焦点上重复的系统灰色矩形，随后中文 30 条重跑通过。
- 像素检查发现并修复中文标题第二行被裁切及重复焦点底色。截图来自修复后的实际 APK，未放入公共仓库。
- 最后补验的英文正常 / 紧凑布局通过；中文紧凑布局（约 640 × 360 dp）说明文字第二行底部仍有轻微裁切。
  主标题、名称、三按钮、焦点和页脚完整；这项次要瑕疵保留为已知问题，不能称中文紧凑布局全部通过。
- 模拟器没有活动默认网络，只有 loopback；界面如实显示连接网络提示。严格 LAN 用例通过启动请求断言后，
  未能启动 DLNA，不能记为通过。早先 Android TV API 36 与 AOSP 首次启动出现系统 UI/系统应用 ANR；
  清理一次性模拟器的系统弹窗后，离线界面测试可正常完成。未降低应用的网络限制。
- 真实局域网重连、iPhone AirPlay、远端 DLNA、解码、声音、字幕、延迟与电视 HDMI 输出仍需实机验证。

## Reproducible scope

The device-tested debug APK SHA-256 is
`d915038cfa363b0b037334b1caaaaf46edd969032109d61b3684d10a0da2bede`.
The separate test APK SHA-256 is
`293378c7d17521475a321f410892f23e8330b7dd3bd72db7d99ae5249c85bf34`.
The published release APK is rebuilt and signed by CI; it has its own checksum and ephemeral
preview certificate. Do not confuse the instrumented local build with the downloaded release binary.

After these device captures, final source review found and fixed one additional offline recovery path:
Stop/Recover with no usable LAN now routes through full receiver startup/cleanup, old suspended
AirPlay status callbacks cannot claim readiness, and the recovery menu no longer overwrites the
network-required message. These three small lifecycle edits received source review and build/host
regression checks, **not another Android execution run**. The screenshots still represent the unchanged
visual layout; the device-tested APK hash intentionally differs from the final rebuilt artifacts.

Build `:tv:assembleDebug :tv:assembleDebugAndroidTest`, then run
`scripts/run_tv_ui_validation.py --serial emulator-5554 --output <directory> --offline
--locales zh-CN --allow-clear-emulator-app-data` on a disposable emulator.
Without `--offline`, the unchanged strict suite requires a suitable LAN and may accept a generated
H.264 fixture for actual DLNA/decoder checks. Those stricter checks did **not** pass in this environment.
CI compiles the test APK but does not execute this device suite.

[Exact final Chinese assertions](assets/tv-v03/ui-checks-zh.json) ·
[APK and private screenshot hashes](assets/tv-v03/evidence.json).

## Screenshot handling

Actual home, advanced-settings and Return-to-playback UI screenshots were inspected.
The Return screen uses the explicitly simulated session described above. Generated screenshots
are kept outside this public repository for private delivery to the requester; no personal
phone/TV photo is included. The public evidence JSON retains their hashes for traceability.
