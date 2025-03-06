import java.util.Properties

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://jitpack.io") {
            content { includeGroup("com.github.requery") }
        }
        mavenCentral()
    }
}

rootProject.name = "PowersyncAndroidExample"
include(":app")

val localProperties = Properties().apply {
    try {
        load(file("local.properties").reader())
    } catch (ignored: java.io.IOException) {
        // ignore
    }
}
val useReleasedVersions = localProperties.getProperty("USE_RELEASED_POWERSYNC_VERSIONS") == "true"

if (!useReleasedVersions) {
    includeBuild("../..") {
        dependencySubstitution {
            substitute(module("com.powersync:core"))
                .using(project(":core")).because("we want to auto-wire up sample dependency")
            substitute(module("com.powersync:connector-supabase"))
                .using(project(":connectors:supabase"))
                .because("we want to auto-wire up sample dependency")
            substitute(module("com.powersync:compose"))
                .using(project(":compose"))
                .because("we want to auto-wire up sample dependency")
        }
    }
}
