package com.mooncast.tv.airplay;
public final class ReceiverTimelineTest {
 public static void main(String[] args){
  ReceiverTimeline log=new ReceiverTimeline();for(int i=0;i<500;i++)log.add(i,"decoded",i);
  if(log.text().split("\n").length!=96||!log.text().startsWith("404 decoded 404"))throw new AssertionError("unbounded history");
  for(String bad:new String[]{"192.168.1.2","http://private","peer name","\nsecret"})try{log.add(0,bad,0);throw new AssertionError("unsafe event");}catch(IllegalArgumentException expected){}
  System.out.println("PASS bounded receiver timeline: no arbitrary payload/event strings");
 }
}
