package com.mooncast.unified;
/** Process death, not disappearance from Android's service list, acknowledges capture release. */
public final class RoleGatePolicy {
 private RoleGatePolicy(){}
 public static boolean blocks(int ownerPid,int pid,String process,String packageName){
  return (ownerPid>0&&pid==ownerPid)||process.equals(packageName+":receiver")||process.equals(packageName+":stream");
 }
 public static boolean ready(boolean hostAck,boolean fileAck,boolean processKnown,boolean blocked){return hostAck&&fileAck&&processKnown&&!blocked;}
}
