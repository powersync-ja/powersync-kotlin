pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}

rootProject.name = "supabase-todolist"

include(":androidApp")
include(":shared")
//include(":desktopApp")