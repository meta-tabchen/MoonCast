package com.mooncast.host;

/** Pure session state. Hardware/permission acknowledgments remain the controller's responsibility. */
final class ManualControlState {
    enum Phase { BRIGHT, COUNTDOWN, TESTING, DARK, RESTORING }
    private Phase phase=Phase.BRIGHT;
    boolean paused,verified;
    private boolean testAttempt,touchTestReturn;
    int generation;
    boolean bright(){return phase==Phase.BRIGHT;}
    boolean pending(){return phase==Phase.COUNTDOWN;}
    boolean dark(){return phase==Phase.TESTING||phase==Phase.DARK;}
    boolean testing(){return phase==Phase.TESTING;}
    boolean renewing(){return phase==Phase.DARK;}
    boolean restoring(){return phase==Phase.RESTORING;}
    boolean testAttempt(){return testAttempt;}
    boolean request(boolean connected,boolean permission,boolean capable){
        if(!bright()||paused||!connected||!permission||!capable)return false;
        testAttempt=!verified;touchTestReturn=false;phase=Phase.COUNTDOWN;++generation;return true;
    }
    boolean powerOff(int token){
        if(!pending()||token!=generation)return false;
        phase=testAttempt?Phase.TESTING:Phase.DARK;return true;
    }
    void beginRestore(boolean guardPresent,boolean fromTouch){
        touchTestReturn|=testing()&&fromTouch;
        ++generation;
        phase=guardPresent||dark()||restoring()?Phase.RESTORING:Phase.BRIGHT;
    }
    boolean restored(){
        boolean confirmation=touchTestReturn;phase=Phase.BRIGHT;testAttempt=false;touchTestReturn=false;return confirmation;
    }
    void disconnect(){paused=false;verified=false;touchTestReturn=false;}
    void pauseApplied(boolean value){paused=value;}
    void confirm(boolean value){verified=value&&bright();}
}
