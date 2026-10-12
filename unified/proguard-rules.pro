# Preserve application/JNI/reflection ABI. Optimize Android support/UI dependency code.
-keep class com.mooncast.** { *; }
-keep class io.github.jqssun.** { *; }
-keep class airplaylib.** { *; }
-keep class go.** { *; }
-keep class com.limelight.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class rikka.** { *; }
# WebRTC reaches Java through JNI, including fork playback audio callbacks.
-keep class org.webrtc.** { *; }
# The native SDK loads JniInit by its literal class name; the AAR has no consumer rules.
-keep class org.jni_zero.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn javax.naming.**
-dontwarn org.bouncycastle.**
