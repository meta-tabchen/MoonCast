package com.mooncast.host;
/** Window-owned brightness override only; never claims physical panel power control. */
final class ScreenDimState {
 enum Phase { BRIGHT, COUNTDOWN, DIMMED, WAKING }
 Phase phase=Phase.BRIGHT;int generation;
 int request(){phase=Phase.COUNTDOWN;return ++generation;}
 boolean activate(int token){if(token!=generation||phase!=Phase.COUNTDOWN)return false;phase=Phase.DIMMED;return true;}
 void beginWake(){++generation;phase=Phase.WAKING;}
 void bright(){++generation;phase=Phase.BRIGHT;}
 boolean active(){return phase!=Phase.BRIGHT;}
}
