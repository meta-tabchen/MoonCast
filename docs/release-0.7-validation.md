# 0.7 validation / 验证范围

VersionName **0.7**, versionCode **57**. Final runtime/source local build commit is `5ab1b3064d57ddd02416d6e3734eabc818d66b1e`. Public main/tag `v0.7` targets `70ee18591e4ae6fed1c7d42e96338d53189d1871`, with the exact same Git tree `0489e0fa1bca7ade608db7ff84582835f0d80a88`. The local SHA is a build-provenance identifier, not a claim of a publicly browsable commit. Final changes after reviewed candidate b677abd were limited to connecting-state CSS and its cancellation fixture.

版本0.7/code57。最终本地构建提交为 `5ab1b3064d57ddd02416d6e3734eabc818d66b1e`，公开main／v0.7目标为 `70ee18591e4ae6fed1c7d42e96338d53189d1871`，二者Git树完全相同：`0489e0fa1bca7ade608db7ff84582835f0d80a88`。本地SHA用于构建溯源，不冒充公开可浏览提交。相对先前b677abd候选，最终仅修连接中控件CSS并增加取消测试。

The source archive is verified; the first CI needed a test-resolver-only repair and the rerun passed. The manual device job was skipped. See the latest CI/source section below. This is not an all-green or user-device acceptance claim.

源码包已核验；首次CI需要仅测试解析修复，重跑已通过，手动设备任务跳过，详见下方最新CI／源码段，不代表全部通过或用户设备验收。

## Changes checked in source / 源码变更

- `gradle/bouncycastle.gradle` pins the official BOM-compatible set: **bcpkix 1.85, bcutil 1.85, bcprov 1.85.2**. These are intentionally not three identical version strings.
- Host initialization catches `LinkageError` as well as ordinary exceptions. `HostFailureStore` stores a bounded stage/error for the current session, actively publishes before Stop, and permits later UI retrieval after a short-lived host exits. It does not store PINs or identity keys.
- Connecting-state controls keep cancellation reachable. The final deferred-createOffer fixture checks Disconnect while an offer is still pending; the 21 browser fixtures and added cancellation case passed on Mac.
- Phone protocol tabs use narrow-layout wrapping rules. Browser fullscreen handling includes page-level immersion when native APIs are absent/rejected, auto-hide/touch-restored controls and safe-area layout. Browser/system bars can remain visible.

## Evidence and limits / 证据与限制

| Check / 检查 | Result / 结果 | Boundary / 边界 |
|---|---|---|
| Protocol tab checks / Tab检查 | 88 checks + 18 mocked layout cases / 88项及18个模拟布局用例 | Not physical 320/360dp device runs / 非实际窄屏设备实测 |
| Browser fixtures / 浏览器夹具 | 21 desktop-browser cases / 21个桌面浏览器用例 | No physical iPhone Safari run / 无iPhone Safari真机验证 |
| Resource localization / 资源 | 803 entries checked / 803项 | Not every screen visually accepted / 非逐页视觉验收 |
| Identity regression / 身份回归 | Old mixed BC set failed; corrected set passed fresh/existing/signature/repeated-ensure checks / 旧混合依赖失败，新配套首次／既有／签名／重复ensure通过 | Temporary directories; no real user identity deleted or rewritten / 临时目录测试，未删除或改写真实用户身份 |
| Final APK identity / 最终APK身份 | ARM64 and ARMv7 independently checked for signature and BC type references / 两ABI独立核验签名及BC类型引用 | Not runtime interoperability / 非运行互通 |
| Android lint / 静态检查 | 0 errors, 11 warnings / 0错误、11警告 | Not a security audit or runtime pass / 非安全审计或运行验收 |
| Mac portable OriginalMediaServerTest | Connection-reset failure retained / 保留连接重置失败 | Must not describe the whole local suite as passed / 不能称本地全套通过 |
| Main CI / 主分支CI | b8be236 host/build checks passed / 主机与构建检查通过 | Manual device job skipped / 手动设备任务跳过 |

The regression baseline was the actual known-working **0.4.6 APK**, identified and hash-checked during investigation, not a Git-parent 0.3.1 approximation. Its earlier BC1.77 set differed from the unified phone1.77/TV-provider1.85.2 mixture. That mixture's missing initialization references were corroborated by APK inspection and host reproduction. The repair addresses that reproduced dependency failure; it does **not** establish full GameStream interoperability or that a particular user's phone now works.

基线为已核验版本和哈希的实际0.4.6 APK，不能用Git父版本0.3.1代替。旧混合组合的缺类经APK与主机复现交叉确认；修复此依赖问题不等于完整GameStream互通或用户手机已复测恢复。

## No additional device acceptance / 未追加的设备验收

No new physical phone/iPhone test, full GameStream session, Safari system-bar hiding, actual remote audio listening or broad network compatibility is claimed. Desktop API-missing/rejected fixtures test fallback logic, not physical Safari chrome behavior. Preserve earlier code54 GIFs and code55 quality observations with their exact historical identities.

未追加手机／iPhone真机、完整GameStream、Safari系统栏隐藏、真实扬声器听音或广泛网络兼容验收。桌面缺失／拒绝API用例验证降级逻辑，不代表真实Safari外壳行为。code54影像和code55画质观察保持原始历史标注。

## Operational boundaries / 运行边界

Do not clear user pairing data to troubleshoot this startup defect. Inspect a bounded returned error first. Fixed community package/certificate, trusted-LAN signaling, source-app capture restrictions and public-test-signing risk remain unchanged. Numeric 0.6.0 remained a draft and is not a prior public release. See [engineering pitfalls](engineering-pitfalls.md), [release procedure](release-process.md), [browser evidence](browser-validation.md) and [source scope](browser-release-source.md).


## DEX check scope / DEX检查边界

The BC verifier checks type-reference closure, not complete method linkage or a proof that every optional path is guarded. Three optional JSSE type references remain reviewed exceptions; do not describe their presence as a full safety proof. Both final ABI APKs were independently checked for identity and BC type-reference scope. This remains static evidence.

BC检查是类型引用闭包，不是完整方法链接检查，也不证明每条可选路径都有保护。三项可选JSSE类型引用保留为审阅例外，不能称作完整安全证明。最终两个ABI的签名及该类型引用范围均独立核验，仍属静态证据。

## Final APK hashes

- ARM64 `MoonCast-0.7-arm64-v8a.apk`: `dfb81195e668df8de38569d51d5221d5f28da1b8e87d40fdcf1ed1cb799041fa`.
- ARMv7 `MoonCast-0.7-armeabi-v7a.apk`: `ef21c1bae6df8be8c117f3dbd842af8b6a37463fb57224bfe0011e95ebacd99d`.


## Source archive and CI follow-up / 源码归档与CI后续

`MoonCast-0.7-source.zip`: **362,291,996 bytes**, **9,962 files**, SHA-256 **`1bb2cf7e0a8504fa4d630aa96137f039bb0513a0f10aeaa87d798cef150eecd8`**. It preserves the tag70ee source tree matching the original Mac5ab build. The notices companion includes provenance, the original source-history bundle and the later CI-only patch explanation.

源码包大小 **362291996字节**、**9962文件**，上述SHA-256已确定；保留tag70ee与原始Mac5ab一致的源码树。声明附件包含来源映射、原始源码历史bundle及后续仅CI补丁说明。

The first CI on `70ee18591e4ae6fed1c7d42e96338d53189d1871` failed in the host-test dependency resolver: raw Android classpath artifact types were ambiguous. This was a test-task resolution failure, not evidence of an APK runtime failure. Main commit **`b8be23646aa7f65ce348a611504d4c1a6b3586f5`** changes only that CI/test resolution to an artifactView selecting Bouncy Castle module JARs from the actual release graph, retaining the tests. It does not replace the frozen runtime/source tag.

首次70ee CI在主机测试依赖解析处失败，原始Android classpath产物类型存在歧义；这是测试任务解析失败，不是APK运行失败证据。main提交 **`b8be23646aa7f65ce348a611504d4c1a6b3586f5`** 仅将该解析改为artifactView，从实际release依赖图选择BC模块JAR，测试保留，冻结运行／源码标签不变。

**CI snapshot:** [run38278828071](https://github.com/meta-tabchen/MoonCast/actions/runs/38278828071) **completed successfully** on b8be236: host/BC tests, ARM64/ARMv7 builds, lint, QR roundtrips, APK type closure and signing guards passed. The manual device job was skipped, not passed. Tag `v0.7` stays at70ee, exactly matching the original build's source tree. The earlier Mac OriginalMediaServerTest connection-reset failure remains disclosed separately.

**CI快照：** 重跑38278828071在b8be236上**成功完成**：主机／BC测试、双ABI构建、lint、QR回环、APK类型闭包与签名检查通过。手动设备任务跳过，不当作通过。v0.7仍指向70ee，与原始构建源码树一致；此前Mac OriginalMediaServerTest连接重置失败独立保留。
