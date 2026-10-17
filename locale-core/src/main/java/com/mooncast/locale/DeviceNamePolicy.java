package com.mooncast.locale;
import java.nio.charset.StandardCharsets;
/** Display-name and DNS-SD UTF-8 budgets, without changing persistent identities. */
public final class DeviceNamePolicy {
 private DeviceNamePolicy(){}
 public static String normalize(String value){
  if(value==null)return "";StringBuilder clean=new StringBuilder();
  value.trim().codePoints().filter(c->!Character.isISOControl(c)).forEach(clean::appendCodePoint);
  return truncate(clean.toString(),60).trim();
 }
 public static boolean valid(String value){return value!=null&&!normalize(value).isEmpty()&&!value.toLowerCase(java.util.Locale.ROOT).contains(".local");}
 public static String truncate(String value,int bytes){if(bytes<0)throw new IllegalArgumentException("negative budget");while(value.getBytes(StandardCharsets.UTF_8).length>bytes)value=value.substring(0,value.offsetByCodePoints(value.length(),-1));return value;}
 public static String serviceLabel(String prefix,String name,String suffix){int budget=63-prefix.getBytes(StandardCharsets.UTF_8).length-suffix.getBytes(StandardCharsets.UTF_8).length;if(budget<0)throw new IllegalArgumentException("service decoration exceeds label budget");return prefix+truncate(normalize(name),budget)+suffix;}
}
