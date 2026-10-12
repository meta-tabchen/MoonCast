/* SPDX-License-Identifier: GPL-3.0-only */
/**
 * GameStream render adapters for the pinned Moonlight Android v12.2 callback API.
 *
 * <p>Integration: construct video with a caller-owned Surface made from the framing
 * TextureView, and construct audio. Pass both to the receiver session. Negotiate only
 * H.264 SDR and stereo. The session controller owns audio focus and the one global
 * native connection. Both adapters implement the core RendererRelease contract.
 *
 * <p>Surface lifecycle: setSurface(null) requests asynchronous detachment. A TextureView
 * owner that loses its texture must retain its SurfaceTexture (return false from
 * onSurfaceTextureDestroyed) and Surface until onSurfaceDetached or a successful
 * awaitSurfaceDetached/awaitReleased. Release them off the rendering callback only after
 * that confirmation. Passing a new Surface reconfigures video and requests a fresh IDR.
 * No compressed pictures are retained while the Surface is absent. A bounded cleanup
 * timeout or throwing vendor release is not permission to release the Surface early.
 *
 * <p>Timing: output is low-latency best effort. No shared audio/video source clock or
 * signed calibration is implemented. Statistics are local receiver observations: video
 * output buffers released for rendering, PCM submitted to AudioTrack, and known local
 * input drops. They are not measured physical display/audibility or transport loss.
 *
 * <p>Verification: run scripts/run_gamestream_render_tests.py for pure queue/Annex-B
 * contracts and lifecycle tests against explicit synthetic Android fakes. Those tests
 * do not validate MediaCodec/AudioTrack on Android, host interoperability, quality,
 * latency, audio focus, or the caller's TextureView lifecycle wiring.
 */
package com.mooncast.tv.gamestream.render;
