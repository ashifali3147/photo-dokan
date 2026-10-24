rootProject.name = "photo-dokan"

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

include(":shared:core")
include(":shared:library")
include(":shared:print")
include(":shared:station")
include(":shared:vision")
include(":shared:ui")
include(":app-android")
include(":app-desktop")
