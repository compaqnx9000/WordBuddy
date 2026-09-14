pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 穿山甲 / Pangle China SDK
        maven { url = uri("https://artifact.bytedance.com/repository/pangle") }
    }
}

rootProject.name = "HotWords"
include(":app")
