pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Скачивает нужный JDK (25 для MapLibre на десктопе), если его нет на машине
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DJMetryApp"
include(":shared")
include(":androidApp")
include(":desktopApp")
include(":iosApp")

