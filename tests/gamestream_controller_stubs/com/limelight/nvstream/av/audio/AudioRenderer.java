// SPDX-License-Identifier: GPL-3.0-or-later
package com.limelight.nvstream.av.audio;
/** Minimal shape for actual PreparedDesktop and ReceiverSession; no audio implementation. */
public interface AudioRenderer { void start(); void stop(); void cleanup(); }
