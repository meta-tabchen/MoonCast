package com.mooncast.locale;
/** Values are deliberately bounded; unknown/corrupt preferences follow the OS. */
public final class LocalePolicy {
 private LocalePolicy() {}
 public static String valid(String value) { return "en".equals(value)||"zh".equals(value)?value:"system"; }
 public static String resourceLanguage(String language) { return "zh".equals(language)?"zh":"en"; }
}
