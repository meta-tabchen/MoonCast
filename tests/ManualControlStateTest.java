package com.mooncast.host;
public final class ManualControlStateTest {
 private static int checks;
 private static void require(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
 public static void main(String[] args){
  ManualControlState s=new ManualControlState();require(s.bright()&&!s.verified&&!s.paused,"default bright, unverified, live");
  require(!s.request(false,true,true)&&!s.request(true,false,true)&&!s.request(true,true,false),"connection/permission/backend gates");
  s.pauseApplied(true);require(!s.request(true,true,true),"paused off rejected");s.pauseApplied(false);
  require(s.request(true,true,true)&&s.testAttempt(),"first request is safety test");int canceled=s.generation;s.beginRestore(false,false);require(s.bright()&&!s.powerOff(canceled),"countdown cancel invalidates callback");
  require(s.request(true,true,true),"retry test");int first=s.generation;require(s.powerOff(first)&&s.testing()&&!s.renewing(),"bounded test never renews");
  s.beginRestore(true,true);require(s.restoring()&&!s.renewing(),"touch requests restore before guard removed");
  require(!s.request(true,true,true),"no second guard/off while restoring");s.beginRestore(true,false);require(s.restoring(),"retry retains restore state");
  require(s.restored()&&s.bright()&&!s.verified,"ack requests user confirmation, does not self-verify");s.confirm(true);require(s.verified,"user confirms visible restoration");
  require(s.request(true,true,true)&&!s.testAttempt(),"manual verified off");require(s.powerOff(s.generation)&&s.renewing(),"normal manual dark state renews");
  s.beginRestore(true,false);require(s.restoring()&&!s.renewing(),"explicit restore stops renewal immediately");require(!s.restored()&&s.bright(),"ordinary restore does not request confirmation");
  require(s.bright()&&!s.powerOff(first),"stay bright until new manual request; stale callbacks ignored");
  s.pauseApplied(true);s.disconnect();s.beginRestore(false,false);s.restored();require(!s.paused&&!s.verified&&s.bright(),"disconnect resets both pause and per-session verification");
  require(s.request(true,true,true)&&s.testAttempt(),"reconnect requires new device test");s.powerOff(s.generation);s.beginRestore(true,false);require(!s.restored()&&!s.verified,"timeout never verifies wake capability");
  System.out.println("PASS manual controls state: "+checks+" checks; no device/hardware claim");
 }
}
