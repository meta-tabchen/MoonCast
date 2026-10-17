package com.mooncast.host;
public final class PanelRecoveryStateTest {
 static int checks;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  PanelRecoveryState s=new PanelRecoveryState();check(s.phase==PanelRecoveryState.Phase.BRIGHT,"default bright");check(s.request()&&s.test,"first request bounded test");int off=s.generation;s.restore(false);check(!s.off(off),"canceled delayed off cannot execute");int restore=s.generation;check(s.beginAttempt(restore),"restore attempt");check(s.restored(restore),"matching on acknowledgment");
  check(s.request()&&s.off(s.generation),"manual off");restore=s.restore(true);check(s.fromTouch&&!s.renewing(),"touch cancels renewal before call");check(s.beginAttempt(restore)&&s.beginAttempt(restore)&&s.beginAttempt(restore)&&!s.beginAttempt(restore),"bounded attempts");check(s.phase==PanelRecoveryState.Phase.RECOVERY_REQUIRED&&!s.restored(restore),"timeout not bright and late completion cannot retire guard");check(!s.request(),"recovery rejects off");check(s.visiblyConfirmed()&&!s.verified,"visible confirmation dismisses but invalidates capability");
  check(s.request()&&s.off(s.generation),"new safety test");restore=s.restore(false);s.ownerLost();check(!s.renewing()&&!s.request(),"owner death irrevocably disables new off");check(s.beginAttempt(restore)&&s.restored(restore),"restoration survives owner death");check(!s.request(),"stopped owner cannot restart");check(!s.visiblyConfirmed(),"no blind bright confirmation");
  System.out.println("PASS independent panel recovery state: "+checks+" checks; no physical device claim");
 }
}
