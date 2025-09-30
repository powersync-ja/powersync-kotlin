pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("plugins")
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

plugins {
    id("com.gradle.develocity") version "4.1"
}

rootProject.name = "powersync-root"

include(":internal:download-core-extension")
include(":internal:PowerSyncKotlin")

include(":core")
include(":core-tests-android")
include(":connectors:supabase")
include(":integrations:room")
include(":static-sqlite-driver")

include(":integrations:sqldelight")
include(":integrations:sqldelight-test-database")

include(":compose")

include(":demos:android-supabase-todolist")
include(":demos:supabase-todolist")
include(":demos:supabase-todolist:androidApp")
include(":demos:supabase-todolist:androidBackgroundSync")
include(":demos:supabase-todolist:desktopApp")
include(":demos:supabase-todolist:iosApp")
include(":demos:supabase-todolist:shared")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
