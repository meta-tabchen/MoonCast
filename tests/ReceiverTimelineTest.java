package com.mooncast.tv.airplay;
public final class ReceiverTimelineTest {
 public static void main(String[] args){
  ReceiverTimeline log=new ReceiverTimeline();for(int i=0;i<500;i++)log.add(i,"decoded",i);
  if(log.text().split("\n").length!=96||!log.text().startsWith("404 decoded 404"))throw new AssertionError("unbounded history");
  for(String bad:new String[]{"192.168.1.2","http://private","peer name","\nsecret","",null,"a".repeat(41)}){log.add(0,bad,0);if(bad!=null&&!bad.isEmpty()&&log.text().contains(" "+bad+" "))throw new AssertionError("unsafe event leaked");}
  if(!log.text().contains("diagnostic-rejected-length"))throw new AssertionError("rejection evidence absent");
  for(int i=0;i<500;i++)log.add(i,"native-stop",i);
  if(!log.text().endsWith("499 native-stop 499"))throw new AssertionError("repeated lifecycle diagnostics");
  System.out.println("PASS bounded receiver timeline: no arbitrary payload/event strings");
 }
}
