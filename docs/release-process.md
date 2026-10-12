# Release procedure / 发布流程

This is the project workflow for an explicitly authorized release. It does not authorize a new release by itself. Continue independently within the user's approved scope; if an action is denied or outside that scope, stop that action, report the blocker and use an authorized next step. Never evade a denial or bypass a security check. A newer user version/scope decision supersedes an older pending release plan.

本流程服务于明确授权的发布，不自行扩大权限。授权内自主完成修复与核验；遇到拒绝或超范围操作应停止该项并报告，禁止绕过拒绝或安全检查。用户的新版本／范围决定优先于旧的待发布计划。

## 1. Freeze identity and scope before building

- Primary source branch: **main**. Normal application target: **`:unified`**.
- Copy the user's numeric version exactly. Current request: **0.7**, tag **v0.7**, title **MoonCast 0.7**. Do not silently change it to 0.7.0 or append browser/community/preview.
- Determine a strictly increasing versionCode from real prior artifacts. Do not infer its final value from a document template. About must show the installed package's version/code; build identity remains separate.
- Keep package **com.mooncast.community** and the existing fixed certificate SHA-256 **290e4372fff33c00848758b12275936900b2eccad053dc63c18622e1a1222bb2**. Do not create a new key, regenerate the fixture or silently fall back. Normal signing checks fail closed.
- State what changed and what did not. Do not describe 0.7 development as a final verified build before evidence exists.

## 2. One writer, no lost concurrent edits

Use one designated publisher for remote refs, tags and Release mutations. Other contributors prepare local patches/overlays and report their base commit. Before applying them, reread current files, preserve hand edits and inspect the diff. Before updating a branch/ref, reread its current SHA and use an expected-SHA guard where available. If it changed, reconcile; do not force-push or overwrite another writer's work. An uncertain tool response requires a readback, not a blind repeat.

Keep the release tag tied to the approved runtime/source snapshot. Later README-only commits can move main without rewriting that historical tag. A code change after signing requires a new build/verification decision, not merely editing a manifest.

## 3. Build and validate the actual release

Use [unified build instructions](unified-build.md). Produce **arm64-v8a and armeabi-v7a** release APKs. For each, verify package, versionName/code, certificate, debuggable/testOnly state, ZIP alignment, resources, native payloads and relevant DEX reference closure. Check the resolved release dependency graph and [identity/startup regressions](engineering-pitfalls.md), not only source declarations.

Run applicable build/lint, portable tests, localization checks and negative signing cases. Existing identity tests use temporary directories; never delete real user pairing data. Preserve SDK locks/licenses. Confirm no private production material, live LAN URLs/QR tokens, identifiers or personal screenshots entered artifacts.

Record checks individually as **passed, failed, skipped, not run, timed out or blocked**. Overall CI success/failure is separate from an individual job. A skipped device job is not device acceptance; an emulator is not a physical TV/iPhone. Retain meaningful failures and explain later independent recovery without retroactively relabelling them. Do not request or run additional device work if the latest approved scope excludes it.

## 4. Prove build/source provenance

Record the runtime build commit, source archive snapshot, public commit/tag, tree hash and artifact hashes. If a local commit could not be pushed and a public commit was reconstructed, verify **identical Git tree hashes** and publish the mapping. Different commit IDs with the same verified tree are explainable; do not claim the local SHA is publicly browsable or that arbitrary “equivalent” trees are identical.

The source archive should preserve the actual build snapshot and input inventory. If needed, a provenance JSON and small original source-history bundle can document the mapping, after checking for excluded secrets/signing material. A manifest is evidence only when its entries match the archive. Keep application source, pinned legacy dependency restoration and external SDK source access distinct.

For WebRTC, retain fixed source/DEPS coordinates, build/fetch mapping and original notices. Do not call an application archive a full offline SDK checkout or claim independent native SDK rebuilding when it was not performed. The intentionally public community Git fixture remains separate from the Release archive: exclude the **MoonCast app-signing PKCS12**, not every upstream cryptographic test fixture indiscriminately.

## 5. Prepare the Release before moving download links

For version 0.7, use:

- `MoonCast-0.7-arm64-v8a.apk`
- `MoonCast-0.7-armeabi-v7a.apk`
- `MoonCast-0.7-source.zip`
- `MoonCast-0.7-notices.zip`
- `SHA256SUMS`

Use bilingual English/Chinese notes with the same feature, verification, risk and upgrade scope. Keep package/signature continuity visible; the public test signer is not publisher authentication. Separate measured results from intended behavior and acknowledge remaining protocol/device limits.

Use the requested **Latest** classification when publishing the authorized release. Latest and numeric naming are presentation choices, not guarantees of stability. Do not mark a newer draft public merely because a superseded release had permission. Do not delete/rename old tags, assets, Release notes or links to hide superseded work or failures.

## 6. Verify public downloads, then update README

After publication, independently read back the public Release, tag target and asset inventory. Download each intended public file and compare its actual bytes, size and SHA-256 with the approved local artifact and published checksum list. Verify APK identity again from downloaded bytes where practical. Seeing an asset name, upload completion or a redirect alone is insufficient.

Only then switch both READMEs' badge, primary download navigation, version text, asset filenames and source references together. Keep older releases accessible. Preserve original version labels on historical GIFs/screenshots; do not turn old media into current-build evidence. Check all README/doc links from main's full tree and check both languages.

## 7. Close with exact status

Confirm the approved commit/tree, tag, Release, both APKs, source/notices and checksum file are publicly accessible and match. Report exact CI outcome for that commit, including skipped/failed devices, and any remaining blocker. If CI is still running, report running and continue the authorized observation; never call it passed prematurely. Do not claim “complete” while a required download/hash/source check remains unresolved.

## Historical scope

The older 0.5.0 dependency-bootstrap asset is intentionally still used for pinned legacy inputs; that does not make a newer app build version 0.5.0. Historical 0.5.1 and code54/code55 media and validation keep their own provenance. The c0936675193c49e3bcd64bb0a76171059b9f9210 build's last change from d44a4c4 was icon-only and had no extra device run; this historical fact is not 0.7 acceptance.
