pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
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

