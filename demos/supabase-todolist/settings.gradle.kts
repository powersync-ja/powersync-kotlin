pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        val kotlinVersion = extra["kotlin.version"] as String
        val agpVersion = extra["agp.version"] as String
        val composeVersion = extra["compose.version"] as String

        kotlin("jvm").version(kotlinVersion)
        kotlin("multiplatform").version(kotlinVersion)
        kotlin("android").version(kotlinVersion)
        id("com.android.application").version(agpVersion)
        id("com.android.library").version(agpVersion)
        id("org.jetbrains.compose").version(composeVersion)
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://jitpack.io")
    }
    versionCatalogs {
        create("projectLibs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}

rootProject.name = "supabase-todolist"

include(":androidApp")
include(":shared")
include(":desktopApp")

includeBuild("../..") {
    dependencySubstitution {
        substitute(module("com.powersync:core"))
            .using(project(":core")).because("we want to auto-wire up sample dependency")
        substitute(module("com.powersync:connectors"))
            .using(project(":connectors")).because("we want to auto-wire up sample dependency")
    }
}