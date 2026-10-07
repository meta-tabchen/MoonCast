# Roadmap

Priorities, not delivery promises. Open an issue before large changes.

## Current preview

- [x] Android → existing Moonlight streaming with a pinned native core.
- [x] Full-screen / heuristic crop / fill / centered 16:9 modes.
- [x] Optional playback audio and recoverable local media muting.
- [x] Optional Accessibility and experimental Root input implementations.
- [x] English and Chinese primary UI resources and bilingual project documentation.

Implemented does not mean validated on every device. See [validation](validation.md).

## Next priorities

- [ ] Device/receiver compatibility matrix with reproducible video, audio, mute, and input tests.
- [ ] Real-player border-detection reports, dark scene/overlay/rotation regression coverage.
- [ ] Full native-source rebuild in a documented clean toolchain.
- [ ] Production signing/update policy before a stable release.
- [ ] Further localization of lower-level diagnostics and translated setup reports.
- [ ] Measured latency/quality/thermal behavior using a published test method.

## Explore after the foundation is stable

- [ ] Better player-region selection without user-drawn boxes.
- [ ] More input paths and richer non-Root interaction where Android permits it.
- [ ] Multiple concurrent receivers, with encoder/bandwidth limits made explicit.
- [ ] Original-file photo/video serving as a separate path from screen mirroring.

HDR, AV1, protected-content capture, and Internet hosting are not promised for this preview.
