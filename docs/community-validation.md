# Community 0.5.1 validation / 社区版验证

Status snapshot: 2026-10-12. The ARM build, lint and signing checks have passed at e6ae6ff; emulator/device validation remains pending. Update each row only from the exact build/run evidence. Historical 0.5.0 results do not certify this version.

截至 2026-10-12，e6ae6ff 的 ARM 构建、lint、签名检查已通过；模拟器和实机验证仍须分别完成。只能依据对应构建与运行证据更新结果，不能套用历史 0.5.0 验收。

## Identity and source / 身份与源码

- Branch: `unified/community`.
- Candidate: `0.5.1-community`, versionCode `51`, application ID `com.mooncast.community`.
- Owner-run signing initialization commit: `cd39378fceeea1814df1b3e849d1588081aa7735`.
- Public test certificate SHA-256: `290e4372fff33c00848758b12275936900b2eccad053dc63c18622e1a1222bb2`.
- The certificate establishes update continuity, not publisher authenticity. The current verified candidate is commit `e6ae6ff36e765bf393f72dc20e5cacddc3de12c3`; this document does not announce a published release.

## Available evidence / 已有证据

The following host-side checks have run; they are not Android runtime results. Record the final CI run and commit when integrating later fixes.

| Check / 检查 | Current result / 当前结果 | What it does not prove / 不代表 |
|---|---|---|
| English/Chinese resource coverage / 中英资源覆盖 | 724 resource entries checked / 已检查 724 项 | Every string visible on an actual screen / 全部实际界面呈现 |
| Language policy / 语言策略 | 17 host checks passed / 17 项通过 | System locale lifecycle on all ROMs / 所有 ROM 语言生命周期 |
| Messages / 消息 | 20 host checks passed / 20 项通过 | Physical device presentation / 实机显示 |
| Discovery / 设备发现 | 112 host checks passed / 112 项通过 | All routers and AirPlay receivers / 所有网络与接收端 |
| APK listener shutdown/reopen / APK 监听关闭重开 | First CI exposed a pre-existing close/rebind race; the fix passed 119 checks × 20 runs and 1,000 fixed-port 48152 reopen cycles / 首次 CI 暴露关闭重绑竞争，修复后重复回归通过 | Android system installer, physical-network behavior / 系统安装与真实网络 |
| Initial community CI / 首轮社区 CI | Failed at the listener race; not an Android build pass / 因上述竞争失败，不是 Android 构建通过 | Completed release validation / 已完成发布验收 |

## Pending release gates / 待验证项

- Final release commit's Android build and lint; candidate e6ae6ff passed ARM64/ARMv7 builds and ARM64 lint.
- Community APK package, versionCode, certificate, debuggable/testOnly state, ZIP alignment, native inventory and payload hashes.
- Final corresponding-source archive and SHA256SUMS; candidate archive independently verified all 9,857 inventory hashes and excluded the MoonCast app-signing PKCS12 fixture.
- In-place community update check using a previous community APK when one is available. The old 0.5.0 uses a different package/signature and is a one-time migration, not an update fixture.
- Actual emulator UI screenshots and language switching. No gallery exists yet; model-generated art is not a UI screenshot.
- Physical phone/TV/iPhone interoperability, playback audio continuity, cropping on real content, and final Android installation flow.

以上 Android 构建、签名 APK 核验、源码包、社区升级、模拟器界面以及实机互通均需分别记录真实结果。构建通过、主机测试通过或模型配图都不能替代设备验收。

## Historical records / 历史记录

[0.5.0 validation](unified-validation.md), [architecture checks](unified-architecture-validation.md) and [0.5.0 artifact hashes](unified-artifacts.json) remain unchanged in substance and explicitly historical. Do not copy their pass labels or hashes onto community artifacts.

## Known locale boundary / 已知语言边界

An external Android Settings language change may leave an active service notification in its prior language until that session is stopped and restarted. In-app language changes explicitly clean up the previous session. Live external-switch notification refresh is not validated; see the [language guide](unified-guide.md#language--语言).

从安卓系统设置直接更换语言，现有服务通知可能在停止并重开会话后才刷新。应用内切换会明确结束旧会话；外部切换期间通知即时刷新尚未验证。

## Current CI / 当前 CI

[Run 38196873831](https://github.com/meta-tabchen/MoonCast/actions/runs/38196873831), commit e6ae6ff: all host suites, missing-fixture/mismatched-pin fail-closed guards, ARM64 build/lint, ARMv7 build, native graph and both APK certificate/package checks passed. API30/35 x86_64 release/debug/test fixtures compiled; emulator boot and UI/update tests are still pending in this snapshot. No actual screenshot or runtime-upgrade pass is claimed here.

该次 CI 已通过主机测试、签名拒绝负例、两 ARM 架构构建、lint、原生库与签名包名检查；两种 Android API 的测试安装包也已编译。模拟器启动及实际界面、覆盖升级仍待结果，不计为通过。

## Device harness findings / 设备测试框架排障

Run 38198883114 at d6c1f54 repeated the build/signature/native checks successfully, including ZIP alignment. API30 completed software-emulator boot, but the harness rejected the test APK's optional version fields before installation; no app/UI/upgrade pass resulted. API35 initialized its emulator without KVM but did not confirm Android boot during the bounded attempt. Both evidence archives were retrieved and checked against their GitHub digests. The next harness revision accepts absent version metadata only for the test package while retaining mandatory application version, package and certificate checks, adds fixture regressions and explicit awake/unlocked preconditions, and bounds each boot probe.

该轮再次通过构建／签名／原生库及 ZIP 对齐检查。API30 已启动，但测试脚本把测试 APK 的可选版本字段误当必需字段，在安装前失败；API35 无硬件加速时未确认启动完成。两者均不计应用运行、覆盖升级或 UI 通过。下一轮修复只放宽测试包版本元数据，应用版本／包名／签名校验不变，并增加预检回归、屏幕状态检查和严格启动探测时限。
