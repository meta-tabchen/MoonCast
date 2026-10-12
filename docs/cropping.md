# How MoonCast frames a video

[简体中文](cropping.zh-CN.md) · [Project overview](../README.md)

This document describes the implementation reviewed in unified source `f3566ab3194e660f987dcfabdc5f2ef22fe05255`. It explains behavior, not a benchmark or a guarantee of correct detection on every video.

![Black-border removal concept](assets/mooncast-hero.png)

*Model-generated concept illustration, not a measured screenshot or exact geometric simulation. Removing embedded bars can fill the display only when the retained aspect ratio matches it.*

## Two kinds of empty space

1. **Embedded borders:** black pixels already present in the source capture, for example a centered video inside a wider phone screen. A crop can remove these pixels before fitting the remaining region.
2. **Aspect-ratio letterboxing:** space needed to fit the whole retained picture onto a differently shaped display. A complete 16:9 picture on a 4:3 display still needs bars. Filling the display instead requires losing some content or distorting it; MoonCast's Fit/Auto preserve proportion.

The detector does not discover an application's internal player surface or recognize people, subtitles, movies or UI semantically. It evaluates pixels and geometry. Manual crop and Fill are explicit creative choices, not proof that more useful picture was recovered.

## TV receiver: evidence, stability, safety

### 1. Sample the source

`VideoFraming` reads a **160 × 90** TextureView bitmap. The TV playback tick requests a sample approximately every **500 ms** for the active GameStream, AirPlay mirror or ready media path. Sampling pauses during manual editing. An unavailable surface or failed readback rejects the sample; the bitmap is cleared first so stale content cannot manufacture a lock.

### 2. Find plausible paired borders

`CropGeometry.detectDetailed` works on the small RGBA sample:

- A pixel is near-black only when each RGB channel is **≤ 24**. A pixel is bright for detection when its largest channel is **≥ 52**.
- A candidate border row/column needs **≥ 99%** near-black pixels and **no bright pixel**. Bright sampled subtitles or indicators therefore stop that scan; downsampling can still miss small details.
- Scans are bounded below **45% per edge**. Opposite edges must agree within the larger of 2 sample pixels or 2% of that dimension; rejected pairs are not cropped. The smaller crop of each pair is retained.
- Whole-frame guards require at least 12% non-black and 10% bright pixels. Interior brightness, a **4 × 4** content grid and visible boundary strips reject ambiguous dark scenes.
- Ordinary borders require ≥35% active and ≥22% bright interior pixels plus 8 sufficiently lit grid cells. Wide borders (≥29% per edge) require ≥50% active, ≥35% bright, 12 lit cells and a retained area of at least 24% of the sample.
- Each qualifying grid cell has ≥18% bright pixels; the three-pixel boundary strips also need ≥18%. One sample pixel is returned as a safety margin around the content.

The reported `confidence` is an evidence score, **not a calibrated probability**.

### 3. Acquire, then hold

`CinemaCropTracker` accepts evidence scores ≥0.87 and waits for **8** consistent ordinary-border samples or **12** wide-border samples. Candidate edges must remain within 0.0126 normalized units; the union of observed regions is retained rather than averaging away an edge. Full-frame, dark or rejected samples do not lock a crop.

At the nominal sampling cadence, acquisition typically needs roughly **4 or 6 seconds of qualifying samples**. This is not a deadline: rejected samples, playback readiness and scheduling can extend it. The locked crop does not keep tightening when scenes change.

### 4. Widen if content appears outside

After lock, the safety path looks for at least four sampled pixels with a channel ≥72 outside a cropped axis. Two consecutive consistent observations restore that axis to its full extent. It only widens; it does not reacquire a tighter crop until a reset/rescan. This may reveal black bars again when controls or subtitles appear. It reduces a class of clipping mistakes, but it is not guaranteed subtitle protection.

New video, source-size changes and explicit rescan reset detection. Saved global framing preferences remain distinct from each video's detector state. The manual editor supports preview, apply and cancel; it does not persist an old video's automatic crop as a universal region.

### 5. Scale proportionally

`FramingTransform` uses one source-pixel scale for both axes and undoes TextureView's default stretch. Fit/Auto use the smaller viewport-to-region scale. Fill uses the larger scale, so the region outside the viewport is clipped. Manual mode combines selected bounds, zoom and pan. Zoom can also hide content: a crop is not inherently lossless.

## Android sender: a separate pipeline

The sender's `VideoPipeline` routes SurfaceTexture → OpenGL ES → encoder surfaces. Automatic border probes read only a **192 × 128** sample approximately every **250 ms**, not the full-resolution frame. Automatic detection is **landscape-only**; portrait keeps the whole phone screen.

Its `CropGeometry` uses the same RGB≤24 near-black threshold but different, more permissive rules: ≥12% active pixels, scans below 30% per edge, paired-edge tolerance of max(3 pixels, 3.5%), ≥92% black rows, and ≥80% black columns (with a narrow neutral-white-indicator allowance). It retains a one-sample-pixel margin. Unlike TV detection, sparse bright details can be ignored.

- **Auto fit/fill:** changes after 3 consistent samples; expansions wait for 6. Dark samples keep the previous crop.
- **Cinema:** locks after 8 consistent non-full-frame samples and holds until rescan, capture-size change or mode change. It does **not** use TV's post-lock expansion safeguard.
- **Centered 16:9:** a geometric centered preset, not automatic player recognition. Portrait keeps the full frame.

Sender cropping happens before encoding; receiver cropping changes presentation after reception. An already discarded source edge cannot be restored by the receiver. Begin with one side's crop controls when diagnosing an unexpected result.

## When to use Fit or Manual

Choose Fit for source-edge inspection, subtitles near boundaries, dark scenes, artwork with intentional black space, unusual aspect ratios or uncertain results. Use Manual when you want to define the crop yourself. Use Fill only if you accept losing source content to cover the display. Protected/DRM black video is not a removable border or a detection failure that cropping can fix.

## Read the source and tests

- TV: [VideoFraming](../tv/src/main/java/com/mooncast/tv/VideoFraming.java), [CropGeometry](../tv/src/main/java/com/mooncast/tv/CropGeometry.java), [CinemaCropTracker](../tv/src/main/java/com/mooncast/tv/CinemaCropTracker.java), [FramingSession](../tv/src/main/java/com/mooncast/tv/FramingSession.java), [FramingTransform](../tv/src/main/java/com/mooncast/tv/FramingTransform.java)
- Sender: [VideoPipeline](../app/src/main/java/com/mooncast/host/VideoPipeline.java), [CropGeometry](../app/src/main/java/com/mooncast/host/CropGeometry.java), [AutoCropTracker](../app/src/main/java/com/mooncast/host/AutoCropTracker.java), [CinemaCropTracker](../app/src/main/java/com/mooncast/host/CinemaCropTracker.java)
- [TV cinema contract checks](../scripts/test_tv_cinema_contract.py), [TV portable tests](../scripts/run_tv_unit_tests.py), [sender geometry tests](../tests/CropGeometryTest.java)

Tests with generated frames check implementation contracts. They are not evidence that every physical TV, phone, player, subtitle style or stream behaves correctly. [Unified validation scope](community-validation.md).
