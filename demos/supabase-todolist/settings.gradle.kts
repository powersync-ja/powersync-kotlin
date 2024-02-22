import java.util.Properties

pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

val localProperties = Properties().apply {
    try {
        load(file("local.properties").reader())
    } catch (ignored: java.io.IOException) {
        throw Error("local.properties file not found")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://jitpack.io")
        mavenLocal()
//        maven {
//            url = uri("https://maven.pkg.github.com/powersync-ja/powersync-kotlin")
//            credentials {
//                username = localProperties.getProperty("GITHUB_USERNAME")
//                password = localProperties.getProperty("GITHUB_TOKEN")
//            }
//        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.4.0")
}

rootProject.name = "supabase-todolist"

include(":androidApp")
include(":shared")
//include(":desktopApp")
