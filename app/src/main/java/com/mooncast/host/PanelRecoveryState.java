package com.mooncast.host;
/** No timeout ever means Bright. Late completions cannot clear a newer guard. */
final class PanelRecoveryState {
 enum Phase { BRIGHT, COUNTDOWN, OFF, RESTORING, RECOVERY_REQUIRED }
 Phase phase=Phase.BRIGHT;int generation,attempts;boolean owner=true,verified,test,fromTouch,failedRecovery,producerQuiescent=true;
 boolean request(){if(!owner||failedRecovery||phase!=Phase.BRIGHT)return false;test=!verified;fromTouch=false;phase=Phase.COUNTDOWN;++generation;return true;}
 boolean off(int token){if(token!=generation||phase!=Phase.COUNTDOWN||!owner)return false;phase=Phase.OFF;return true;}
 int restore(boolean touch){fromTouch|=test&&touch;if(phase==Phase.BRIGHT)return generation;phase=Phase.RESTORING;attempts=0;return ++generation;}
 boolean beginAttempt(int token){if(token!=generation||phase!=Phase.RESTORING)return false;if(++attempts>3){phase=Phase.RECOVERY_REQUIRED;blockFurtherOff();return false;}return true;}
 void offSubmitted(int token){if(token==generation&&phase==Phase.OFF)producerQuiescent=false;}
 boolean acknowledgeQuiescence(int token){if(token!=generation)return false;producerQuiescent=true;return true;}
 boolean canVisiblyConfirm(){return phase==Phase.RECOVERY_REQUIRED&&producerQuiescent;}
 void invalidate(){verified=false;}
 void blockFurtherOff(){failedRecovery=true;verified=false;}
 boolean restored(int token){if(token!=generation||phase!=Phase.RESTORING)return false;phase=Phase.BRIGHT;test=false;producerQuiescent=true;return true;}
 void uncertain(int token){if(token==generation&&phase==Phase.RESTORING){phase=Phase.RECOVERY_REQUIRED;blockFurtherOff();}}
 boolean visiblyConfirmed(){if(!canVisiblyConfirm())return false;phase=Phase.BRIGHT;verified=false;test=false;++generation;return true;}
 void ownerLost(){owner=false;verified=false;}
 boolean renewing(){return owner&&phase==Phase.OFF&&!test;}
}
