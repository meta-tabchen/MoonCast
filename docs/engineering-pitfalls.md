# Engineering pitfalls and regression gates / 工程踩坑与回归门槛

This is an investigation and engineering checklist, **not a declaration that 0.7 has passed final APK or device acceptance**. Record each result against the exact artifact/commit. Preserve useful failures as well as passes.

本文记录已定位问题及工程检查，不代表0.7最终APK或用户手机已验收。每项结论对应明确产物／提交，不删除失败证据。

## 1. Unified dependencies can break identity before a server binds

### Failure mechanism

The working phone 0.4.6 artifact used the Bouncy Castle 1.77 set. The unified app mixed phone bcpkix/bcutil 1.77 with TV-selected bcprov 1.85.2. The actual packaged DEX lacked classes such as OIWObjectIdentifiers/Misc identifiers referenced by the older `OperatorHelper` initialization path. `HostIdentity.ensure` runs before native server binding; a linkage failure there can prevent the host from listening at all. It is therefore not sufficient to blame discovery, IP selection or the network because a receiver cannot connect.

The old mixed combination was reproduced as a host-side `NoClassDefFoundError`, and APK inspection corroborated the missing-class dependency path. The corrected **official BOM-compatible set is bcpkix 1.85 + bcutil 1.85 + bcprov 1.85.2**, not three libraries with the same version string. Host checks of fresh identity creation, parsing/signature behavior, existing identity and repeated ensure without rewriting PEM passed during investigation. Those checks do not establish final 0.7 APK or user-device success.

### Required gates

1. Inspect the resolved `:unified` release runtime graph, including transitive selections from phone and TV modules. Declared Gradle versions alone are insufficient.
2. Test certificate/key creation in a new **temporary directory**, then parse and exercise signing. Run ensure repeatedly and verify expected existing PEM contents are byte-for-byte unchanged.
3. Test a known existing identity fixture and compatibility with prior formats. Do not reset a user's real identity or pairing data. A fresh-install pass cannot replace an existing-user check.
4. Inspect final release DEX classes and referenced dependency closure after shrinking. Verify the actual initialization reference path, not only that an arbitrary BC class exists.
5. Retain the old mixed-set negative reproduction; exercise the corrected set and final packaged release separately. Add a user-visible, actionable error if identity setup fails.

身份初始化早于原生监听：BC混用造成缺类时，接收端“连不上”可能发生在服务器绑定之前。必须检查统一release实际依赖和最终DEX；使用临时目录测试首次与既有身份，反复ensure不得重写已有PEM。禁止清除用户配对数据来掩盖问题。配套版本是pkix/util1.85与prov1.85.2，而非三个同号。

## 2. A fast host exit can hide the real error

The observed host path can schedule process termination about **250 ms** after stopping while the UI polls about every **1500 ms**. An initialization error followed by teardown can disappear before the next poll. A generic “stopped” state or an empty error does not establish user cancellation.

Report the failure through the existing service/UI notification path **before** releasing the host, with a bounded reason and lifecycle-safe delivery. Keep cleanup deadlines and process ownership checks. Test failure before native bind, failure after connection, repeated Stop and disconnected/unavailable UI. Do not publish private addresses, keys or tokens in diagnostic output. An error-path fix needs explicit checks that the user can see the reason, not just that a log line was emitted.

约250ms结束host可能早于1500ms界面轮询，导致错误被吞。应主动回传错误再清理，保留生命周期与时限约束；不能只靠慢轮询，也不能把所有错误显示成正常停止。

## 3. Use the real regression baseline

For the phone startup regression, the relevant known-working baseline is the actual **0.4.6 APK**, not a repository parent labelled 0.3.1. Repository ancestry does not imply the artifact someone used had that content. Record package, versionCode, certificate and artifact hash for both working and failing APKs. Compare resolved dependencies and packaged DEX/native inventories. Keep source reconstruction and runtime build identities separate where they differ.

实际工作基线是0.4.6安装包，不能用Git父提交0.3.1替代。对照时记录双方真实版本、签名、哈希及依赖／DEX差异。

## 4. Protocol tabs must survive narrow phones and translation

Use an equal-width layout with intentional **two-line** labels rather than a one-line fit that clips or pushes a tab out. Check 320dp and 360dp widths, English and Chinese, and larger system font scales. Inspect full words, icon alignment, selected/unselected contrast and touch targets; do not insert breaks that make English words unreadable. Exercise all tabs, repeated taps and switching while a session is active. An attractive tablet screenshot is not narrow-phone evidence.

协议Tab需两行等权布局，检查320/360dp、中英文及大字号，不截词、不挤出第三个Tab。平板好看不能替代窄屏检查。

## 5. Safari immersion is not control over the browser chrome

Detect fullscreen API availability and handle both missing APIs and rejected promises. Offer a **page-level immersive fallback** with controls hidden after a bounded idle interval, restored by touch, and placed within safe-area insets. Keep a discoverable exit and accessible controls. Test missing API, rejected request, normal exit and repeated entry; do not assume a successful JavaScript call means the device entered native fullscreen.

Do not promise that the web page hides Safari's address/tab/system bars. Device/browser policies decide that. A desktop mobile viewport, emulator or synthetic event test can verify layout/logic, but cannot be called a physical iPhone Safari test. Clearly label the tested browser, device and mode. No permission or security-warning bypass is an acceptable fullscreen workaround.

Safari API缺失或拒绝时降级为网页沉浸：控件自动隐藏、触摸恢复、安全区布局和可发现退出。不承诺隐藏浏览器系统栏，不把模拟器或桌面移动视口称作iPhone实测，不绕过权限与安全检查。

## 6. Capability and media evidence need exact scope

Hardware capability preflight can find one supporting encoder while WebRTC selects another of the same MIME type. Treat this as a compatibility risk, not a universal observed failure. Use actual receiver dimensions, decoded FPS and RTP-video throughput; distinguish them from requested ceilings, sender encoding bitrate and total LAN traffic.

Retain previous Clear/Smooth observations with their tested version. A failed automated Stop observation stays failed even if a later independent check confirms cleanup. Software QR decoding is not a physical camera scan. Decoded audio energy is not speaker listening or A/V synchronization. Historical code54 GIFs are not proof of newly added UI, icons or later builds.

## Related guidance

[Build and signing](unified-build.md) · [Release procedure](release-process.md) · [Version naming](versioning.md) · [Browser evidence](browser-validation.md) · [QR and quality boundaries](browser-qr-quality.md)
