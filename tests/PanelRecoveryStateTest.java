package com.mooncast.host;
public final class PanelRecoveryStateTest {
 static int checks;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  PanelRecoveryState s=new PanelRecoveryState();check(s.phase==PanelRecoveryState.Phase.BRIGHT,"default bright");check(s.request()&&s.test,"first request bounded test");int off=s.generation;s.restore(false);check(!s.off(off),"canceled delayed off cannot execute");int restore=s.generation;check(s.beginAttempt(restore),"restore attempt");check(s.restored(restore),"matching on acknowledgment");
  check(s.request()&&s.off(s.generation),"manual off");restore=s.restore(true);check(s.fromTouch&&!s.renewing(),"touch cancels renewal before call");check(s.beginAttempt(restore)&&s.beginAttempt(restore)&&s.beginAttempt(restore)&&!s.beginAttempt(restore),"bounded attempts");check(s.phase==PanelRecoveryState.Phase.RECOVERY_REQUIRED&&!s.restored(restore),"timeout not bright and late completion cannot retire guard");check(!s.request(),"recovery rejects off");check(s.visiblyConfirmed()&&!s.verified,"visible confirmation dismisses but invalidates capability");
  check(!s.request(),"failed recovery forbids helper replacement after visible confirmation");s=new PanelRecoveryState();
  check(s.request()&&s.off(s.generation),"fresh owner safety test");restore=s.restore(false);s.ownerLost();check(!s.renewing()&&!s.request(),"owner death irrevocably disables new off");check(s.beginAttempt(restore)&&s.restored(restore),"restoration survives owner death");check(!s.request(),"stopped owner cannot restart");check(!s.visiblyConfirmed(),"no blind bright confirmation");
  PanelRecoveryState delayed=new PanelRecoveryState();check(delayed.request()&&delayed.off(delayed.generation),"delayed OFF setup");delayed.offSubmitted(delayed.generation);int pending=delayed.restore(false);delayed.uncertain(pending);
  check(!delayed.canVisiblyConfirm()&&!delayed.visiblyConfirmed(),"lit screen cannot dismiss while delayed OFF may still run");
  check(!delayed.acknowledgeQuiescence(pending-1)&&!delayed.canVisiblyConfirm(),"stale helper acknowledgement rejected");
  check(delayed.acknowledgeQuiescence(pending)&&delayed.canVisiblyConfirm(),"matching terminal helper acknowledgement permits visible confirmation");
  check(delayed.visiblyConfirmed()&&!delayed.verified,"full visible acknowledgement clears only quiesced recovery");
  check(!delayed.request(),"quiescence does not mean helper retirement or permission to start another OFF");delayed.verified=true;delayed.invalidate();check(!delayed.verified,"disconnect invalidates physical capability test");
  System.out.println("PASS independent panel recovery state: "+checks+" checks; no physical device claim");
 }
}
