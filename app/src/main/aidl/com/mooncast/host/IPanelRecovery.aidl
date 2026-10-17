package com.mooncast.host;
/** Private, one-way commands. Recovery never blocks a cast process's stop deadline. */
oneway interface IPanelRecovery {
    void initialize(IBinder owner, boolean root);
    void requestOff();
    void restore();
    void ownerStopped();
}
