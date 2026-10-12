package com.mooncast.unified;
/** Pure policy: only explicit valid choices override device detection. */
public final class RolePolicy {
 public static final String PHONE="phone", TV="tv", AUTO="auto";
 public static String selected(String saved,boolean television){return PHONE.equals(saved)||TV.equals(saved)?saved:(television?TV:PHONE);}
 public static boolean valid(String value){return PHONE.equals(value)||TV.equals(value)||AUTO.equals(value);}
 private RolePolicy(){}
}
