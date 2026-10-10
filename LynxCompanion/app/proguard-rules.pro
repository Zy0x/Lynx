-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# TopJohnWu LibSU Universal Root Bridge
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**

# Data & State Models
-keep class com.noir.lynx.data.** { *; }
-keepclassmembers class com.noir.lynx.data.** { *; }

# Foreground Services & Quick Settings Tiles
-keep class com.noir.lynx.service.** { *; }
-keepclassmembers class com.noir.lynx.service.** { *; }

# Broadcast Receivers
-keep class com.noir.lynx.receiver.** { *; }
-keepclassmembers class com.noir.lynx.receiver.** { *; }

# UI Activities & ViewModels
-keep class com.noir.lynx.ui.** { *; }
-keepclassmembers class com.noir.lynx.ui.** { *; }

# Engine, Hardware, Display & Safety Managers
-keep class com.noir.lynx.hardware.** { *; }
-keep class com.noir.lynx.safety.** { *; }
-keep class com.noir.lynx.sync.** { *; }
-keep class com.noir.lynx.display.** { *; }
-keep class com.noir.lynx.engine.** { *; }
-keep class com.noir.lynx.kernel.** { *; }
-keep class com.noir.lynx.lab.** { *; }

-dontwarn kotlinx.serialization.**
