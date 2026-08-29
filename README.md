# HotWords 生词本

Android 原生背单词应用（Kotlin + Jetpack Compose），界面参考有道类「生词本 / 卡片模式」。

## 要不要装 Android Studio？

**建议装。** 写代码不强制依赖它，但本机编译、装模拟器、真机调试几乎都需要 Android SDK。Android Studio 会一次性带上：

- JDK 17（当前工程需要，本机现有的 Java 8 **不够**）
- Android SDK / 构建工具
- 设备管理器（模拟器）
- Logcat、布局检查

也可以只装 [Command line tools](https://developer.android.com/studio#command-tools)，再自己配 JDK 17 和 SDK，步骤更碎，不推荐第一次做 Android。

装好后用 Android Studio 打开本目录即可，不必从零 New Project。

## 设计标准

按 **iPhone 17 Pro Max** 逻辑尺寸做基准，再按实际屏幕宽度缩放：

| 项目 | 数值 |
| --- | --- |
| 逻辑画布 | **440 × 956 pt**（1 pt ≈ 1 dp） |
| 物理分辨率 | 1320 × 2868 @3x |
| 屏幕 | 6.9" Super Retina XDR |

窄屏（常见 Android 360 dp）会按 `360/440` 缩小字号和间距；更高的手机靠滚动，不硬压高度。

推荐模拟器：分辨率 `1320 × 2868`，密度 **480 dpi（xxhdpi）**，宽高比接近 19.5:9。

## 已实现

- 生词列表：按日期分组、全部词句 / 单词 / 短语、按时间或字母排序、搜索
- 隐藏释义（浅蓝斜纹遮罩，点击单条可揭开）
- 系统 TTS 英音 / 美音
- 卡片模式：上一条 / 下一条、自动播放、随机、模式设置
- 「卡片模式」打乱顺序进入卡片

词库目前是内置演示数据，后续可接生词同步或词典 API。

## 用 Android Studio 运行

1. 安装 [Android Studio](https://developer.android.com/studio)（带 JDK 17 的近期稳定版即可）。
2. 打开 `HotWords` 目录，等待 Gradle 同步。
3. 创建设备或插上已开 USB 调试的手机。
4. 运行 `app`。

首次同步会下载 Gradle 8.11.1 和 Android 依赖，需要网络。
