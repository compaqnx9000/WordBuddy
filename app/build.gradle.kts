import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun escapeBuildConfig(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

// 穿山甲：在 local.properties 填写 csj.appId / csj.splashCodeId（勿提交密钥到 git）
val csjAppId = localProperties.getProperty("csj.appId", "").trim()
val csjSplashCodeId = localProperties.getProperty("csj.splashCodeId", "").trim()
val csjSplashFallbackCodeId = localProperties.getProperty("csj.splashFallbackCodeId", "").trim()
val csjDrawCodeId = localProperties.getProperty("csj.drawCodeId", "104539577").trim()
val csjRewardCodeId = localProperties.getProperty("csj.rewardCodeId", "104540414").trim()
// 微信开放平台移动应用 AppId。AppSecret 只放服务端 .env 的 WECHAT_APP_SECRET。
val wechatAppId = localProperties.getProperty("wechat.appId", "").trim()

android {
    namespace = "com.hotgis.wordbuddy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hotgis.wordbuddy"
        minSdk = 26
        targetSdk = 34
        versionCode = 108
        versionName = "1.17"
        ndk {
            // Pangle AAR only ships armeabi-v7a / arm64-v8a (no x86_64).
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
        // Domain https://wordbuddy.cc is blocked until ICP 备案; use server IP for now.
        buildConfigField("String", "API_BASE_URL", "\"http://39.96.67.128:8787\"")
        buildConfigField("String", "API_FALLBACK_URL", "\"http://39.96.67.128:8787\"")
        buildConfigField("String", "CSJ_APP_ID", "\"${escapeBuildConfig(csjAppId)}\"")
        buildConfigField("String", "CSJ_SPLASH_CODE_ID", "\"${escapeBuildConfig(csjSplashCodeId)}\"")
        buildConfigField(
            "String",
            "CSJ_SPLASH_FALLBACK_CODE_ID",
            "\"${escapeBuildConfig(csjSplashFallbackCodeId)}\"",
        )
        buildConfigField("String", "CSJ_DRAW_CODE_ID", "\"${escapeBuildConfig(csjDrawCodeId)}\"")
        buildConfigField("String", "CSJ_REWARD_CODE_ID", "\"${escapeBuildConfig(csjRewardCodeId)}\"")
        buildConfigField("String", "WECHAT_APP_ID", "\"${escapeBuildConfig(wechatAppId)}\"")
    }

    signingConfigs {
        create("release") {
            val store = keystoreProperties.getProperty("storeFile")
            if (!store.isNullOrBlank()) {
                storeFile = rootProject.file(store)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Emulator and physical debug builds both use the Aliyun cloud API.
            buildConfigField("String", "API_BASE_URL", "\"http://39.96.67.128:8787\"")
            buildConfigField("String", "API_FALLBACK_URL", "\"http://39.96.67.128:8787\"")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        jniLibs {
            // false improves 16KB page-size zip alignment for native .so (Android 15+).
            useLegacyPackaging = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // 显式依赖 fragment-ktx 1.8.5，解决 biometric 隐式引入 fragment 1.2.5 导致的 "Can only use lower 16 bits for requestCode" 选图崩溃
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.database)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.pangle.mediation.sdk)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.alipay.sdk:alipaysdk-android:15.8.33")
    implementation(libs.wechat.sdk)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Copy debug APK to repo outputs/WordBuddy-debug.apk after every assembleDebug.
val copyDebugApkToOutputs by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("outputs/apk/debug"))
    include("*.apk")
    into(rootProject.layout.projectDirectory.dir("outputs"))
    rename { "WordBuddy-debug.apk" }
    doFirst {
        rootProject.layout.projectDirectory.dir("outputs").asFile.mkdirs()
    }
}

// Copy release APK to repo outputs/WordBuddy-release.apk after every assembleRelease.
val copyReleaseApkToOutputs by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("outputs/apk/release"))
    include("*.apk")
    into(rootProject.layout.projectDirectory.dir("outputs"))
    rename { "WordBuddy-release.apk" }
    doFirst {
        rootProject.layout.projectDirectory.dir("outputs").asFile.mkdirs()
    }
}

afterEvaluate {
    tasks.named("assembleDebug").configure {
        finalizedBy(copyDebugApkToOutputs)
    }
    tasks.named("assembleRelease").configure {
        finalizedBy(copyReleaseApkToOutputs)
    }
}
