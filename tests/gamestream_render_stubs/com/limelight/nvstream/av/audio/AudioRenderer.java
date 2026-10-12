/* SPDX-License-Identifier: GPL-3.0-only */
package com.limelight.nvstream.av.audio;
import com.limelight.nvstream.jni.MoonBridge;
/** Shape-only test ABI, checked separately against actual v12.2 upstream sources. */
public interface AudioRenderer {
    int setup(MoonBridge.AudioConfiguration configuration,int sampleRate,int samplesPerFrame);
    void start();void stop();void cleanup();void playDecodedAudio(short[] data);
}
