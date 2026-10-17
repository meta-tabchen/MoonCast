# Manual casting controls in 0.9

## 中文

1. 在手机端打开设置 → **投屏悬浮控件**，主动开启并授予“显示在其他应用上层”权限。拒绝权限时仍可用投屏通知里的操作。
2. 开始投屏后，可以回到抖音或其他应用。拖动小按钮到屏幕边缘，点开操作面板；**停止**固定在顶部，其余操作可滚动。是否允许悬浮窗仍受 Android 和前台应用限制。
3. **暂停/恢复**暂停画面及采集音频，保持连接；恢复时使用当前画面。**调暗手机屏幕**有三秒可取消倒计时，随后向系统请求该窗口的最低亮度。第一次触摸恢复亮度并吞掉完整手势，抬手后下一次触摸才交给原应用。恢复后不会自动再调暗。
4. 开始、重新连接默认保持亮屏。调暗不改动系统保存的亮度；停止、断开、锁屏、撤销悬浮权限或窗口进程退出会取消窗口亮度覆盖。系统导航键和状态栏不在全屏触摸保护的保证范围内。
5. **Root/Shizuku 物理关屏尚未开放为通用功能**。目前可用的调暗是系统窗口亮度控制，并不等于面板断电，也没有手机耗电测量结论。具体手机的最低可见亮度、抖音连续播放和捕获效果需实物验证。

## One device name

Phone settings and TV advanced settings expose the same saved display name. Rename during a session does not restart that session; the new value applies when the corresponding sender/receiver starts again. Persistent UUIDs, certificates and pairing records are not replaced. Names are limited to 60 UTF-8 bytes; `.local` is reserved by the receiver protocol. DNS-SD labels may be shortened to preserve their required MAC prefix or APK suffix within 63 bytes.

## English

Enable **Floating casting controls** in phone settings, then grant overlay permission if wanted. Notification controls remain available when permission is declined. During casting, drag the bubble to an edge and expand it over another application. Stop stays in the fixed header; secondary actions scroll.

**Dim phone screen** uses a transparent window-scoped minimum-brightness request after a cancellable three-second delay. The first complete touch gesture restores brightness and is consumed; a fresh gesture then reaches the underlying application. Restoration stays bright until another manual request. It does not alter saved global brightness or claim physical panel-off/power savings. Android or another application may suppress overlays.

Root/Shizuku physical panel-off remains gated pending device-specific restoration/fault validation. See [the exact test record](release-0.9-validation.md) for generated-media, Android-window and physical-device boundaries.
