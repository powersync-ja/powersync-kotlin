import java.util.Properties

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

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
        maven("https://jitpack.io") {
            content { includeGroup("com.github.requery") }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.4.0")
}

rootProject.name = "supabase-todolist"

include(":androidApp")
include(":shared")
include(":desktopApp")

val useReleasedVersions = localProperties.getProperty("USE_RELEASED_POWERSYNC_VERSIONS") == "true"

if (!useReleasedVersions) {
    includeBuild("../..") {
        dependencySubstitution {
            substitute(module("com.powersync:core"))
                .using(project(":core")).because("we want to auto-wire up sample dependency")
            substitute(module("com.powersync:persistence"))
                .using(project(":persistence")).because("we want to auto-wire up sample dependency")
            substitute(module("com.powersync:connector-supabase"))
                .using(project(":connectors:supabase"))
                .because("we want to auto-wire up sample dependency")
        }
    }
}
