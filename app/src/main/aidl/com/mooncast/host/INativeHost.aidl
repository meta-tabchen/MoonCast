package com.mooncast.host;
import com.mooncast.host.INativeHostCallbacks;
interface INativeHost {
    void start(String name, boolean hevc, INativeHostCallbacks callbacks);
    void pin(String pin);
    boolean disconnect(long session);
    void audio(int frames);
    void shutdown();
}
