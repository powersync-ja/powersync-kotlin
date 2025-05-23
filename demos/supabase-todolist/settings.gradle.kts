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

val localProperties =
    Properties().apply {
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
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.4.0")
}

rootProject.name = "supabase-todolist"

include(":androidApp")
include(":androidBackgroundSync")
include(":shared")
include(":desktopApp")

val useReleasedVersions = localProperties.getProperty("USE_RELEASED_POWERSYNC_VERSIONS") == "true"

if (!useReleasedVersions) {
    includeBuild("../..") {
        dependencySubstitution {
            val replacements = mapOf(
                "core" to "core",
                "persistence" to "persistence",
                "connector-supabase" to "connectors:supabase",
                "compose" to "compose"
            )

            replacements.forEach { (moduleName, projectName) ->
                substitute(module("com.powersync:$moduleName"))
                    .using(project(":$projectName"))
                    .because("we want to auto-wire up sample dependency")
            }
        }
    }
}
