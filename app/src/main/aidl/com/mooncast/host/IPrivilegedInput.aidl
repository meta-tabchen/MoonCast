package com.mooncast.host;
import android.view.Surface;
interface IPrivilegedInput {
    void destroy() = 16777114;
    void initialize(IBinder owner) = 1;
    int uid() = 2;
    void touch(int type,int id,float x,float y,float pressure) = 3;
    void key(int key,boolean release,int flags) = 4;
    void cancel() = 5;
    void display(int id) = 6;
    int createDisplay(in Surface surface,int width,int height,int dpi) = 7;
    void releaseDisplays() = 8;
    void launch(String component,int displayId) = 9;
    boolean displayPower(boolean off) = 10;
    boolean displayPowerPending() = 11;
}
