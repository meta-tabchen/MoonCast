// SPDX-License-Identifier: GPL-3.0-or-later
package com.limelight.nvstream.av.video;
/** Minimal shape for actual PreparedDesktop and ReceiverSession; no decoder implementation. */
public abstract class VideoDecoderRenderer { public abstract void start(); public abstract void stop(); public abstract void cleanup(); }
