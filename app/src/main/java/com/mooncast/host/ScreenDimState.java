package com.mooncast.host;
/** Window-owned brightness override only; never claims physical panel power control. */
final class ScreenDimState {
 enum Phase { BRIGHT, COUNTDOWN, DIMMED, WAKING }
 Phase phase=Phase.BRIGHT;int generation;long countdownStartedAt=-1,dimmedAt=-1,noticeDeadline;
 int request(long now){phase=Phase.COUNTDOWN;countdownStartedAt=dimmedAt=-1;noticeDeadline=now+10000;return ++generation;}
 boolean noticeExpired(int token,long now){return token==generation&&phase==Phase.COUNTDOWN&&countdownStartedAt<0&&now>=noticeDeadline;}
 boolean noticeReady(int token,long now){if(token!=generation||phase!=Phase.COUNTDOWN||countdownStartedAt>=0||now>=noticeDeadline)return false;countdownStartedAt=now;return true;}
 boolean activate(int token,long now){if(token!=generation||phase!=Phase.COUNTDOWN||countdownStartedAt<0||now-countdownStartedAt<3000)return false;phase=Phase.DIMMED;dimmedAt=now;return true;}
 void beginWake(){++generation;phase=Phase.WAKING;}
 void bright(){++generation;phase=Phase.BRIGHT;}
 boolean active(){return phase!=Phase.BRIGHT;}
}
