# HotWords — keep default Android optimize rules for now.

# 穿山甲 / Pangle
-keep class com.bytedance.sdk.** { *; }
-keep class com.ss.sys.** { *; }
-keep class com.ss.android.** { *; }
-keep class com.bykv.** { *; }
-dontwarn com.bytedance.sdk.**
-dontwarn com.ss.android.**

# Sigmob / WindAd（GroMore mediation-sigmob-adapter）
-keep class com.sigmob.** { *; }
-keep interface com.sigmob.** { *; }
-keep class com.sigmob.windad.** { *; }
-keep interface com.sigmob.windad.** { *; }
-keep class com.czhj.** { *; }
-keep interface com.czhj.** { *; }
-keep class com.tan.mark.** { *; }
-dontwarn com.sigmob.**
-dontwarn com.czhj.**
