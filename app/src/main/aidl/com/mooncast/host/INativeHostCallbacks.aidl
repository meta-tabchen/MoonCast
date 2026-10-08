package com.mooncast.host;
import android.view.Surface;
interface INativeHostCallbacks {
    void createDisplay(long session, int width, int height, int fps, int packetMs, in Surface surface, boolean audio);
    void stopDisplay(long session);
    void pinRequested();
    void error(String text);
    void uuid(String uuid);
    float[] readAudio(int count);
    void touch(long session, int type, int pointer, float x, float y, float pressure);
    void mouse(long session, float x, float y, float width, float height);
    void relative(long session, int x, int y);
    void button(long session, boolean release);
    void key(int key, boolean release, int flags);
}
