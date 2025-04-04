pluginManagement {
    repositories {
        google()
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

rootProject.name = "powersync-root"

include(":core")
include(":core-tests-android")
include(":connectors:supabase")
include("static-sqlite-driver")

include(":dialect")
include(":persistence")
include(":PowerSyncKotlin")

include(":compose")
include(":attachments")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
