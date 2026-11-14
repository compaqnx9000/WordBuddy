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

# 百度广告 SDK（GroMore mediation-baidu-adapter）
-keep class com.baidu.mobads.** { *; }
-keep class com.baidu.mobad.** { *; }
-keep class com.style.widget.** { *; }
-keep class com.component.** { *; }
-keep class com.baidu.ad.magic.flute.** { *; }
-keep class com.baidu.mobstat.forbes.** { *; }
-dontwarn com.baidu.**

# 快手广告 SDK（GroMore mediation-ks-adapter，kssdk-ad 5.7.20.1）
-keep class com.kwad.** { *; }
-keep class com.ksad.** { *; }
-keep class com.kwai.** { *; }
-dontwarn com.kwad.**
-dontwarn com.ksad.**
-dontwarn com.kwai.**

# 优量汇 / 广点通（GroMore mediation-gdt-adapter，union 4.701.1571）
-keep class com.qq.e.** { *; }
-keep class com.qq.e.comm.** { *; }
-keep class com.qq.e.ads.** { *; }
-dontwarn com.qq.e.**
