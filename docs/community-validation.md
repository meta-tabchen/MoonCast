# Community 0.5.1 validation / 社区版验证

**Overall CI: failed.** The aggregate build-verification job passed, but device jobs did not complete successfully. This is a preview with partial emulator evidence, not a fully validated UI or physical-device release.

**整轮 CI 失败。** 聚合构建验证任务通过，但设备任务未全部成功。这是具有部分模拟器证据的预览版，不是已完成 UI 或实机验收的版本。

## Exact build / 对应构建

- Source / 源码：`27967d778c55042bb2adc926074bc3b1d94e19b9`.
- Tag / 标签：`community-v0.5.1`; development branch / 开发分支：`unified/community`.
- [CI run 38203238592 and raw logs / CI 与原始日志](https://github.com/meta-tabchen/MoonCast/actions/runs/38203238592).
- Version / 版本：`0.5.1-community`, versionCode `51`, package / 包名：`com.mooncast.community`.
- Certificate SHA-256 / 证书：`290e4372fff33c00848758b12275936900b2eccad053dc63c18622e1a1222bb2`.
- Owner initialization commit / 用户初始化提交：`cd39378fceeea1814df1b3e849d1588081aa7735`.
- Exact artifact names, sizes and hashes / 产物文件名、大小与校验和：[community-artifacts.json](community-artifacts.json).

A later documentation-only commit does not change the APK/source provenance above. The public certificate provides update continuity, not maintainer authentication.

后续文档提交不改变上述 APK／源码来源。公开测试证书用于升级连续性，不证明维护者身份。

## Passed build and host checks / 已通过的构建与主机检查

- Both ARM release builds, lint, ZIP alignment, package/certificate checks and missing/wrong-signing rejection checks passed. / 两个 ARM 架构 release 构建、lint、ZIP 对齐、包名／证书与签名缺失或错误拒绝检查通过。
- All **9,864** corresponding-source inventory entries matched their hashes. The archive excludes the **MoonCast app-signing PKCS12 fixture**; unrelated upstream cryptographic test fixtures remain. / 完整源码 **9864** 项输入哈希一致，源包不含 **MoonCast 应用签名 PKCS12**，上游密码学测试材料仍保留。
- Host unit suites passed. Localization work includes 724 resource entries, 17 language-policy checks and 20 message checks; discovery has 112 host checks. These are not proof that every UI page or real receiver works. / 主机单元测试通过，中英资源 724 项、语言策略 17 项、消息 20 项及发现 112 项不能替代实际界面或接收设备验证。
- The earlier fixed-port listener close/reopen race was reproduced and fixed; targeted regressions passed 119 checks × 20 runs and 1,000 port-48152 reopen cycles. / 前期固定端口关闭重开竞争已复现并修复，定向回归通过 119 项 × 20 轮与 1000 次 48152 端口重开。

## Android 15 / API 35: partial runtime evidence

A real x86_64 emulator installation upgraded a **synthetic versionCode-50 baseline to 51** with the same package and certificate. First-install time and a private-data marker survived without uninstalling. This validates that tested synthetic update path only: it is neither an upgrade from historical 0.5.0 (different package/signature) nor a physical-device installation test of the published ARM APKs.

Android 15 x86_64 模拟器实际执行了同包名、同证书的**合成 versionCode 50→51** 覆盖升级，未卸载并保留首次安装时间和私有数据标记。此结果仅对应该合成升级路径，不代表旧 0.5.0 能覆盖升级，也不是发布 ARM APK 的实机安装结果。

The **system-language selection stage passed**, including ten distinct English/Chinese resource groups and system-language resolution. The **next locale process start hit an ANR**. Remaining language, page, cancellation and screenshot checks did not complete. Logs recorded CPU saturation, but no startup thread trace was captured. The cause is **unknown**: neither an environment-only explanation nor an application defect has been established.

**system 语言选择阶段通过**，包括十组中英文资源区分与系统语言解析；**后续语言进程启动发生 ANR**，余下语言、页面、取消交互和截图检查未完成。日志记录到 CPU 满载，但未取得启动线程栈，**根因未知**，不能断言只是环境问题或已确定为应用缺陷。

## Android 11 / API 30: runtime not validated

The emulator lost its package-manager service during installation. One standard non-streamed retry also failed. App runtime, UI, upgrade behavior and screenshots on this platform did **not** pass validation.

模拟器安装期间包管理服务消失，一次标准非流式安装重试仍失败。此平台的应用运行、界面、升级和截图验证**未通过**。

## Not established / 未建立的证据

- Complete locale/UI/cancellation regression / 完整语言、页面与取消交互回归。
- Actual UI screenshot or GIF: **none usable from this run**. README images are model-generated concept art. / 本轮**没有可用真实界面截图或 GIF**；README 图片为模型概念图。
- Phone-to-physical-TV/iPhone interoperability, audio/video continuity, real-content cropping, latency, power savings and final device installer behavior. / 手机到真实电视／iPhone 互通、音画连续性、实际内容裁剪、延迟、省电及实机系统安装流程。

## Language behavior and remaining boundary / 语言行为与边界

The implementation confirms session cleanup before an in-app language change; Cancel or selecting the current language keeps the session. Full interaction regression did not finish. A language change from Android Settings may leave an active notification in the old language until stopping and restarting the session. [Language guide](unified-guide.md#language--语言).

实现中，应用内更改语言先确认结束会话；取消或选择当前语言不停止会话，但本轮完整交互回归尚未完成。从系统设置切换语言时，现有通知可能到停止并重开会话后才刷新。[语言说明](unified-guide.md#language--语言)。

## Historical evidence and downloads / 历史记录与下载

[0.5.0 remains available / 旧版仍可下载](https://github.com/meta-tabchen/MoonCast/releases/tag/unified-v0.5.0-preview). [0.5.0 validation](unified-validation.md), [architecture checks](unified-architecture-validation.md) and [old artifact hashes](unified-artifacts.json) remain explicitly historical. Their pass labels and hashes must not be attributed to 0.5.1.

The dependency bootstrap still downloads hash-pinned legacy native inputs from the 0.5.0 source archive. The **new 0.5.1 complete-source archive corresponds to the app commit above**. These archives serve different purposes; restoration does not replace current application code. See [build instructions](unified-build.md).

依赖引导仍从旧 0.5.0 源包取得哈希固定的原生输入；**新 0.5.1 完整对应源码属于上方应用提交**。两者用途不同，恢复依赖不会将当前应用代码替换成旧版。详见[构建说明](unified-build.md)。

## Follow-up startup diagnosis / 后续启动诊断

[Diagnostic run 38206995486](https://github.com/meta-tabchen/MoonCast/actions/runs/38206995486), on the separate `diagnostics/community-startup` branch, rebuilt only the exact released commit's x86_64 debug and instrumentation APKs. Both matched the prior APK bytes (debug SHA-256 `18142c09116fdab2bebcc26cd92de2117c68d648cfc7c81ae7e71600d1d3d4fd`; test `d5bfa343ffa6f7aff01765cad300c660cac6b498c3e1ac92123442d1a7106361`). The fixtures were saved before starting the emulator.

The first cold `verify/system` start failed before any `select/system` language change. The captured main thread was **Runnable**, in Android's `Security.increaseVersion` → `insertProviderAt` → `NetworkSecurityConfigProvider.install` → `ActivityThread.handleBindApplication`, with no MoonCast frame in that stack. Its scheduler statistics showed approximately **2.294 seconds running versus 30.152 seconds waiting on the run queue**. CPU pressure was 89.59%; system phone, Google services and keyboard processes also reported startup ANRs. Even before installing MoonCast, the captured system CPU sample was 99% busy. The held class lock in the stack is not evidence of a monitor-wait deadlock.

Taken together, this strongly supports software-emulator scheduling starvation for this observed failure. It does not establish that every application path is correct, or that physical devices behave the same way. No production-code fix was isolated, no ANR threshold was relaxed, and the same configuration was not repeatedly rerun until green. The original failed CI result remains a failure. Full UI regression, real-device casting, screenshots and GIFs are still pending; this diagnosis does not turn them into passed checks.

The run retains reconstructed fixtures, full logcat, ANR/exit diagnostics and an exact-byte manifest as Actions artifacts. [Linux scheduler field definitions](https://www.kernel.org/doc/html/v6.12/scheduler/sched-stats.html) explain the runtime/run-queue-wait interpretation. Artifact retention is finite; a missing or expired artifact is not additional verification.

中文：上述独立诊断只重建已发布提交的 x86_64 debug 与测试 APK，两个文件均与原始 SHA-256 完全一致，且在启动模拟器前保存。第一次冷启动 `verify/system` 就失败，尚未执行语言切换。主线程处于 Runnable，在 Android 安全配置初始化调用链中，没有 MoonCast 方法栈；实际运行约 2.294 秒、排队等待约 30.152 秒，CPU 压力 89.59%。系统电话、Google 服务及输入法也发生启动 ANR，安装 MoonCast 前的系统采样已达 99% CPU。

这些证据强烈支持本次软件模拟器发生严重调度饥饿；不代表已经排除所有应用问题，更不能代替真机验证。没有定位到需要修改的产品代码，没有放宽 ANR 阈值，也没有重复跑到通过为止。原 CI 失败事实保留。完整 UI、真实设备投屏、截图和 GIF 仍待验证。原始 APK、完整日志和诊断清单已保存为该次 Actions 产物，须注意其保留期限。
