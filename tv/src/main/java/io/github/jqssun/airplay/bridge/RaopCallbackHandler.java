/* SPDX-License-Identifier: GPL-3.0-only
 * JNI callback ABI from jqssun/android-airplay-server v0.0.31.
 */
package io.github.jqssun.airplay.bridge;

public interface RaopCallbackHandler {
    void onVideoData(byte[] data, long ntpTimeNs, boolean h265);
    void onAudioFormat(int ct, int spf, boolean usingScreen);
    void onVideoSize(float srcW, float srcH, float w, float h);
    void onVolumeChange(float volume);
    float onClientVolume();
    void onAudioTeardown();
    void onConnectionInit();
    void onConnectionDestroy();
    void onConnectionReset(int reason);
    void onDisplayPin(String pin);
    void onMetadata(byte[] data);
    void onCoverArt(byte[] data);
    void onProgress(long start, long current, long end);
    void onDacpId(String dacpId, String activeRemote);
    void onMirrorRunning(boolean running);
    void onVideoPlay(String location, float startPositionSeconds);
    void onVideoScrub(float positionSeconds);
    void onVideoRate(float rate);
    void onVideoStop();
    void onVideoSessionPoll();
    void onLog(String message);
}
