package com.mooncast.locale;
import java.nio.charset.StandardCharsets;
public final class DeviceNamePolicyTest {
 static void require(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  require(!DeviceNamePolicy.valid(".local"));require(!DeviceNamePolicy.valid("room.local TV"));require(!DeviceNamePolicy.valid("ROOM.LOCAL"));require(DeviceNamePolicy.valid("Room"));
  require(DeviceNamePolicy.normalize(null).equals(""));require(DeviceNamePolicy.normalize("  A\nB\t ").equals("AB"));
  String ascii="x".repeat(60),chinese="名".repeat(20),emoji="😀".repeat(20);
  require(DeviceNamePolicy.normalize(ascii+"x").equals(ascii));require(DeviceNamePolicy.normalize(chinese+"名").equals(chinese));require(DeviceNamePolicy.normalize(emoji).equals("😀".repeat(15)));
  for(String value:new String[]{ascii,chinese,emoji})for(String[] decoration:new String[][]{{""," APK"},{"aabbccddeeff@",""},{"",""}}){String label=DeviceNamePolicy.serviceLabel(decoration[0],value,decoration[1]);require(label.getBytes(StandardCharsets.UTF_8).length<=63);require(label.startsWith(decoration[0])&&label.endsWith(decoration[1]));require(!label.contains("�"));}
  require(DeviceNamePolicy.serviceLabel("",ascii," APK").equals("x".repeat(59)+" APK"));require(DeviceNamePolicy.serviceLabel("",chinese," APK").equals("名".repeat(19)+" APK"));
  System.out.println("PASS device-name normalization and DNS-SD decoration budgets: Unicode boundaries, APK suffix and RAOP prefix; no discovery/runtime claim");
 }
}
