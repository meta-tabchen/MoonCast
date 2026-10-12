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
