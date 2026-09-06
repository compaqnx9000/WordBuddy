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

android {
    namespace = "com.zeroglab.hotwords"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zeroglab.hotwords"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.1.1"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
        // Release / physical builds talk to the Aliyun API.
        buildConfigField("String", "API_BASE_URL", "\"http://39.96.67.128:8787\"")
        buildConfigField("String", "API_FALLBACK_URL", "\"http://39.96.67.128:8787\"")
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
            // Emulator reaches this machine via 10.0.2.2 (local catalogs include 中考).
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8787\"")
            buildConfigField("String", "API_FALLBACK_URL", "\"http://192.168.1.3:8787\"")
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
            useLegacyPackaging = true
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
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Copy debug APK to repo outputs/HotWords-debug.apk after every assembleDebug.
val copyDebugApkToOutputs by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("outputs/apk/debug"))
    include("*.apk")
    into(rootProject.layout.projectDirectory.dir("outputs"))
    rename { "HotWords-debug.apk" }
    doFirst {
        rootProject.layout.projectDirectory.dir("outputs").asFile.mkdirs()
    }
}

// Copy release APK to repo outputs/HotWords-release.apk after every assembleRelease.
val copyReleaseApkToOutputs by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("outputs/apk/release"))
    include("*.apk")
    into(rootProject.layout.projectDirectory.dir("outputs"))
    rename { "HotWords-release.apk" }
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
